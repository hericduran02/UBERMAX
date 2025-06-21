package com.drivermax.app;

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
 * DriverMax - Asistente transparente para conductores profesionales
 * Ayuda a gestionar viajes de forma ética y transparente
 */
public class MainActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private TextView statusText;
    private Button actionButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        statusText = findViewById(R.id.status_text);
        actionButton = findViewById(R.id.main_action_button);
        
        actionButton.setOnClickListener(v -> checkPermissions());
        
        updateUI();
    }
    
    private void checkPermissions() {
        if (hasLocationPermissions()) {
            startLocationService();
            Toast.makeText(this, "DriverMax activado", Toast.LENGTH_SHORT).show();
        } else {
            requestLocationPermissions();
        }
    }
    
    private boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
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
            boolean granted = grantResults.length > 0 && 
                             grantResults[0] == PackageManager.PERMISSION_GRANTED;
            
            if (granted) {
                startLocationService();
                Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permisos necesarios", Toast.LENGTH_LONG).show();
            }
            updateUI();
        }
    }
    
    private void startLocationService() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }
    
    private void updateUI() {
        if (hasLocationPermissions()) {
            statusText.setText("DriverMax Listo");
            actionButton.setText("Verificar Estado");
        } else {
            statusText.setText("Permisos Pendientes");
            actionButton.setText("Permitir Ubicación");
        }
    }
} 