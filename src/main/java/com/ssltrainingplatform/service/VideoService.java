package com.ssltrainingplatform.service;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.JavaFXFrameConverter;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class VideoService {

    private FFmpegFrameGrabber grabber;
    private final JavaFXFrameConverter fxConverter = new JavaFXFrameConverter();
    private final Java2DFrameConverter aiConverter = new Java2DFrameConverter();

    private Thread playbackThread;
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    private final ImageView targetView;
    private Consumer<Double> onProgressUpdate;
    private Consumer<BufferedImage> onFrameCaptured;
    private long totalDuration = 0;

    private String sourcePath;

    public VideoService(ImageView targetView) {
        this.targetView = targetView;
    }

    public void setOnProgressUpdate(Consumer<Double> onProgressUpdate) {
        this.onProgressUpdate = onProgressUpdate;
    }

    public void setOnFrameCaptured(Consumer<BufferedImage> cb) {
        this.onFrameCaptured = cb;
        System.out.println("🔧 VideoService: Puente de IA configurado correctamente.");
    }

    public void loadVideo(String filePath) throws Exception {
        stop();
        if (grabber != null) grabber.close();

        // 1. CORRECCIÓN: GUARDAR LA RUTA AQUÍ
        this.sourcePath = filePath;

        System.out.println("📂 Cargando video: " + filePath);
        grabber = new FFmpegFrameGrabber(filePath);
        grabber.start();
        totalDuration = grabber.getLengthInTime();
        seek(0);
    }

    public void play() {
        if (isPlaying.get() || grabber == null) return;

        isPlaying.set(true);
        stopRequested.set(false);

        playbackThread = new Thread(() -> {
            try {
                double frameRate = grabber.getFrameRate();
                long sleepTime = (long) (1000 / frameRate);
                System.out.println("▶️ Video iniciado a " + frameRate + " fps");

                while (!stopRequested.get() && isPlaying.get()) {
                    long start = System.currentTimeMillis();

                    Frame frame = grabber.grabImage();
                    if (frame == null) {
                        isPlaying.set(false);
                        break;
                    }

                    Image image = fxConverter.convert(frame);
                    Platform.runLater(() -> targetView.setImage(image));

                    if (onFrameCaptured != null) {
                        try {
                            BufferedImage bi = aiConverter.convert(frame);
                            if (bi != null) {
                                BufferedImage clone = new BufferedImage(bi.getWidth(), bi.getHeight(), bi.getType());
                                clone.getGraphics().drawImage(bi, 0, 0, null);
                                onFrameCaptured.accept(clone);
                            }
                        } catch (Exception e) {
                            System.err.println("⚠️ Error convirtiendo frame para IA: " + e.getMessage());
                        }
                    }

                    if (onProgressUpdate != null && totalDuration > 0) {
                        double current = grabber.getTimestamp();
                        double percent = (current / totalDuration) * 100.0;
                        Platform.runLater(() -> onProgressUpdate.accept(percent));
                    }

                    long process = System.currentTimeMillis() - start;
                    long wait = sleepTime - process;
                    if (wait > 0) Thread.sleep(wait);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    public void pause() { isPlaying.set(false); }

    public void stop() {
        pause();
        try {
            if (grabber != null) { grabber.stop(); grabber.release(); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void seek(double percent) {
        if (grabber == null) return;
        boolean wasPlaying = isPlaying.get();
        isPlaying.set(false);
        try { Thread.sleep(50); } catch (InterruptedException e) {}

        try {
            long position = (long) (totalDuration * (percent / 100.0));
            grabber.setTimestamp(position);
            Frame frame = grabber.grabImage();
            if (frame != null) {
                Image image = fxConverter.convert(frame);
                Platform.runLater(() -> targetView.setImage(image));
            }
        } catch (Exception e) { e.printStackTrace(); }

        if (wasPlaying) play();
    }

    public double getTotalDuration() {
        return totalDuration / 1_000_000.0;
    }

    public boolean isPlaying() { return isPlaying.get(); }

    public String getSourcePath() {
        return sourcePath;
    }

    public double getCurrentTime() {
        // En JavaCV (FFmpeg), el tiempo se obtiene del grabber en microsegundos
        if (grabber != null) {
            return grabber.getTimestamp() / 1_000_000.0; // Convertir de microsegundos a segundos
        }
        return 0.0;
    }
}