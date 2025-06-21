package com.ubermax.app;

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
import android.provider.Settings;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * UberMax - Servicio ético de ubicación sincronizado con bot Telegram
 * Solo envía ubicación cuando el bot de Telegram tiene viaje activo
 */
public class LocationService extends Service {
    
    private static final String TAG = "UberMax";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "UberMaxLocationChannel";
    private static final String PREFS_NAME = "UberMaxPrefs";
    private static final String LAST_TRACKING_STATE = "last_tracking_state";
    
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private ApiClient apiClient;
    private SharedPreferences prefs;
    private String androidId;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        apiClient = new ApiClient(this);
        androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        
        createNotificationChannel();
        setupLocationCallback();
        
        Log.d(TAG, "UberMax LocationService creado - Android ID: " + androidId);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "UberMax LocationService iniciado");
        
        // Iniciar con notificación base y verificar estado
        startForeground(NOTIFICATION_ID, createNotification("🔄 Verificando estado del bot..."));
        
        // Verificar inmediatamente si debe trackear
        checkBotStatusAndStartTracking();
        
        return START_STICKY;
    }
    
    private void checkBotStatusAndStartTracking() {
        apiClient.checkUserStatus(androidId, new ApiClient.ApiCallback<ApiClient.UserStatusResponse>() {
            @Override
            public void onSuccess(ApiClient.UserStatusResponse response) {
                boolean shouldTrack = response.shouldTrack();
                
                // Guardar estado para MainActivity
                prefs.edit().putBoolean(LAST_TRACKING_STATE, shouldTrack).apply();
                
                if (shouldTrack) {
                    Log.d(TAG, "Bot indica que debe trackear - Iniciando GPS");
                    startLocationUpdates();
                    updateNotification("🚗 Viaje activo - GPS registrando");
                } else {
                    Log.d(TAG, "Bot indica que NO debe trackear - Servicio en espera");
                    updateNotification("⏸️ Esperando activación desde bot");
                    // No detener el servicio, seguir monitoreando
                }
            }
            
            @Override
            public void onError(String error) {
                Log.d(TAG, "Error consultando bot: " + error);
                updateNotification("📶 Sin conexión con servidor");
                // En caso de error, mantener servicio activo pero sin GPS
            }
        });
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
                Priority.PRIORITY_HIGH_ACCURACY, 30000) // Cada 30 segundos
                .setMinUpdateIntervalMillis(15000) // Mínimo 15 segundos
                .build();
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest, 
                locationCallback, 
                Looper.getMainLooper()
            );
            
            Log.d(TAG, "GPS iniciado - Enviando ubicación cada 30 segundos");
            
        } catch (SecurityException e) {
            Log.e(TAG, "No hay permisos de ubicación", e);
            updateNotification("❌ Sin permisos de ubicación");
        }
    }
    
    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.d(TAG, "GPS detenido");
        }
    }
    
    private void sendLocationToBackend(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        
        Log.d(TAG, String.format("Enviando ubicación: %.6f, %.6f (Viaje desde bot)", latitude, longitude));
        
        apiClient.sendLocation(latitude, longitude, new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String response) {
                Log.d(TAG, "Ubicación enviada exitosamente");
                updateNotification("🚗 Viaje activo - Ubicación enviada");
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error enviando ubicación: " + error);
                updateNotification("🚗 Viaje activo - Error de conexión");
            }
        });
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "UberMax - Bot Telegram",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Notificaciones de UberMax sincronizado con bot Telegram");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification(String message) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UberMax - Bot Sync")
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
        Log.d(TAG, "UberMax LocationService destruido");
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 