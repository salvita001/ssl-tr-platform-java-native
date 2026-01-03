package com.ssltrainingplatform.service;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.TreeMap;

public class FilmstripService {

    // Usamos TreeMap para poder buscar tiempos aproximados (floorEntry)
    public TreeMap<Double, Image> generateFilmstrip(File videoFile, double intervalSeconds, double thumbHeight) {
        TreeMap<Double, Image> thumbnails = new TreeMap<>();

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
            grabber.start();

            long duration = grabber.getLengthInTime(); // Microsegundos
            long intervalMicro = (long) (intervalSeconds * 1_000_000);

            Java2DFrameConverter converter = new Java2DFrameConverter();

            // Recorremos el video a intervalos fijos
            for (long t = 0; t < duration; t += intervalMicro) {
                grabber.setTimestamp(t);

                // A veces grabImage devuelve null en frames intermedios, intentamos capturar
                var frame = grabber.grabImage();
                if (frame != null) {
                    BufferedImage buf = converter.convert(frame);
                    if (buf != null) {
                        // Escalar la imagen para ahorrar RAM (importante para videos largos)
                        double ratio = (double) buf.getWidth() / buf.getHeight();
                        int w = (int) (thumbHeight * ratio);
                        int h = (int) thumbHeight;

                        // Crear imagen JavaFX
                        Image fxImage = SwingFXUtils.toFXImage(buf, null);

                        // Guardamos: Clave = Segundo exacto, Valor = Imagen
                        thumbnails.put(t / 1_000_000.0, fxImage);
                    }
                }
            }
            grabber.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return thumbnails;
    }
}