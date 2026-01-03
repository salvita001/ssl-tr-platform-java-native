package com.ssltrainingplatform.dto;

public class FrameRequest {

    private String videoId;
    private double timestamp;

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "FrameRequest{" +
                "videoId='" + videoId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
