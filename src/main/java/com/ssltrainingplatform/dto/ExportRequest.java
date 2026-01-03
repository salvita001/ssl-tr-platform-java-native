package com.ssltrainingplatform.dto;
import java.util.List;

public class ExportRequest {
    private String videoId;
    private List<ExportItem> items;

    public ExportRequest(String videoId, List<ExportItem> items) {
        this.videoId = videoId;
        this.items = items;
    }
}