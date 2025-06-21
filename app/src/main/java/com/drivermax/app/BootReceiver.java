package com.drivermax.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * DriverMax - Receptor de inicio automático
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
            
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean wasInTrip = prefs.getBoolean(IN_TRIP_KEY, false);
            
            if (wasInTrip) {
                Log.d(TAG, "DriverMax: Reactivando seguimiento de viaje después de reinicio");
                
                Intent serviceIntent = new Intent(context, LocationService.class);
                context.startForegroundService(serviceIntent);
                
                Log.d(TAG, "DriverMax: Servicio de viaje reactivado transparentemente");
            } else {
                Log.d(TAG, "DriverMax: No había viaje activo - Sin reactivación");
            }
        }
    }
} 