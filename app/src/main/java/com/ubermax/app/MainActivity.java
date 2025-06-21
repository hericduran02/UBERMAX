package com.ubermax.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * UberMax - Actividad principal súper simple
 * Solo pide permisos GPS y mantiene el servicio corriendo
 */
public class MainActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private TextView statusText;
    private Button permissionButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Inicializar vistas
        statusText = findViewById(R.id.status_text);
        permissionButton = findViewById(R.id.permission_button);
        
        // Configurar botón
        permissionButton.setOnClickListener(v -> checkAndRequestPermissions());
        
        // Verificar permisos al iniciar
        checkAndRequestPermissions();
    }
    
    private void checkAndRequestPermissions() {
        if (hasLocationPermissions()) {
            // Permisos OK - iniciar servicio
            startLocationService();
            updateUI(true);
        } else {
            // Pedir permisos
            requestLocationPermissions();
        }
    }
    
    private boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestLocationPermissions() {
        String[] permissions = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        };
        
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                startLocationService();
                updateUI(true);
                Toast.makeText(this, "¡UberMax activado!", Toast.LENGTH_SHORT).show();
            } else {
                updateUI(false);
                Toast.makeText(this, "Se necesitan permisos de ubicación", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void startLocationService() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }
    
    private void updateUI(boolean hasPermissions) {
        if (hasPermissions) {
            statusText.setText("✅ UberMax funcionando\n\nLa app está enviando tu ubicación al backend cuando tengas zona preferencial activa en el bot de Telegram.");
            permissionButton.setText("Verificar Estado");
        } else {
            statusText.setText("❌ Permisos necesarios\n\nNecesitas dar permisos de ubicación para que UberMax funcione.");
            permissionButton.setText("Dar Permisos");
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Actualizar UI cada vez que la app regresa al primer plano
        updateUI(hasLocationPermissions());
    }
} 