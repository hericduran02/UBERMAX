package com.drivermax.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.core.content.ContextCompat;

/**
 * Receiver para auto-inicio después de reinicio del dispositivo
 * Solo reactiva el servicio si había un viaje en curso
 * Completamente transparente para el conductor
 */
public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "DriverMax";
    private static final String PREFS_NAME = "DriverMaxPrefs";
    private static final String IN_TRIP_KEY = "in_trip";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Sistema reiniciado - Verificando estado de viaje");
            
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean wasInTrip = prefs.getBoolean(IN_TRIP_KEY, false);
            
            if (wasInTrip) {
                Log.d(TAG, "Había viaje en curso - Reactivando LocationService");
                Intent serviceIntent = new Intent(context, LocationService.class);
                ContextCompat.startForegroundService(context, serviceIntent);
            } else {
                Log.d(TAG, "No había viaje en curso - No se reactiva el servicio");
            }
        }
    }
} 