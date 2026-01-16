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
                // CAMBIO 1: Usamos extensión .ts (Transport Stream) para evitar errores de unión
                String tempName = new File(tempDir, "part_" + String.format("%03d", index++) + ".ts").getAbsolutePath();
                FFmpegBuilder builder = new FFmpegBuilder().overrideOutputFiles(true);

                if (seg.isFreezeFrame) {
                    // --- CASO 1: IMAGEN CONGELADA ---
                    builder.setInput(seg.imagePath)
                            .addExtraArgs("-f", "lavfi")
                            .addExtraArgs("-i", "anullsrc=channel_layout=stereo:sample_rate=44100")
                            .addOutput(tempName)
                            .setVideoFilter("loop=loop=-1:size=1:start=0")
                            .setDuration((long) (seg.duration * 1000), TimeUnit.MILLISECONDS)
                            .setVideoCodec("libx264")
                            .setAudioCodec("aac")
                            .setAudioSampleRate(44100)
                            .setVideoFrameRate(30)
                            .setVideoResolution(1280, 720)
                            .addExtraArgs("-pix_fmt", "yuv420p")
                            // CAMBIO 2: Filtros necesarios para formato TS
                            .addExtraArgs("-bsf:v", "h264_mp4toannexb")
                            .addExtraArgs("-f", "mpegts")
                            .done();
                } else {
                    // --- CASO 2: VIDEO ---
                    builder.setInput(sourceVideoPath)
                            .addOutput(tempName)
                            .setStartOffset((long) (seg.startSource * 1000), TimeUnit.MILLISECONDS)
                            .setDuration((long) ((seg.endSource - seg.startSource) * 1000), TimeUnit.MILLISECONDS)
                            .setVideoCodec("libx264")
                            .setAudioCodec("aac")
                            .setAudioSampleRate(44100)
                            .setVideoFrameRate(30)
                            .setVideoResolution(1280, 720)
                            .addExtraArgs("-pix_fmt", "yuv420p")
                            .addExtraArgs("-err_detect", "ignore_err")
                            // CAMBIO 2: Filtros necesarios para formato TS
                            .addExtraArgs("-bsf:v", "h264_mp4toannexb")
                            .addExtraArgs("-f", "mpegts")
                            .done();
                }

                System.out.println("Renderizando parte: " + index + " (Tipo: " + (seg.isFreezeFrame ? "Freeze" : "Video") + ")");
                executor.createJob(builder).run();
                tempFiles.add(tempName);
            }

            // 2. CONCATENAR (Ahora une archivos .ts que son mucho más estables)
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
                    .setVideoCodec("libx264")  // Recodificamos para generar el MP4 final limpio
                    .setAudioCodec("aac")
                    .setPreset("fast")
                    .done();

            System.out.println("Uniendo archivo final (recodificando desde TS)...");
            executor.createJob(concatBuilder).run();

        } finally {
            // tempDir.delete();
        }
    }

    public static class ExportJobSegment {
        boolean isFreezeFrame;
        double startSource;
        double endSource;
        double duration;
        String imagePath;

        public static ExportJobSegment video(double start, double end) {
            ExportJobSegment s = new ExportJobSegment();
            s.isFreezeFrame = false;
            s.startSource = start;
            s.endSource = end;
            return s;
        }

        public static ExportJobSegment freeze(String imgPath, double duration) {
            ExportJobSegment s = new ExportJobSegment();
            s.isFreezeFrame = true;
            s.imagePath = imgPath;
            s.duration = duration;
            return s;
        }
    }
}