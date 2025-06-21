package com.drivermax.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * DriverMax - Asistente transparente para conductores profesionales
 * Ayuda a gestionar viajes de forma ética y transparente
 */
public class MainActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String PREFS_NAME = "DriverMaxPrefs";
    private static final String TRIPS_COUNT_KEY = "trips_count";
    private static final String TOTAL_DISTANCE_KEY = "total_distance";
    private static final String ACTIVE_TIME_KEY = "active_time";
    private static final String IN_TRIP_KEY = "in_trip";
    
    private TextView statusText;
    private TextView tripsCount;
    private TextView totalDistance;
    private TextView activeTime;
    private Button mainActionButton;
    
    private SharedPreferences prefs;
    private boolean isInTrip = false;
    private long tripStartTime = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        initializeViews();
        loadStatistics();
        updateUI();
    }
    
    private void initializeViews() {
        statusText = findViewById(R.id.status_text);
        tripsCount = findViewById(R.id.trips_count);
        totalDistance = findViewById(R.id.total_distance);
        activeTime = findViewById(R.id.active_time);
        mainActionButton = findViewById(R.id.main_action_button);
        
        mainActionButton.setOnClickListener(v -> handleMainAction());
    }
    
    private void loadStatistics() {
        int trips = prefs.getInt(TRIPS_COUNT_KEY, 0);
        float distance = prefs.getFloat(TOTAL_DISTANCE_KEY, 0.0f);
        int timeMinutes = prefs.getInt(ACTIVE_TIME_KEY, 0);
        isInTrip = prefs.getBoolean(IN_TRIP_KEY, false);
        
        tripsCount.setText("🚗 Viajes completados: " + trips);
        totalDistance.setText("📍 Distancia recorrida: " + String.format("%.1f km", distance));
        activeTime.setText("⏱️ Tiempo activo: " + timeMinutes + " min");
    }
    
    private void handleMainAction() {
        if (!hasLocationPermissions()) {
            showPermissionDialog();
        } else if (!isInTrip) {
            showStartTripDialog();
        } else {
            showEndTripDialog();
        }
    }
    
    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
            .setTitle("🔒 Permisos de Ubicación")
            .setMessage("DriverMax necesita acceso a tu ubicación para:\n\n" +
                       "• Confirmar inicio y fin de viajes\n" +
                       "• Calcular distancias recorridas\n" +
                       "• Generar estadísticas precisas\n\n" +
                       "Tu ubicación SOLO se envía cuando estés en un viaje activo.")
            .setPositiveButton("Permitir", (dialog, which) -> requestLocationPermissions())
            .setNegativeButton("Cancelar", null)
            .show();
    }
    
    private void showStartTripDialog() {
        new AlertDialog.Builder(this)
            .setTitle("🚗 Iniciar Viaje")
            .setMessage("¿Estás listo para comenzar un nuevo viaje?\n\n" +
                       "DriverMax comenzará a registrar:\n" +
                       "• Tu ruta y distancia\n" +
                       "• Tiempo del viaje\n" +
                       "• Ubicación para estadísticas")
            .setPositiveButton("Iniciar Viaje", (dialog, which) -> startTrip())
            .setNegativeButton("Cancelar", null)
            .show();
    }
    
    private void showEndTripDialog() {
        new AlertDialog.Builder(this)
            .setTitle("🏁 Terminar Viaje")
            .setMessage("¿Has terminado el viaje actual?\n\n" +
                       "Se guardará automáticamente en tus estadísticas.")
            .setPositiveButton("Terminar Viaje", (dialog, which) -> endTrip())
            .setNegativeButton("Continuar", null)
            .show();
    }
    
    private void startTrip() {
        isInTrip = true;
        tripStartTime = System.currentTimeMillis();
        
        prefs.edit().putBoolean(IN_TRIP_KEY, true).apply();
        
        // Iniciar servicio de ubicación
        Intent serviceIntent = new Intent(this, LocationService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        
        updateUI();
        Toast.makeText(this, "✅ ¡Viaje iniciado! DriverMax está registrando tu ruta.", Toast.LENGTH_LONG).show();
    }
    
    private void endTrip() {
        isInTrip = false;
        
        // Calcular duración del viaje
        long tripDuration = (System.currentTimeMillis() - tripStartTime) / 60000; // minutos
        
        // Actualizar estadísticas
        int currentTrips = prefs.getInt(TRIPS_COUNT_KEY, 0);
        int currentTime = prefs.getInt(ACTIVE_TIME_KEY, 0);
        
        prefs.edit()
            .putBoolean(IN_TRIP_KEY, false)
            .putInt(TRIPS_COUNT_KEY, currentTrips + 1)
            .putInt(ACTIVE_TIME_KEY, currentTime + (int)tripDuration)
            .apply();
        
        // Detener servicio de ubicación
        Intent serviceIntent = new Intent(this, LocationService.class);
        stopService(serviceIntent);
        
        loadStatistics();
        updateUI();
        
        Toast.makeText(this, "✅ Viaje terminado. Estadísticas actualizadas.", Toast.LENGTH_LONG).show();
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
                updateUI();
                Toast.makeText(this, "✅ Permisos concedidos. ¡DriverMax está listo!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "DriverMax necesita permisos de ubicación para funcionar.", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void updateUI() {
        if (!hasLocationPermissions()) {
            statusText.setText("⚠️ Permisos Pendientes");
            mainActionButton.setText("Permitir Ubicación");
        } else if (isInTrip) {
            statusText.setText("🚗 En Viaje Activo");
            mainActionButton.setText("Terminar Viaje");
        } else {
            statusText.setText("✅ DriverMax Listo");
            mainActionButton.setText("Iniciar Viaje");
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadStatistics();
        updateUI();
    }
} 