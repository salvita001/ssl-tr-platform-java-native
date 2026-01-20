package com.ssltrainingplatform.model;

import com.ssltrainingplatform.util.SimpleKalmanTracker;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DrawingShape {
    private String id;

    // --- CLAVE PARA TU PROBLEMA ---
    private String clipId; // Identificador del segmento (Frame) al que pertenece
    // ------------------------------

    private String type;

    // Coordenadas principales (Inicio/Fin)
    private double startX;
    private double startY;
    private double endX;
    private double endY;

    // Estilos
    private String color;
    private double strokeWidth = 5.0;

    // Contenido específico
    private String textValue; // Para herramienta Texto
    private List<Double> points = new ArrayList<>(); // Para Polígono, Muro, Lápiz y Curva

    private Object userData;

    // --- NUEVO: ESTRUCTURAS PARA HERRAMIENTAS MULTI-PUNTO (Como Line Defense) ---

    // Lista de puntos clave. Para una línea defensiva de 4 jugadores, tendrá 4 puntos.
    private List<Point.Double> keyPoints = new ArrayList<>();

    // Lista de trackers, uno para cada punto clave.
    // NO SE GUARDA EN JSON (es "transient"). Se regenera en tiempo de ejecución.
    private transient List<SimpleKalmanTracker> internalTrackers = new ArrayList<>();

    // --- MÉTODOS NUEVOS ---

    public void addKeyPoint(double x, double y) {
        this.keyPoints.add(new Point.Double(x, y));
        // Al añadir un punto, creamos su tracker correspondiente
        this.internalTrackers.add(new SimpleKalmanTracker(x, y));
    }

    public List<Point.Double> getKeyPoints() {
        return keyPoints;
    }

    // Método vital para el tracking: devuelve los trackers internos
    public List<SimpleKalmanTracker> getInternalTrackers() {
        // Seguridad: Si cargamos de JSON, la lista puede ser null. Inicializarla.
        if (internalTrackers == null) {
            internalTrackers = new ArrayList<>();
            // Si hay puntos pero no trackers, los creamos (recuperación tras cargar)
            for (Point.Double p : keyPoints) {
                internalTrackers.add(new SimpleKalmanTracker(p.x, p.y));
            }
        }
        return internalTrackers;
    }

    // Actualiza la posición de un punto específico después del tracking
    public void updateKeyPointPosition(int index, double newX, double newY) {
        if (index >= 0 && index < keyPoints.size()) {
            keyPoints.get(index).setLocation(newX, newY);
        }
    }

    public DrawingShape(String id, String type, double startX, double startY, String color) {
        this.id = id;
        this.type = type;
        this.startX = startX;
        this.startY = startY;
        this.endX = startX;
        this.endY = startY;
        this.color = color;
    }

    // --- MÉTODOS DE LÓGICA ---

    public void addPoint(double x, double y) {
        this.points.add(x);
        this.points.add(y);
    }

    // Mover la forma (arrastrar)
    public void move(double dx, double dy) {
        this.startX += dx;
        this.startY += dy;
        this.endX += dx;
        this.endY += dy;

        // Si tiene lista de puntos (Polígono, Curva, Lápiz), movemos todos
        if (points != null && !points.isEmpty()) {
            for (int i = 0; i < points.size(); i += 2) {
                points.set(i, points.get(i) + dx);
                points.set(i + 1, points.get(i + 1) + dy);
            }
        }
    }

    // Detectar si el ratón ha hecho clic encima
    public boolean isHit(double x, double y) {
        double padding = 20.0; // Margen para facilitar el clic

        // 1. POLÍGONO, LÁPIZ Y CURVA (Usan lista de puntos)
        if (("polygon".equals(type) || "pen".equals(type) || "curve".equals(type)) && points != null && !points.isEmpty()) {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

            // Incluir Start/End en la caja envolvente por si acaso
            minX = Math.min(minX, Math.min(startX, endX));
            maxX = Math.max(maxX, Math.max(startX, endX));
            minY = Math.min(minY, Math.min(startY, endY));
            maxY = Math.max(maxY, Math.max(startY, endY));

            // Revisar todos los puntos internos
            for (int i = 0; i < points.size(); i+=2) {
                double px = points.get(i);
                double py = points.get(i+1);
                if (px < minX) minX = px;
                if (px > maxX) maxX = px;
                if (py < minY) minY = py;
                if (py > maxY) maxY = py;
            }
            return x >= minX - padding && x <= maxX + padding &&
                    y >= minY - padding && y <= maxY + padding;
        }

        // 2. MURO (Rectángulo que crece hacia arriba)
        if ("wall".equals(type)) {
            double wallHeight = 80.0; // Debe coincidir con tu lógica de dibujo
            double left = Math.min(startX, endX) - padding;
            double right = Math.max(startX, endX) + padding;
            double top = Math.min(startY, endY) - wallHeight - padding; // Tope del muro
            double bottom = Math.max(startY, endY) + padding; // Base del muro

            return x >= left && x <= right && y >= top && y <= bottom;
        }

        // 3. TEXTO
        if ("text".equals(type) && textValue != null) {
            double estimatedW = textValue.length() * strokeWidth * 1.5; // Estimación simple
            double estimatedH = strokeWidth * 3.0;
            // Asumiendo alineación Top-Left
            return x >= startX - padding && x <= startX + estimatedW + padding &&
                    y >= startY - padding && y <= startY + estimatedH + padding;
        }

        // 4. RESTO (Flechas, Rectángulos, Zoom, etc.)
        double left = Math.min(startX, endX) - padding;
        double right = Math.max(startX, endX) + padding;
        double top = Math.min(startY, endY) - padding;
        double bottom = Math.max(startY, endY) + padding;

        return x >= left && x <= right && y >= top && y <= bottom;
    }

    public DrawingShape copy() {
        DrawingShape replica = new DrawingShape(this.id, this.type, this.startX, this.startY, this.color);
        replica.setEndX(this.endX);
        replica.setEndY(this.endY);
        replica.setStrokeWidth(this.strokeWidth);
        replica.setTextValue(this.textValue);
        replica.setClipId(this.clipId);

        // Clonar la lista de puntos (vital para polígonos y flechas 3D)
        if (this.points != null) {
            replica.setPoints(new ArrayList<>(this.points));
        }
        return replica;
    }

    // --- GETTERS Y SETTERS ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClipId() { return clipId; }
    public void setClipId(String clipId) { this.clipId = clipId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getStartX() { return startX; }
    public void setStartX(double startX) { this.startX = startX; }

    public double getStartY() { return startY; }
    public void setStartY(double startY) { this.startY = startY; }

    public double getEndX() { return endX; }
    public void setEndX(double endX) { this.endX = endX; }

    public double getEndY() { return endY; }
    public void setEndY(double endY) { this.endY = endY; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public double getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(double strokeWidth) { this.strokeWidth = strokeWidth; }

    public String getTextValue() { return textValue; }
    public void setTextValue(String textValue) { this.textValue = textValue; }

    public List<Double> getPoints() { return points; }
    public void setPoints(List<Double> points) { this.points = points; }

    public Object getUserData() {
        return userData;
    }

    public void setUserData(Object userData) {
        this.userData = userData;
    }
}