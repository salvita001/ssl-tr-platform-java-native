package com.ssltrainingplatform.model;

import java.util.ArrayList;
import java.util.List;

public class EditorState {
    // Hacemos los campos 'public final' para acceder fácilmente sin getters/setters excesivos
    public final List<VideoSegment> segmentsSnapshot;
    public final List<DrawingShape> shapesSnapshot;
    public final double durationSnapshot;

    public EditorState(List<VideoSegment> segs, List<DrawingShape> shps, double dur) {
        this.segmentsSnapshot = new ArrayList<>();
        // IMPORTANTE: Usamos el método copy() que creaste para no guardar referencias vivas
        for (VideoSegment s : segs) {
            this.segmentsSnapshot.add(s.copy());
        }

        this.shapesSnapshot = new ArrayList<>();
        for (DrawingShape s : shps) {
            this.shapesSnapshot.add(s.copy());
        }

        this.durationSnapshot = dur;
    }
}