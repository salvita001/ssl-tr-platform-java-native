package com.ssltrainingplatform.dto;

public class FrameAnnotationResponse {
    private String annotationId;
    private String annotationsJson;
    private double duration;

    // Getters
    public String getAnnotationId() { return annotationId; }
    public String getAnnotationsJson() { return annotationsJson; }
    public double getDuration() { return duration; }

    // Setters (necesarios para que GSON pueda rellenar el objeto)
    public void setAnnotationId(String annotationId) { this.annotationId = annotationId; }
    public void setAnnotationsJson(String annotationsJson) { this.annotationsJson = annotationsJson; }
    public void setDuration(double duration) { this.duration = duration; }
}