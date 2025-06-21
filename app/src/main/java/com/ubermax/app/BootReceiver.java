package com.ubermax.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.core.content.ContextCompat;

/**
 * UberMax - Receptor para iniciar automáticamente después del reboot
 */
public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "UberMax";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Log.d(TAG, "Dispositivo reiniciado - iniciando UberMax");
            
            // Iniciar servicio de ubicación
            Intent serviceIntent = new Intent(context, LocationService.class);
            ContextCompat.startForegroundService(context, serviceIntent);
            
            Log.d(TAG, "Servicio UberMax iniciado automáticamente");
        }
    }
} 