package com.drivermax.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * DriverMax - Servicio ético de ubicación
 * Solo envía ubicación cuando el conductor está en un viaje activo
 */
public class LocationService extends Service {
    
    private static final String TAG = "DriverMax";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "DriverMaxChannel";
    
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private ApiClient apiClient;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        apiClient = new ApiClient(this);
        
        createNotificationChannel();
        setupLocationCallback();
        
        Log.d(TAG, "DriverMax Service creado");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification("DriverMax activo"));
        startLocationUpdates();
        return START_STICKY;
    }
    
    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    sendLocationToBackend(location);
                }
            }
        };
    }
    
    private void startLocationUpdates() {
        try {
            LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 30000)
                .setMinUpdateIntervalMillis(15000)
                .build();
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest, 
                locationCallback, 
                Looper.getMainLooper()
            );
            
            Log.d(TAG, "Ubicaciones iniciadas");
            
        } catch (SecurityException e) {
            Log.e(TAG, "Sin permisos", e);
        }
    }
    
    private void sendLocationToBackend(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        
        Log.d(TAG, String.format("Ubicación: %.6f, %.6f", latitude, longitude));
        
        apiClient.sendLocation(latitude, longitude, new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String response) {
                Log.d(TAG, "Enviado exitoso");
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error: " + error);
            }
        });
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "DriverMax",
                NotificationManager.IMPORTANCE_LOW
            );
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification(String message) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DriverMax")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        Log.d(TAG, "Service destruido");
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 