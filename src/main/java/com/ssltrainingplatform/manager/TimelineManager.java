package com.ssltrainingplatform.manager;

import com.ssltrainingplatform.model.VideoSegment;

import java.util.ArrayList;
import java.util.List;

public class TimelineManager {

    private final List<VideoSegment> segments = new ArrayList<>();
    private double totalTimelineDuration = 0.0;
    private double totalOriginalDuration = 0.0;

    public void reset(double originalDuration) {
        this.totalOriginalDuration = originalDuration;
        this.totalTimelineDuration = originalDuration;
        segments.clear();
        // Segmento inicial completo
        segments.add(new VideoSegment(0, totalTimelineDuration, 0, originalDuration, "#3b82f6", false));
    }

}
