package com.drivermax.app;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * DriverMax - Cliente API transparente
 * Solo envía ubicación cuando el conductor está en un viaje activo
 * Toda la comunicación es transparente y ética
 */
public class ApiClient {
    
    private static final String TAG = "ApiClient";
    private static final String BASE_URL = "http://4.237.92.83:8080";
    
    private final OkHttpClient client;
    private final String deviceId;
    
    public ApiClient(Context context) {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
            
        this.deviceId = Settings.Secure.getString(
            context.getContentResolver(), 
            Settings.Secure.ANDROID_ID
        );
    }
    
    /**
     * Enviar ubicación al backend durante un viaje activo
     * SOLO se llama cuando el conductor ha iniciado un viaje
     * Completamente transparente - el conductor sabe exactamente cuándo se envía
     */
    public void sendLocation(double latitude, double longitude, ApiCallback<String> callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("user_id", deviceId);
            json.put("latitude", latitude);
            json.put("longitude", longitude);
            json.put("timestamp", System.currentTimeMillis() / 1000);
            
            RequestBody body = RequestBody.create(
                json.toString(),
                MediaType.get("application/json")
            );
            
            Request request = new Request.Builder()
                .url(BASE_URL + "/location")
                .post(body)
                .build();
                
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Error HTTP", e);
                    callback.onError(e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    
                    if (response.isSuccessful()) {
                        callback.onSuccess(responseBody);
                    } else {
                        callback.onError("HTTP " + response.code());
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error JSON", e);
            callback.onError(e.getMessage());
        }
    }
    
    /**
     * Callback interface para respuestas del API
     */
    public interface ApiCallback<T> {
        void onSuccess(T response);
        void onError(String error);
    }
} 