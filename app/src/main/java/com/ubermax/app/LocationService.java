package com.ubermax.app;

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
 * UberMax - Servicio súper simple para enviar ubicación al backend
 * Solo funciona cuando el bot tiene zona preferencial activa
 */
public class LocationService extends Service {

    private static final String TAG = "UberMax";
    private static final String CHANNEL_ID = "UBERMAX_LOCATION";
    private static final int NOTIFICATION_ID = 1001;
    
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private ApiClient apiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Servicio UberMax iniciado");
        
        // Inicializar cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        // Inicializar cliente API con context
        apiClient = new ApiClient(this);
        
        // Crear canal de notificación
        createNotificationChannel();
        
        // Configurar callback de ubicación
        setupLocationCallback();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Mostrar notificación de servicio corriendo
        startForeground(NOTIFICATION_ID, createNotification());
        
        // Iniciar tracking de ubicación
        startLocationUpdates();
        
        Log.d(TAG, "UberMax tracking iniciado");
        
        // Reiniciar servicio si es eliminado
        return START_STICKY;
    }
    
    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                
                for (Location location : locationResult.getLocations()) {
                    // Enviar ubicación al backend
                    sendLocationToBackend(location);
                }
            }
        };
    }
    
    private void startLocationUpdates() {
        try {
            LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 
                30000 // 30 segundos
            )
            .setMinUpdateIntervalMillis(15000) // Mínimo 15 segundos
            .build();
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            );
            
            Log.d(TAG, "Actualizaciones de ubicación iniciadas");
            
        } catch (SecurityException e) {
            Log.e(TAG, "Error de permisos: " + e.getMessage());
        }
    }
    
    private void sendLocationToBackend(Location location) {
        // Enviar solo si tenemos una ubicación válida
        if (location == null) return;
        
        Log.d(TAG, String.format("Enviando ubicación: %.6f, %.6f", 
            location.getLatitude(), location.getLongitude()));
        
        // Enviar al backend (el backend decide si está en zona preferencial)
        apiClient.sendLocation(
            location.getLatitude(),
            location.getLongitude(),
            new ApiClient.ApiCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    Log.d(TAG, "Ubicación enviada exitosamente");
                }
                
                @Override
                public void onError(String error) {
                    Log.w(TAG, "Error enviando ubicación: " + error);
                }
            }
        );
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "UberMax Tracking",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Servicio de ubicación UberMax");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
    
    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UberMax")
            .setContentText("Enviando ubicación cuando zona preferencial esté activa")
            .setSmallIcon(R.drawable.ic_location)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // Detener actualizaciones de ubicación
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        
        Log.d(TAG, "Servicio UberMax detenido");
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null; // No vinculamos el servicio
    }
} 