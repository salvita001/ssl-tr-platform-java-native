package com.ssltrainingplatform.manager;

import com.ssltrainingplatform.model.VideoSegment;
import javafx.scene.image.Image;
import java.util.ArrayList;
import java.util.List;

public class TimelineManager {

    // 1. AHORA LA LISTA VIVE AQUÍ
    private List<VideoSegment> segments = new ArrayList<>();

    // 2. LA DURACIÓN TOTAL TAMBIÉN VIVE AQUÍ
    private double totalTimelineDuration = 0.0;
    private double totalOriginalDuration = 0.0;

    // --- INICIALIZACIÓN ---
    public void reset(double originalDuration) {
        this.totalOriginalDuration = originalDuration;
        this.totalTimelineDuration = originalDuration;
        segments.clear();
        segments.add(new VideoSegment(0, totalTimelineDuration, 0, originalDuration,
                "#3b82f6", false));
    }

    // --- ACCESO A DATOS (GETTERS) ---
    public List<VideoSegment> getSegments() {
        return segments;
    }

    public double getTotalDuration() {
        return totalTimelineDuration;
    }

    // Método vital para Undo/Redo: Restaurar una copia de la lista
    public void restoreState(List<VideoSegment> snapshot, double durationSnapshot) {
        this.segments = new ArrayList<>(snapshot); // Importante: crear nueva lista
        this.totalTimelineDuration = durationSnapshot;
    }

    // --- LÓGICA DE NEGOCIO (LO QUE ANTES HACÍA EL CONTROLLER) ---

    public VideoSegment getSegmentAt(double time) {
        double epsilon = 0.05;
        for (VideoSegment seg : segments) {
            if (time >= seg.getStartTime() - epsilon && time < seg.getEndTime() - epsilon) {
                return seg;
            }
        }
        return null;
    }

    public boolean cutVideo(double currentTime, Image currentThumbnail) {
        VideoSegment activeSegment = getSegmentAt(currentTime);
        if (activeSegment == null || activeSegment.isFreezeFrame()) return false;

        double offset = currentTime - activeSegment.getStartTime();
        if (offset < 0.5 || (activeSegment.getEndTime() - currentTime) < 0.5) return false;

        // Lógica de corte
        double oldSourceEnd = activeSegment.getSourceEndTime();
        double oldTimelineEnd = activeSegment.getEndTime();
        double cutPointSource = activeSegment.getSourceStartTime() + offset;

        activeSegment.setEndTime(currentTime);
        activeSegment.setSourceEndTime(cutPointSource);

        double gap = 0.05;
        double newStartTime = currentTime + gap;
        double newDuration = oldTimelineEnd - currentTime;

        String newColor = activeSegment.getColor().equals("#3b82f6") ? "#10b981" : "#3b82f6";
        VideoSegment newSegment = new VideoSegment(
                newStartTime, newStartTime + newDuration,
                cutPointSource, oldSourceEnd,
                newColor, false
        );
        if (currentThumbnail != null) newSegment.setThumbnail(currentThumbnail);

        int idx = segments.indexOf(activeSegment);
        segments.add(idx + 1, newSegment);

        shiftSegments(idx + 2, gap);
        totalTimelineDuration += gap;

        return true;
    }

    // En TimelineManager.java

    public boolean deleteSegment(VideoSegment seg) {
        // Regla de seguridad: No dejar el timeline vacío
        if (seg == null || segments.size() <= 1) return false;

        int idx = segments.indexOf(seg);
        if (idx == -1) return false; // El segmento no existe en la lista

        double delDur = seg.getDuration();

        // 1. Borrar
        segments.remove(seg);

        // 2. Desplazar el resto hacia la izquierda (cantidad negativa)
        shiftSegments(idx, -delDur);

        // 3. Actualizar total
        totalTimelineDuration -= delDur;

        return true; // Confirmamos que se borró
    }

    public void recalculateSegmentTimes() {
        if (segments.isEmpty()) return;
        double currentPos = 0.0;
        double editGap = 0.05;
        for (VideoSegment seg : segments) {
            double duration = seg.getDuration();
            seg.setStartTime(currentPos);
            seg.setEndTime(currentPos + duration);
            currentPos = seg.getEndTime() + editGap;
        }
        totalTimelineDuration = currentPos - editGap;
    }

    // Método privado para mover bloques de tiempo (Helper)
    private void shiftSegments(int startIndex, double amount) {
        for (int i = startIndex; i < segments.size(); i++) {
            VideoSegment s = segments.get(i);
            s.setStartTime(s.getStartTime() + amount);
            s.setEndTime(s.getEndTime() + amount);
        }
    }

    public void moveSegment(VideoSegment seg, int newIndex) {
        // 1. Lo quitamos de donde esté
        segments.remove(seg);

        // 2. Lo insertamos en la nueva posición (con seguridad de índices)
        if (newIndex >= 0 && newIndex <= segments.size()) {
            segments.add(newIndex, seg);
        } else {
            segments.add(seg); // Si el índice es raro, lo ponemos al final
        }

        // 3. ¡Importante! Recalcular tiempos automáticamente
        recalculateSegmentTimes();
    }

    public VideoSegment getNextSegment(VideoSegment currentSeg, double currentTime) {
        int nextIndex = -1;

        if (currentSeg != null) {
            // Caso A: Sabemos cuál es el actual, queremos el siguiente (+1)
            nextIndex = segments.indexOf(currentSeg) + 1;
        } else {
            // Caso B: Estamos en un hueco vacío, buscamos el primer clip que empiece después
            for (int i = 0; i < segments.size(); i++) {
                if (segments.get(i).getStartTime() > currentTime) {
                    nextIndex = i;
                    break;
                }
            }
            // Si no encontramos ninguno, es que estamos al final
            if (nextIndex == -1) nextIndex = segments.size();
        }

        // Comprobamos si el índice es válido
        if (nextIndex >= 0 && nextIndex < segments.size()) {
            return segments.get(nextIndex);
        }

        return null; // Significa que ya no hay más videos (fin del timeline)
    }

    // Pégalo dentro de TimelineManager.java

    public void insertFreezeFrame(double currentTime, double duration, Image thumbnail) {
        // 1. Buscamos el segmento bajo el cabezal
        VideoSegment activeSeg = getSegmentAt(currentTime);
        if (activeSeg == null) return; // Si estamos en un hueco, no hacemos nada

        double gap = 0.05; // Hueco visual entre clips

        // 2. Calculamos los puntos de corte exactos
        double splitTimeTimeline = currentTime;

        // Calculamos en qué punto del video original estamos
        double offset = currentTime - activeSeg.getStartTime();
        double splitTimeSource = activeSeg.getSourceStartTime() + offset;

        double originalEndTimeTimeline = activeSeg.getEndTime();
        double originalEndTimeSource = activeSeg.getSourceEndTime();

        // 3. MODIFICAR EL SEGMENTO ACTUAL (Lado Izquierdo)
        // Ahora termina justo donde está el cabezal
        activeSeg.setEndTime(splitTimeTimeline);
        activeSeg.setSourceEndTime(splitTimeSource);

        // 4. CREAR EL SEGMENTO CONGELADO (Centro)
        VideoSegment freezeSeg = new VideoSegment(
                splitTimeTimeline + gap,               // Empieza después del hueco
                splitTimeTimeline + gap + duration,    // Dura X segundos
                splitTimeSource, splitTimeSource,      // Source Start == End (es una foto)
                "#00bcd4", true                        // Color Cyan, flag de Freeze activado
        );

        // Guardamos la miniatura (la pasa el controller)
        if (thumbnail != null) freezeSeg.setThumbnail(thumbnail);

        // 5. CREAR EL RESTO DEL VIDEO (Lado Derecho)
        // Alternamos color para que se vea que es un corte nuevo
        String colorRight = activeSeg.getColor().equals("#3b82f6") ? "#10b981" : "#3b82f6";

        VideoSegment rightSeg = new VideoSegment(
                freezeSeg.getEndTime() + gap,                      // Empieza después del freeze
                originalEndTimeTimeline + duration + (2 * gap),    // El final se desplaza
                splitTimeSource, originalEndTimeSource,            // Continúa el video original
                colorRight, false
        );

        // 6. INSERTAR LOS NUEVOS SEGMENTOS EN LA LISTA
        int idx = segments.indexOf(activeSeg);
        segments.add(idx + 1, freezeSeg);
        segments.add(idx + 2, rightSeg);

        // 7. EMPUJAR EL RESTO DE SEGMENTOS
        // Todo lo que había después debe moverse a la derecha
        double totalShift = duration + (2 * gap);
        shiftSegments(idx + 3, totalShift);

        // 8. ACTUALIZAR DURACIÓN TOTAL
        totalTimelineDuration += totalShift;
    }

    // En TimelineManager.java

    public boolean modifySegmentDuration(VideoSegment seg, double newDuration) {
        if (seg == null) return false;

        double oldDuration = seg.getDuration();
        double difference = newDuration - oldDuration;

        // 1. Aplicar la nueva duración
        seg.setEndTime(seg.getStartTime() + newDuration);

        // 2. Empujar todos los clips siguientes
        int index = segments.indexOf(seg);
        if (index != -1 && index < segments.size() - 1) {
            shiftSegments(index + 1, difference);
        }

        // 3. Actualizar duración total
        totalTimelineDuration += difference;

        return true;
    }

    public double getTotalOriginalDuration() {
        return totalOriginalDuration;
    }
}