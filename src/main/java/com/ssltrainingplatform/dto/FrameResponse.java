package com.ssltrainingplatform.dto;

public class FrameResponse {
    // Este ID es el nombre único del archivo de imagen generado (ej. UUID)
    private String frameId;

    // Constructor vacío (necesario para la serialización de Jackson)
    public FrameResponse() {
    }

    public FrameResponse(String frameId) {
        this.frameId = frameId;
    }

    public String getFrameId() {
        return frameId;
    }

    public void setFrameId(String frameId) {
        this.frameId = frameId;
    }

    @Override
    public String toString() {
        return "FrameResponse{" +
                "frameId='" + frameId + '\'' +
                '}';
    }
}
