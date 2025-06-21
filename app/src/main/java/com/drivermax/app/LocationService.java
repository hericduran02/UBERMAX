package com.drivermax.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
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
    private static final String CHANNEL_ID = "DriverMaxLocationChannel";
    private static final String PREFS_NAME = "DriverMaxPrefs";
    private static final String IN_TRIP_KEY = "in_trip";
    
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private ApiClient apiClient;
    private SharedPreferences prefs;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        apiClient = new ApiClient(this);
        
        createNotificationChannel();
        setupLocationCallback();
        
        Log.d(TAG, "DriverMax LocationService creado");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "DriverMax LocationService iniciado");
        
        // Solo iniciar si está en viaje
        if (isInTrip()) {
            startForeground(NOTIFICATION_ID, createNotification("🚗 En viaje - Registrando ruta"));
            startLocationUpdates();
        } else {
            Log.d(TAG, "No está en viaje - LocationService no enviará ubicación");
            startForeground(NOTIFICATION_ID, createNotification("⏸️ Fuera de servicio"));
            stopSelf();
        }
        
        return START_STICKY;
    }
    
    private boolean isInTrip() {
        return prefs.getBoolean(IN_TRIP_KEY, false);
    }
    
    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    // Solo enviar si está en viaje
                    if (isInTrip()) {
                        sendLocationToBackend(location);
                        updateNotification("🚗 Viaje activo - Ubicación enviada");
                    } else {
                        Log.d(TAG, "Viaje terminado - Deteniendo envío de ubicación");
                        stopLocationUpdates();
                        stopSelf();
                    }
                }
            }
        };
    }
    
    private void startLocationUpdates() {
        try {
            LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 30000) // Cada 30 segundos
                .setMinUpdateIntervalMillis(15000) // Mínimo 15 segundos
                .build();
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest, 
                locationCallback, 
                Looper.getMainLooper()
            );
            
            Log.d(TAG, "Actualizaciones de ubicación iniciadas para viaje activo");
            
        } catch (SecurityException e) {
            Log.e(TAG, "No hay permisos de ubicación", e);
        }
    }
    
    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.d(TAG, "Actualizaciones de ubicación detenidas");
        }
    }
    
    private void sendLocationToBackend(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        
        Log.d(TAG, String.format("Enviando ubicación: %.6f, %.6f (Viaje activo)", latitude, longitude));
        
        apiClient.sendLocation(latitude, longitude, new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String response) {
                Log.d(TAG, "Ubicación enviada exitosamente: " + response);
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error enviando ubicación: " + error);
            }
        });
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "DriverMax - Seguimiento de Viajes",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Notificaciones de DriverMax para viajes activos");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification(String message) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DriverMax - Asistente de Conductores")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
    
    private void updateNotification(String message) {
        Notification notification = createNotification(message);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        Log.d(TAG, "DriverMax LocationService destruido");
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 