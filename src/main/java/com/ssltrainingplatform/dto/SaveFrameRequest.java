package com.ssltrainingplatform.dto;

public class SaveFrameRequest {

    // El ID del frame de imagen base que se editó
    private String frameId;

    // NUEVO CAMPO: El ID único generado por el frontend (uuidv4()) para este punto de pausa.
    private String pausePointId;

    // Las anotaciones dibujadas, serializadas como una cadena JSON
    private String annotationsJson;
    private double duration;

    private int originalWidth;
    private int originalHeight;

    // Constructor vacío
    public SaveFrameRequest() {
    }

    public SaveFrameRequest(String frameId, String pausePointId, double duration, String annotationsJson,
                            int originalWidth, int originalHeight) {
        this.frameId = frameId;
        this.pausePointId = pausePointId;
        this.duration = duration;
        this.annotationsJson = annotationsJson;
        this.originalWidth = originalWidth; // Para el escalado
        this.originalHeight = originalHeight; // Para el escalado
    }

    public String getFrameId() {
        return frameId;
    }

    public void setFrameId(String frameId) {
        this.frameId = frameId;
    }

    public String getPausePointId() {
        return pausePointId;
    }

    public void setPausePointId(String pausePointId) {
        this.pausePointId = pausePointId;
    }

    public String getAnnotationsJson() {
        return annotationsJson;
    }

    public void setAnnotationsJson(String annotationsJson) {
        this.annotationsJson = annotationsJson;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public int getOriginalWidth() {
        return originalWidth;
    }

    public void setOriginalWidth(int originalWidth) {
        this.originalWidth = originalWidth;
    }

    public int getOriginalHeight() {
        return originalHeight;
    }

    public void setOriginalHeight(int originalHeight) {
        this.originalHeight = originalHeight;
    }

    @Override
    public String toString() {
        return "SaveFrameRequest{" +
                "frameId='" + frameId + '\'' +
                ", pausePointId='" + pausePointId + '\'' +
                ", annotationsJson='" + annotationsJson + '\'' +
                ", duration=" + duration +
                ", originalWidth=" + originalWidth +
                ", originalHeight=" + originalHeight +
                '}';
    }
}
