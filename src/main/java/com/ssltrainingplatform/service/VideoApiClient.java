package com.ssltrainingplatform.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ssltrainingplatform.dto.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VideoApiClient {

    // Asegúrate de que esto apunta a tu backend Spring Boot
    private static final String BASE_URL = "http://localhost:8080/api/video";

    private final HttpClient client;
    private final Gson gson;

    // VARIABLE PARA EL TOKEN
    private String authToken;

    public VideoApiClient() {
        this.client = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    // MÉTODO PARA SETEAR EL TOKEN (Lo llamarás tras el login)
    public void setAuthToken(String token) {
        this.authToken = token;
    }

    // 1. SUBIR VIDEO (Multipart manual)
    public String uploadVideo(File file) throws Exception {
        String boundary = "---" + UUID.randomUUID().toString();

        // Leemos el archivo
        byte[] fileBytes = Files.readAllBytes(file.toPath());

        // Construimos el cuerpo Multipart manualmente
        String header = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n" +
                "Content-Type: video/mp4\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";

        // Unimos Header + Archivo + Footer
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] footerBytes = footer.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[headerBytes.length + fileBytes.length + footerBytes.length];

        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, body, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + fileBytes.length, footerBytes.length);

        // 1. Crear el Builder con la configuración básica
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary);

        // 2. AÑADIR TOKEN (Solo si lo tenemos guardado)
        if (authToken != null && !authToken.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + authToken);
        }

        // 3. Construir y enviar la petición final
        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();


        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            // Tu VideoController devuelve un Map {"videoId": "..."}
            // Parseamos la respuesta
            UploadResponse resp = gson.fromJson(response.body(), UploadResponse.class);
            return resp.videoId;
        } else {
            throw new IOException("Error subiendo video: " + response.statusCode());
        }
    }

    // 2. EXPORTAR VIDEO (Enviar JSON, recibir Bytes)
    public byte[] exportVideo(ExportRequest exportRequest) throws Exception {
        String jsonBody = gson.toJson(exportRequest);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/export"))
                .header("Content-Type", "application/json");

        // --- AÑADIR TOKEN SI EXISTE ---
        if (authToken != null && !authToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + authToken);
        }
        // ------------------------------

        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();

        // Recibimos los bytes del video (MP4) directamente
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new IOException("Error en exportación remota: " + response.statusCode());
        }
    };

    // 3. CAPTURAR FRAME (Paso 1: Obtener la imagen base)
    public String captureFrame(String videoId, double timestamp) throws Exception {
        FrameRequest req = new FrameRequest();
        req.setVideoId(videoId);
        req.setTimestamp(timestamp);

        String jsonBody = gson.toJson(req);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/frame")) // Asegúrate de que esta es la ruta en tu VideoController
                .header("Content-Type", "application/json");

        if (authToken != null) builder.header("Authorization", "Bearer " + authToken);

        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            FrameResponse resp = gson.fromJson(response.body(), FrameResponse.class);
            return resp.getFrameId(); // El ID de la imagen generada (ej: "frame_123.jpg")
        } else {
            throw new IOException("Error capturando frame: " + response.statusCode());
        }
    }

    // En VideoApiClient.java

    public void saveAnnotation(SaveFrameRequest req) throws Exception {
        String jsonBody = gson.toJson(req);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/frame/save"))
                .header("Content-Type", "application/json");

        if (authToken != null) builder.header("Authorization", "Bearer " + authToken);

        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // ✅ CORRECCIÓN: Añadimos el código 204 a la lista de éxitos
        int status = response.statusCode();
        if (status != 200 && status != 201 && status != 204) {
            throw new IOException("Error guardando anotación: " + status);
        }

        // Si llega aquí con 204, el método termina felizmente y el EditorController sigue su curso
        System.out.println("✅ Anotación guardada correctamente en el servidor (Status 204)");
    }

    // En VideoApiClient.java

    public List<FrameAnnotationResponse> getAnnotations(String videoId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/annotations" + videoId))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            // Usamos un TypeToken de Gson para deserializar una lista
            Type listType = new TypeToken<List<FrameAnnotationResponse>>(){}.getType();
            return gson.fromJson(response.body(), listType);
        }
        return new ArrayList<>();
    }

    // Clase auxiliar interna para leer la respuesta de subida
    private static class UploadResponse { String videoId; }
}