package com.ubermax.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * UberMax - Asistente transparente para conductores profesionales
 * Ayuda a gestionar viajes de forma ética y transparente
 */
public class MainActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String PREFS_NAME = "UberMaxPrefs";
    private static final String TRIPS_COUNT_KEY = "trips_count";
    private static final String TOTAL_DISTANCE_KEY = "total_distance";
    private static final String ACTIVE_TIME_KEY = "active_time";
    
    private TextView statusText;
    private TextView tripsCount;
    private TextView totalDistance;
    private TextView activeTime;
    private TextView botInstructions;
    private Button permissionsButton;
    
    private SharedPreferences prefs;
    private ApiClient apiClient;
    private Handler statusHandler;
    private String androidId;
    private boolean isTrackingActive = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        apiClient = new ApiClient(this);
        statusHandler = new Handler(Looper.getMainLooper());
        
        // Obtener Android ID único
        androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        
        initializeViews();
        loadStatistics();
        startStatusMonitoring();
        updateUI();
    }
    
    private void initializeViews() {
        statusText = findViewById(R.id.status_text);
        tripsCount = findViewById(R.id.trips_count);
        totalDistance = findViewById(R.id.total_distance);
        activeTime = findViewById(R.id.active_time);
        botInstructions = findViewById(R.id.bot_instructions);
        permissionsButton = findViewById(R.id.main_action_button);
        
        permissionsButton.setOnClickListener(v -> handlePermissions());
    }
    
    private void loadStatistics() {
        int trips = prefs.getInt(TRIPS_COUNT_KEY, 0);
        float distance = prefs.getFloat(TOTAL_DISTANCE_KEY, 0.0f);
        int timeMinutes = prefs.getInt(ACTIVE_TIME_KEY, 0);
        
        // Formato minimalista para el nuevo diseño
        tripsCount.setText(String.valueOf(trips));
        totalDistance.setText(String.format("%.1f km", distance));
        activeTime.setText(timeMinutes + " min");
    }
    
    private void handlePermissions() {
        if (!hasLocationPermissions()) {
            showPermissionDialog();
        } else {
            Toast.makeText(this, "✅ Permisos ya concedidos. Usa el bot de Telegram para activar viajes.", Toast.LENGTH_LONG).show();
        }
    }
    
    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
            .setTitle("🔒 Permisos de Ubicación")
            .setMessage("UberMax necesita ubicación para:\n\n" +
                       "• Tracking automático de viajes\n" +
                       "• Cálculo preciso de distancias\n" +
                       "• Estadísticas en tiempo real\n\n" +
                       "Solo se activa con comandos del bot.")
            .setPositiveButton("Permitir", (dialog, which) -> requestLocationPermissions())
            .setNegativeButton("Cancelar", null)
            .show();
    }
    
    private void startStatusMonitoring() {
        // Consultar estado cada 30 segundos
        statusHandler.post(new Runnable() {
            @Override
            public void run() {
                checkBotStatus();
                statusHandler.postDelayed(this, 30000); // 30 segundos
            }
        });
    }
    
    private void checkBotStatus() {
        if (!hasLocationPermissions()) {
            return; // No verificar si no hay permisos
        }
        
        // Consultar backend con Android ID
        apiClient.checkUserStatus(androidId, new ApiClient.ApiCallback<ApiClient.UserStatusResponse>() {
            @Override
            public void onSuccess(ApiClient.UserStatusResponse response) {
                boolean shouldTrack = response.shouldTrack();
                
                if (shouldTrack != isTrackingActive) {
                    isTrackingActive = shouldTrack;
                    updateTrackingState();
                }
                
                runOnUiThread(() -> updateUI());
            }
            
            @Override
            public void onError(String error) {
                // Log silencioso, no molestar al usuario constantemente
                runOnUiThread(() -> {
                    if (!isTrackingActive) {
                        statusText.setText("📶 Conectando con servidor...");
                    }
                });
            }
        });
    }
    
    private void updateTrackingState() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        
        if (isTrackingActive) {
            // Iniciar servicio de ubicación
            ContextCompat.startForegroundService(this, serviceIntent);
            
            // Incrementar contador de viajes
            int currentTrips = prefs.getInt(TRIPS_COUNT_KEY, 0);
            prefs.edit().putInt(TRIPS_COUNT_KEY, currentTrips + 1).apply();
            loadStatistics();
            
            Toast.makeText(this, "🚗 ¡Viaje activado desde Telegram! GPS registrando ruta.", Toast.LENGTH_LONG).show();
        } else {
            // Detener servicio
            stopService(serviceIntent);
            Toast.makeText(this, "🏁 Viaje terminado desde Telegram. GPS desactivado.", Toast.LENGTH_LONG).show();
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
                updateUI();
                Toast.makeText(this, "✅ Permisos concedidos. Ahora usa el bot de Telegram para activar viajes.", Toast.LENGTH_LONG).show();
                startStatusMonitoring(); // Iniciar monitoreo
            } else {
                Toast.makeText(this, "UberMax necesita permisos de ubicación para funcionar con el bot.", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void updateUI() {
        if (!hasLocationPermissions()) {
            statusText.setText("⚠️ Permisos Requeridos");
            permissionsButton.setText("Conceder Permisos");
            botInstructions.setText("Permite ubicación para continuar");
        } else if (isTrackingActive) {
            statusText.setText("🚗 Viaje Activo");
            permissionsButton.setText("✅ Sistema Activo");
            botInstructions.setText("Viaje en curso • Usa /terminar_viaje para finalizar");
        } else {
            statusText.setText("✅ Sistema Listo");
            permissionsButton.setText("✅ Configurado");
            botInstructions.setText("Usa /tomar_viaje para activar tracking automático");
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadStatistics();
        updateUI();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (statusHandler != null) {
            statusHandler.removeCallbacksAndMessages(null);
        }
    }
} 