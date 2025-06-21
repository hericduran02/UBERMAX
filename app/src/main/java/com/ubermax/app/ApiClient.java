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
 * UberMax - Cliente API súper simple
 * Usa ID automático del dispositivo - SIN registro de usuario
 */
public class ApiClient {
    
    private static final String TAG = "UberMax";
    private static final String BACKEND_URL = "http://tu-servidor.com:8080"; // Cambiar por tu URL
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
     * Sin necesidad de registro manual
     */
    private String getDeviceId() {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }
    
    /**
     * Enviar ubicación al backend automáticamente
     * Usa ID del dispositivo - SIN configuración manual
     */
    public void sendLocation(double latitude, double longitude, ApiCallback<String> callback) {
        try {
            // Crear JSON con ubicación y ID automático del dispositivo
            JSONObject json = new JSONObject();
            json.put("user_id", getDeviceId()); // ID automático del dispositivo
            json.put("latitude", latitude);
            json.put("longitude", longitude);
            
            // Crear request
            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                .url(BACKEND_URL + "/api/location")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
            
            // Enviar de forma asíncrona
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Error enviando ubicación: " + e.getMessage());
                    if (callback != null) {
                        callback.onError("Error de conexión: " + e.getMessage());
                    }
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (response.isSuccessful()) {
                            String responseBody = response.body().string();
                            Log.d(TAG, "Ubicación enviada correctamente");
                            if (callback != null) {
                                callback.onSuccess(responseBody);
                            }
                        } else {
                            String error = "Error HTTP: " + response.code();
                            Log.w(TAG, error);
                            if (callback != null) {
                                callback.onError(error);
                            }
                        }
                    } finally {
                        response.close();
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error creando request: " + e.getMessage());
            if (callback != null) {
                callback.onError("Error interno: " + e.getMessage());
            }
        }
    }
    
    /**
     * Interface para callbacks de la API
     */
    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }
} 