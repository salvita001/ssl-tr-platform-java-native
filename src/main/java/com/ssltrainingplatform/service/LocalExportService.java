package com.ssltrainingplatform.service;

import com.ssltrainingplatform.util.FFmpegLocator;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LocalExportService {

    private final FFmpeg ffmpeg;
    private final FFprobe ffprobe;

    public LocalExportService() throws IOException {
        // ✅ CAMBIO: Usamos el Locator dinámico
        String ffmpegPath = FFmpegLocator.getFFmpegPath();
        String ffprobePath = FFmpegLocator.getFFprobePath();

        this.ffmpeg = new FFmpeg(ffmpegPath);
        this.ffprobe = new FFprobe(ffprobePath);

        System.out.println("Motor de video iniciado con: " + ffmpegPath);
    }

    public void renderProject(String sourceVideoPath, List<ExportJobSegment> segments, File outputFile)
            throws IOException {
        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
        List<String> tempFiles = new ArrayList<>();
        File tempDir = Files.createTempDirectory("editor_render_").toFile();

        try {
            int index = 0;
            for (ExportJobSegment seg : segments) {
                String tempName = new File(tempDir, "part_" + String.format("%03d", index++) + ".mp4").getAbsolutePath();

                FFmpegBuilder builder = new FFmpegBuilder().overrideOutputFiles(true);

                if (seg.isFreezeFrame) {
                    // --- CASO 1: IMAGEN CONGELADA (DIBUJO) ---
                    // Añadimos pista de audio muda para evitar cortes de sonido al unir
                    builder.setInput(seg.imagePath)
                            .addExtraArgs("-f", "lavfi")
                            .addExtraArgs("-i", "anullsrc=channel_layout=stereo:sample_rate=44100")
                            .addOutput(tempName)
                            .setDuration((long) (seg.duration * 1000), TimeUnit.MILLISECONDS)
                            .setVideoCodec("libx264")
                            .setAudioCodec("aac") // Importante: Codec de audio
                            .setAudioSampleRate(44100)
                            .setVideoFrameRate(30)
                            .setVideoResolution(1280, 720)
                            .addExtraArgs("-pix_fmt", "yuv420p")
                            .addExtraArgs("-shortest") // Cortar cuando acabe la imagen
                            .done();
                } else {
                    // --- CASO 2: VIDEO EN MOVIMIENTO (CORREGIDO) ---

                    // ✅ CAMBIO 1: No usamos addExtraArgs("-ss") antes del input.
                    // Esto fuerza a FFmpeg a leer el archivo de forma segura.

                    builder.setInput(sourceVideoPath)
                            .addOutput(tempName)

                            // ✅ CAMBIO 2: Ponemos el Seek (StartOffset) AQUÍ, en la salida.
                            // Esto activa el "Output Seeking" (lento pero seguro).
                            .setStartOffset((long) (seg.startSource * 1000), TimeUnit.MILLISECONDS)

                            .setDuration((long) ((seg.endSource - seg.startSource) * 1000), TimeUnit.MILLISECONDS)
                            .setVideoCodec("libx264")
                            .setAudioCodec("aac")
                            .setAudioSampleRate(44100)
                            .setVideoFrameRate(30)
                            .setVideoResolution(1280, 720)
                            .addExtraArgs("-pix_fmt", "yuv420p")

                            // ✅ CAMBIO 3: Filtro de seguridad para video corrupto
                            // Si encuentra un frame roto, lo ignora en lugar de fallar
                            .addExtraArgs("-err_detect", "ignore_err")
                            .done();
                }

                System.out.println("Renderizando parte: " + index + " (Tipo: " + (seg.isFreezeFrame ? "Freeze" : "Video") + ")");
                executor.createJob(builder).run();
                tempFiles.add(tempName);
            }

            // 2. CONCATENAR
            File listFile = new File(tempDir, "list.txt");
            try (FileWriter writer = new FileWriter(listFile)) {
                for (String path : tempFiles) {
                    writer.write("file '" + path.replace("\\", "/") + "'\n");
                }
            }

            FFmpegBuilder concatBuilder = new FFmpegBuilder()
                    .setInput(listFile.getAbsolutePath())
                    .addExtraArgs("-f", "concat")
                    .addExtraArgs("-safe", "0")
                    .addOutput(outputFile.getAbsolutePath())
                    .setVideoCodec("copy")
                    .setAudioCodec("copy") // Copiar también el audio sin recodificar
                    .done();

            System.out.println("Uniendo archivo final...");
            executor.createJob(concatBuilder).run();

        } finally {
            // tempDir.delete(); // Descomenta esto en producción
        }
    }

    // Clase auxiliar simple (DTO) para pasar datos del Controller al Service
    public static class ExportJobSegment {
        boolean isFreezeFrame;
        double startSource;
        double endSource;
        double duration;
        String imagePath; // Ruta al PNG temporal si es freeze

        // Constructor para Video
        public static ExportJobSegment video(double start, double end) {
            ExportJobSegment s = new ExportJobSegment();
            s.isFreezeFrame = false;
            s.startSource = start;
            s.endSource = end;
            return s;
        }

        // Constructor para Freeze
        public static ExportJobSegment freeze(String imgPath, double duration) {
            ExportJobSegment s = new ExportJobSegment();
            s.isFreezeFrame = true;
            s.imagePath = imgPath;
            s.duration = duration;
            return s;
        }
    }
}