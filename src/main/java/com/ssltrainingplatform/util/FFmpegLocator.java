package com.ssltrainingplatform.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class FFmpegLocator {

    private static String cachedFFmpegPath = null;
    private static String cachedFFprobePath = null;

    public static String getFFmpegPath() throws IOException {
        if (cachedFFmpegPath == null) {
            cachedFFmpegPath = extractBinary("ffmpeg");
        }
        return cachedFFmpegPath;
    }

    public static String getFFprobePath() throws IOException {
        if (cachedFFprobePath == null) {
            cachedFFprobePath = extractBinary("ffprobe");
        }
        return cachedFFprobePath;
    }

    private static String extractBinary(String binaryName) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        String resourcePath;
        String fileName = binaryName;

        // 1. Detectar Sistema Operativo y definir rutas
        if (os.contains("win")) {
            fileName += ".exe";
            resourcePath = "/bin/windows/" + fileName;
        } else if (os.contains("mac")) {
            resourcePath = "/bin/mac/" + fileName;
        } else {
            throw new UnsupportedOperationException("Sistema operativo no soportado: " + os);
        }

        // 2. Definir dónde lo vamos a guardar temporalmente en el disco del usuario
        // Usamos la carpeta temporal del sistema (java.io.tmpdir)
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "ssl_video_editor_bin");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        File destinationFile = new File(tempDir, fileName);

        // 3. Extraer solo si no existe ya (para no hacerlo cada vez que abres la app)
        if (!destinationFile.exists()) {
            try (InputStream in = FFmpegLocator.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new IOException("No se encontró el binario en recursos: " + resourcePath);
                }
                Files.copy(in, destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // 4. IMPORTANTE PARA MAC/LINUX: Dar permisos de ejecución
        if (!os.contains("win")) {
            destinationFile.setExecutable(true);
        }

        System.out.println("Binario listo en: " + destinationFile.getAbsolutePath());
        return destinationFile.getAbsolutePath();
    }
}