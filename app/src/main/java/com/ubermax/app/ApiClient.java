package com.ubermax.app;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;

/**
 * Cliente para comunicación con el backend UberMax
 * Envía ubicaciones y consulta estado del bot Telegram
 */
public class ApiClient {
    
    private static final String TAG = "UberMax";
    private static final String BACKEND_URL = "http://4.237.92.83:8080"; // URL del backend VPS
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private Context context;
    private OkHttpClient client;
    
    public ApiClient(Context context) {
        this.context = context;
        // Cliente HTTP simple con timeouts
        client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }
    
    /**
     * Obtiene un ID único automático del dispositivo
     * Esto se usa para identificar al conductor sin registro manual
     */
    private String getDeviceId() {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }
    
    /**
     * Consulta el estado del usuario en el backend para ver si debe trackear
     * Usa Android ID para identificar el dispositivo automáticamente
     */
    public void checkUserStatus(String androidId, ApiCallback<UserStatusResponse> callback) {
        Request request = new Request.Builder()
            .url(BACKEND_URL + "/api/users/" + androidId + "/status")
            .get()
            .build();
            
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d(TAG, "Error consultando estado: " + e.getMessage());
                callback.onError("Error de conexión: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    
                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(responseBody);
                        JSONObject data = json.getJSONObject("data");
                        
                        boolean shouldTrack = data.getBoolean("should_track");
                        String zoneStatus = data.getString("zone_status");
                        String accountStatus = data.getString("account_status");
                        
                        UserStatusResponse userStatus = new UserStatusResponse(shouldTrack, zoneStatus, accountStatus);
                        callback.onSuccess(userStatus);
                        
                        Log.d(TAG, "Estado del usuario: should_track=" + shouldTrack + ", zone_status=" + zoneStatus);
                    } else {
                        Log.d(TAG, "Error del servidor: " + response.code());
                        callback.onError("Error del servidor: " + response.code());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parseando respuesta: " + e.getMessage());
                    callback.onError("Error procesando respuesta");
                }
            }
        });
    }
    
    /**
     * Envía ubicación al backend cuando hay viaje activo
     * Solo se llama durante viajes conscientes del usuario
     */
    public void sendLocation(double latitude, double longitude, ApiCallback<String> callback) {
        try {
            // Crear JSON con ubicación y Android ID automático
            JSONObject json = new JSONObject();
            json.put("user_id", getDeviceId()); // Android ID único
            json.put("latitude", latitude);
            json.put("longitude", longitude);
            json.put("timestamp", System.currentTimeMillis());
            json.put("app", "drivermax_android");
            json.put("trip_status", "active"); // Indica que está en viaje activo
            
            Log.d(TAG, String.format("Enviando ubicación de viaje: %.6f, %.6f", latitude, longitude));
            
            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                .url(BACKEND_URL + "/api/location")
                .post(body)
                .build();
                
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    String error = "Error de conexión: " + e.getMessage();
                    Log.e(TAG, error);
                    callback.onError(error);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body() != null ? response.body().string() : "OK";
                        Log.d(TAG, "Ubicación enviada exitosamente durante viaje");
                        callback.onSuccess(responseBody);
                    } else {
                        String error = "Error del servidor: " + response.code();
                        Log.e(TAG, error);
                        callback.onError(error);
                    }
                    response.close();
                }
            });
            
        } catch (Exception e) {
            String error = "Error creando solicitud: " + e.getMessage();
            Log.e(TAG, error);
            callback.onError(error);
        }
    }
    
    /**
     * Clase para respuesta del estado del usuario desde el backend
     */
    public static class UserStatusResponse {
        private final boolean shouldTrack;
        private final String zoneStatus;
        private final String accountStatus;
        
        public UserStatusResponse(boolean shouldTrack, String zoneStatus, String accountStatus) {
            this.shouldTrack = shouldTrack;
            this.zoneStatus = zoneStatus;
            this.accountStatus = accountStatus;
        }
        
        public boolean shouldTrack() {
            return shouldTrack;
        }
        
        public String getZoneStatus() {
            return zoneStatus;
        }
        
        public String getAccountStatus() {
            return accountStatus;
        }
    }
    
    /**
     * Callback para respuestas del API
     */
    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }
} 