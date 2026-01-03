package com.ssltrainingplatform; // Asegúrate de que el paquete es correcto

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class SslTpApp extends Application {

    // 1. VARIABLE GLOBAL PARA GUARDAR EL TOKEN
    public static String AUTH_TOKEN = null;

    @Override
    public void start(Stage stage) throws IOException {

        // 2. LEER ARGUMENTOS (Buscamos "token=...")
        Parameters params = getParameters();
        List<String> list = params.getRaw();

        for (String arg : list) {
            // El registro de Windows nos pasa: token=ssltp://TU_TOKEN_REAL
            if (arg.startsWith("token=")) {
                String rawToken = arg.substring(6); // Quitar "token="

                // LIMPIEZA DEL PROTOCOLO (NUEVO)
                if (rawToken.startsWith("ssltp://")) {
                    AUTH_TOKEN = rawToken.substring(8); // Quitar "ssltp://"
                    // A veces los navegadores añaden una barra al final
                    if (AUTH_TOKEN.endsWith("/")) {
                        AUTH_TOKEN = AUTH_TOKEN.substring(0, AUTH_TOKEN.length() - 1);
                    }
                } else {
                    AUTH_TOKEN = rawToken;
                }

                System.out.println("🔑 Token limpio recibido: " + AUTH_TOKEN.substring(0, 10) + "...");
                break;
            }
        }

        // Carga normal de la interfaz
        FXMLLoader fxmlLoader = new FXMLLoader(SslTpApp.class.getResource("/layout.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1280, 720);

        stage.setTitle("Ssl Tp Java - Pro Edition");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args); // Importante pasar args aquí también
    }
}