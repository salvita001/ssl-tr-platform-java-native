package com.ssltrainingplatform.model;

import javafx.scene.image.Image;

import java.util.UUID;

public class VideoSegment {
    private final String id = UUID.randomUUID().toString();
    private double startTime;       // Inicio en el Timeline
    private double endTime;         // Fin en el Timeline
    private double sourceStartTime; // Inicio en el Video Original
    private double sourceEndTime;   // Fin en el Video Original
    private boolean isSelected;
    private String color;
    private boolean isFreezeFrame;
    private Image thumbnail;

    public VideoSegment(double startTime, double endTime, double sourceStartTime, double sourceEndTime, String color,
                        boolean isFreezeFrame) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.sourceStartTime = sourceStartTime;
        this.sourceEndTime = sourceEndTime;
        this.color = color;
        this.isFreezeFrame = isFreezeFrame;
        this.isSelected = false;
    }

    // Constructor de compatibilidad (para video normal)
    public VideoSegment(double startTime, double endTime, double sourceStartTime, double sourceEndTime, String color) {
        this(startTime, endTime, sourceStartTime, sourceEndTime, color, false);
    }

    public VideoSegment() {
    }

    // Getters y Setters
    public double getStartTime() { return startTime; }
    public void setStartTime(double startTime) { this.startTime = startTime; }

    public double getEndTime() { return endTime; }
    public void setEndTime(double endTime) { this.endTime = endTime; }

    public double getSourceStartTime() { return sourceStartTime; }
    public void setSourceStartTime(double s) { this.sourceStartTime = s; }

    public double getSourceEndTime() { return sourceEndTime; }
    public void setSourceEndTime(double s) { this.sourceEndTime = s; }

    public boolean isFreezeFrame() { return isFreezeFrame; }

    public double getDuration() { return endTime - startTime; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
    public String getColor() { return color; }

    public Image getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(Image thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getId() {
        return id;
    }

    public VideoSegment copy() {
        VideoSegment clone = new VideoSegment(startTime, endTime, sourceStartTime, sourceEndTime, color, isFreezeFrame);
        clone.setThumbnail(this.thumbnail); // La imagen puede ser compartida (referencia)
        // IMPORTANTE: Si tienes un campo 'id' que no se pasa en el constructor, cópialo manualmente:
        // clone.setId(this.id); (Si tu id es final y se genera aleatorio en el constructor, necesitarás un constructor privado o setter para clonarlo)
        return clone;
    }

    @Override
    public String toString() {
        return "VideoSegment{" +
                "id='" + id + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", sourceStartTime=" + sourceStartTime +
                ", sourceEndTime=" + sourceEndTime +
                ", isSelected=" + isSelected +
                ", color='" + color + '\'' +
                ", isFreezeFrame=" + isFreezeFrame +
                ", thumbnail=" + thumbnail +
                '}';
    }
}