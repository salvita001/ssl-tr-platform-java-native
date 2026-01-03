package com.ssltrainingplatform.service;

import javafx.scene.canvas.Canvas;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.embed.swing.SwingFXUtils;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class ExportService {

    public void exportVideo(String inputFile, String outputFile, Canvas drawingCanvas) {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile);
             FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputFile, 1920, 1080)) {

            grabber.start();
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFormat("mp4");
            recorder.setFrameRate(30);
            recorder.start();

            Java2DFrameConverter converter = new Java2DFrameConverter();
            Frame videoFrame;

            while ((videoFrame = grabber.grabImage()) != null) {
                // 1. Obtener imagen base del video
                BufferedImage bufImg = converter.convert(videoFrame);

                // 2. Comprobar si hay dibujos para este instante de tiempo
                // (Aquí iría tu lógica de 'if freeze time' o 'if tracking')

                // 3. Capturar tus gráficos vectoriales de JavaFX como imagen
                // Esto es el equivalente a tu 'stageRef.toDataURL()' pero instantáneo en memoria
                WritableImage snapshot = drawingCanvas.snapshot(new SnapshotParameters(), null);
                BufferedImage overlay = SwingFXUtils.fromFXImage(snapshot, null);

                // 4. Fusionar (Stitching)
                Graphics2D g = bufImg.createGraphics();
                g.drawImage(overlay, 0, 0, null); // Pintar flechas sobre el video
                g.dispose();

                // 5. Grabar frame final
                recorder.record(converter.convert(bufImg));
            }

            grabber.stop();
            recorder.stop();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
