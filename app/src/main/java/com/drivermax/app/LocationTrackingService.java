package com.drivermax.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

public class LocationTrackingService extends Service implements LocationListener {
    
    private static final String TAG = "LocationTrackingService";
    private static final String CHANNEL_ID = "UberMaxTracking";
    private static final int NOTIFICATION_ID = 1001;
    private static final long MIN_TIME_BETWEEN_UPDATES = 30000; // 30 segundos
    private static final float MIN_DISTANCE_CHANGE = 10; // 10 metros
    
    public static boolean isRunning = false;
    
    private LocationManager locationManager;
    private ApiClient apiClient;
    private Handler statusCheckHandler;
    private Runnable statusCheckRunnable;
    
    private String userId;
    private String backendUrl;
    private boolean userZoneActive = false;
    private Location lastLocation;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        apiClient = new ApiClient();
        statusCheckHandler = new Handler(Looper.getMainLooper());
        
        createNotificationChannel();
        setupStatusChecker();
        
        isRunning = true;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        
        if (intent != null) {
            userId = intent.getStringExtra("user_id");
            backendUrl = intent.getStringExtra("backend_url");
            
            if (userId != null && backendUrl != null) {
                apiClient.setUserId(userId);
                apiClient.setBaseUrl(backendUrl);
                
                startLocationTracking();
                startStatusChecking();
                
                showNotification(getString(R.string.notification_waiting_text));
            }
        }
        
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        
        stopLocationTracking();
        stopStatusChecking();
        
        if (apiClient != null) {
            apiClient.cleanup();
        }
        
        isRunning = false;
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.notification_channel_desc));
        channel.setShowBadge(false);
        
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
    
    private void showNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.status_indicator)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build();
        
        startForeground(NOTIFICATION_ID, notification);
    }
    
    private void setupStatusChecker() {
        statusCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkUserStatus();
                statusCheckHandler.postDelayed(this, 60000); // Cada 60 segundos
            }
        };
    }
    
    private void startStatusChecking() {
        statusCheckHandler.post(statusCheckRunnable);
    }
    
    private void stopStatusChecking() {
        if (statusCheckHandler != null && statusCheckRunnable != null) {
            statusCheckHandler.removeCallbacks(statusCheckRunnable);
        }
    }
    
    private void checkUserStatus() {
        if (apiClient != null) {
            apiClient.checkUserStatus(new ApiClient.ApiCallback<ApiClient.UserStatus>() {
                @Override
                public void onSuccess(ApiClient.UserStatus status) {
                    boolean wasActive = userZoneActive;
                    userZoneActive = status.isActive();
                    
                    Log.d(TAG, "User status: zone_active=" + userZoneActive + ", status=" + status.status);
                    
                    if (userZoneActive != wasActive) {
                        updateTrackingState();
                    }
                    
                    updateNotification();
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error checking user status: " + error);
                }
            });
        }
    }
    
    private void updateTrackingState() {
        if (userZoneActive) {
            Log.d(TAG, "User zone activated - starting GPS tracking");
            startLocationTracking();
        } else {
            Log.d(TAG, "User zone deactivated - stopping GPS tracking");
            stopLocationTracking();
        }
    }
    
    private void updateNotification() {
        String notificationText;
        if (userZoneActive) {
            notificationText = getString(R.string.notification_text);
        } else {
            notificationText = getString(R.string.notification_waiting_text);
        }
        showNotification(notificationText);
    }
    
    private void startLocationTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted");
            return;
        }
        
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_BETWEEN_UPDATES,
                MIN_DISTANCE_CHANGE,
                this
            );
            
            Log.d(TAG, "GPS location tracking started");
        } else {
            Log.e(TAG, "GPS provider not enabled");
        }
    }
    
    private void stopLocationTracking() {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
            Log.d(TAG, "GPS location tracking stopped");
        }
    }
    
    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "Location changed: " + location.getLatitude() + ", " + location.getLongitude());
        
        lastLocation = location;
        
        if (userZoneActive && apiClient != null) {
            apiClient.sendLocation(location.getLatitude(), location.getLongitude(), new ApiClient.ApiCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    Log.d(TAG, "Location sent successfully");
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Failed to send location: " + error);
                }
            });
        }
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, "Location provider status changed: " + provider + " status: " + status);
    }
    
    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "Location provider enabled: " + provider);
        if (LocationManager.GPS_PROVIDER.equals(provider) && userZoneActive) {
            startLocationTracking();
        }
    }
    
    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "Location provider disabled: " + provider);
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            stopLocationTracking();
        }
    }
} 