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
}