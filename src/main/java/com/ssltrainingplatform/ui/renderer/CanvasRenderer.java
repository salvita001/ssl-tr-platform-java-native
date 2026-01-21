package com.ssltrainingplatform.ui.renderer;

import com.ssltrainingplatform.model.DrawingShape;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Affine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CanvasRenderer {

    private final GraphicsContext gcDraw;

    public CanvasRenderer(GraphicsContext gcDraw) {
        this.gcDraw = gcDraw;
    }

    // =========================================================
    //   1. DIBUJO EN PANTALLA (EDITOR)
    // =========================================================
    public void drawShape(Image vidImg, double vidDispW, double vidDispH, double offX, double offY, DrawingShape s,
                          Color c, double size, double x1, double y1, double x2, double y2) {

        // --- COHERENCIA VISUAL ---
        // Calculamos un grosor unificado basado en el slider 'size'.
        // Usamos size / 3.0 como referencia base para líneas.
        double unifiedStroke = Math.max(1.5, size / 3.0);

        gcDraw.setStroke(c);
        gcDraw.setFill(c);
        gcDraw.setLineWidth(unifiedStroke);
        gcDraw.setLineCap(StrokeLineCap.ROUND);

        switch (s.getType()) {
            case "arrow":
                drawProArrow(gcDraw, x1, y1, x2, y2, c, size, unifiedStroke, false);
                break;
            case "arrow-dashed":
                drawProArrow(gcDraw, x1, y1, x2, y2, c, size, unifiedStroke, true);
                break;
            case "arrow-3d":
                if (!s.getPoints().isEmpty()) {
                    double ctrlX = s.getPoints().get(0);
                    double ctrlY = s.getPoints().get(1);
                    drawCurvedArrow(gcDraw, x1, y1, ctrlX, ctrlY, x2, y2, c, size, unifiedStroke);
                } else {
                    double midX = (x1 + x2) / 2;
                    double midY = Math.min(y1, y2) - 50;
                    drawCurvedArrow(gcDraw, x1, y1, midX, midY, x2, y2, c, size, unifiedStroke);
                }
                break;
            case "pen":
                if (s.getPoints() != null && s.getPoints().size() > 2) {
                    gcDraw.setLineWidth(unifiedStroke);
                    gcDraw.setLineCap(StrokeLineCap.ROUND);
                    gcDraw.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

                    gcDraw.beginPath();
                    gcDraw.moveTo(s.getPoints().get(0), s.getPoints().get(1));
                    for (int i = 2; i < s.getPoints().size(); i += 2) {
                        gcDraw.lineTo(s.getPoints().get(i), s.getPoints().get(i+1));
                    }
                    gcDraw.stroke();
                }
                break;

            case "wall":
                gcDraw.setLineWidth(unifiedStroke);
                drawSimpleWall(gcDraw, x1, y1, x2, y2, c, 80.0);
                break;

            case "base":
                gcDraw.setLineWidth(unifiedStroke);
                drawProBase(gcDraw, x1, y1, x2, y2, c, size);
                break;

            case "spotlight":
                drawProSpotlight(gcDraw, x1, y1, x2, y2, c, size);
                break;

            case "polygon":
                if (s.getPoints() != null && s.getPoints().size() >= 4) {
                    gcDraw.setLineWidth(unifiedStroke);
                    drawFilledPolygon(gcDraw, s.getPoints(), c);
                }
                break;
            case "rect-shaded":
                gcDraw.setLineWidth(unifiedStroke);
                drawShadedRect(gcDraw, x1, y1, x2, y2, c);
                break;
            case "zoom-circle":
                gcDraw.setLineWidth(unifiedStroke);
                if (vidImg != null) drawRealZoom(gcDraw, x1, y1, x2, y2, c, vidImg, 1.0,
                        offX, offY, vidDispW, vidDispH, true);
                break;
            case "zoom-rect":
                gcDraw.setLineWidth(unifiedStroke);
                if (vidImg != null) drawRealZoom(gcDraw, x1, y1, x2, y2, c, vidImg, 1.0,
                        offX, offY, vidDispW, vidDispH, false);
                break;
            case "text":
                if (s.getTextValue() != null) {
                    double fontSize = size * 2.5;
                    gcDraw.setFont(Font.font("Arial", FontWeight.BOLD, fontSize));
                    gcDraw.setTextBaseline(javafx.geometry.VPos.TOP);
                    gcDraw.setStroke(Color.BLACK);
                    gcDraw.setLineWidth(Math.max(2.0, unifiedStroke * 0.5));
                    gcDraw.strokeText(s.getTextValue(), x1, y1);
                    gcDraw.setFill(c);
                    gcDraw.fillText(s.getTextValue(), x1, y1);
                }
                break;
            case "tracking":
                // Coordenadas
                double feetX = x2;
                double feetY = y2;
                double headX = x2;
                double headY = y1;

                // --- 1. ELIPSE GIGANTE (40% de la altura) ---
                double currentHeight = (y2 - y1);

                // AUMENTADO: De 0.30 a 0.40
                double radius = currentHeight * 0.40;

                // Ampliamos límites para que no se corte si el jugador es muy grande
                if (radius < 25) radius = 25;
                if (radius > 120) radius = 120; // Límite superior subido

                gcDraw.setLineWidth(3.0);
                gcDraw.setStroke(c);

                // Dibujamos la elipse
                gcDraw.strokeOval(feetX - radius, feetY - (radius * 0.4), radius * 2, radius * 0.8);

                gcDraw.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.35));
                gcDraw.fillOval(feetX - radius, feetY - (radius * 0.4), radius * 2, radius * 0.8);

                // --- 2. TRIÁNGULO MÁS PEQUEÑO ---
                // REDUCIDO: De 0.5 a 0.3 veces el radio.
                // Al ser el radio más grande, necesitamos bajar mucho este factor para que el triángulo encoja.
                double triSize = radius * 0.30;

                // Límite mínimo para que no desaparezca
                if (triSize < 12) triSize = 12;

                double[] xPts = { headX, headX - triSize/2, headX + triSize/2 };
                double[] yPts = { headY - 10, headY - 10 - triSize, headY - 10 - triSize };

                gcDraw.setFill(c);
                gcDraw.fillPolygon(xPts, yPts, 3);
                break;
            case "line_defense":
                // 1. Estilo de Línea Conectora
                gcDraw.setStroke(c);
                gcDraw.setLineWidth(3.0); // Grosor fijo para que se vea bien
                gcDraw.setLineCap(StrokeLineCap.ROUND);
                gcDraw.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

                List<java.awt.Point.Double> points = s.getKeyPoints();
                if (points == null || points.isEmpty()) break;

                // 2. Dibujar la línea que une los pies
                gcDraw.beginPath();
                gcDraw.moveTo(points.get(0).x, points.get(0).y);
                for (int i = 1; i < points.size(); i++) {
                    gcDraw.lineTo(points.get(i).x, points.get(i).y);
                }
                gcDraw.stroke();

                // 3. Recuperar alturas (guardadas en el Controller durante el tracking)
                java.util.Map<Integer, Double> heights = (s.getUserData() instanceof java.util.Map)
                        ? (java.util.Map<Integer, Double>) s.getUserData()
                        : new java.util.HashMap<>();

                double defH = 60.0; // Altura por defecto si no hay tracking

                // 4. Dibujar marcadores por jugador (Círculo Base + Triángulo Cabeza)
                for (int i = 0; i < points.size(); i++) {
                    double fx = points.get(i).x; // Pies X
                    double fy = points.get(i).y; // Pies Y

                    // Altura específica de este jugador (o default)
                    double h = heights.getOrDefault(i, defH);
                    double head_Y = fy - h;

                    // A. CÍRCULO BASE
                    double rx = 15; double ry = 7;
                    gcDraw.setLineWidth(2);
                    gcDraw.setStroke(c);
                    gcDraw.strokeOval(fx - rx, fy - ry, rx * 2, ry * 2);
                    gcDraw.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.3)); // Relleno suave
                    gcDraw.fillOval(fx - rx, fy - ry, rx * 2, ry * 2);

                    // B. TRIÁNGULO CABEZA (Invertido apuntando a la cabeza)
                    double tSize = 8;
                    double tBaseY = head_Y - 5; // Un poco por encima de la cabeza

                    // Coordenadas: Punta abajo, Base arriba
                    double[] xT = { fx, fx - tSize, fx + tSize };
                    double[] yT = { tBaseY, tBaseY - tSize*1.5, tBaseY - tSize*1.5 };

                    gcDraw.setFill(c); // Color sólido del picker
                    gcDraw.fillPolygon(xT, yT, 3);
                }
                break;
            default:
                gcDraw.setLineWidth(unifiedStroke);
                gcDraw.strokeRect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
        }
    }

    // =========================================================
    //   2. DIBUJO PARA EXPORTACIÓN
    // =========================================================

    public void drawShapeOnGC(GraphicsContext gc, DrawingShape s, Image backgroundImage,
                              double x1, double y1, double x2, double y2,
                              double size, double sx, double sy, double offX, double offY,
                              double exportW, double exportH) {

        double scaledSize = size;
        double unifiedStroke = Math.max(1.5, scaledSize / 3.0);

        Color c = Color.web(s.getColor());
        gc.setStroke(c);
        gc.setFill(c);
        gc.setLineWidth(unifiedStroke * sx); // Escalamos también el grosor
        gc.setLineCap(StrokeLineCap.ROUND);

        switch (s.getType()) {
            case "arrow":
                drawProArrow(gc, x1, y1, x2, y2, c, scaledSize * sx, unifiedStroke * sx, false);
                break;
            case "arrow-dashed":
                drawProArrow(gc, x1, y1, x2, y2, c, scaledSize * sx, unifiedStroke * sx, true);
                break;
            case "arrow-3d":
                // CORRECCIÓN: Usar el punto de control editado si existe
                if (!s.getPoints().isEmpty()) {
                    // Recuperamos el punto de control original y lo escalamos al tamaño de exportación
                    double rawCx = s.getPoints().get(0);
                    double rawCy = s.getPoints().get(1);

                    double cx3d = (rawCx - offX) * sx;
                    double cy3d = (rawCy - offY) * sy;

                    drawCurvedArrow(gc, x1, y1, cx3d, cy3d, x2, y2, c, scaledSize * sx, unifiedStroke * sx);
                } else {
                    // Fallback por defecto
                    double cx3d = (x1 + x2) / 2;
                    double cy3d = Math.min(y1, y2) - Math.abs(x2 - x1) * 0.3;
                    drawCurvedArrow(gc, x1, y1, cx3d, cy3d, x2, y2, c, scaledSize * sx, unifiedStroke * sx);
                }
                break;
            case "pen":
                gc.setLineWidth(unifiedStroke * sx);
                if (s.getPoints() != null && s.getPoints().size() > 2) {
                    gc.beginPath();
                    gc.moveTo((s.getPoints().get(0) - offX) * sx, (s.getPoints().get(1) - offY) * sy);
                    for (int i = 2; i < s.getPoints().size(); i += 2) {
                        gc.lineTo((s.getPoints().get(i) - offX) * sx, (s.getPoints().get(i + 1) - offY) * sy);
                    }
                    gc.stroke();
                }
                break;
            case "wall":
                gc.setLineWidth(unifiedStroke * sx);
                drawSimpleWall(gc, x1, y1, x2, y2, c, 80.0 * sy);
                break;
            case "base":
                gc.setLineWidth(unifiedStroke * sx);
                drawProBase(gc, x1, y1, x2, y2, c, scaledSize * sx);
                break;
            case "spotlight":
                drawProSpotlight(gc, x1, y1, x2, y2, c, scaledSize * sx);
                break;
            case "polygon":
                if (s.getPoints() != null && s.getPoints().size() >= 4) {
                    gc.setLineWidth(unifiedStroke * sx);
                    java.util.List<Double> scaledPts = new java.util.ArrayList<>();
                    for (int i = 0; i < s.getPoints().size(); i += 2) {
                        scaledPts.add((s.getPoints().get(i) - offX) * sx);
                        scaledPts.add((s.getPoints().get(i + 1) - offY) * sy);
                    }
                    drawFilledPolygon(gc, scaledPts, c);
                }
                break;
            case "rect-shaded":
                gc.setLineWidth(unifiedStroke * sx);
                drawShadedRect(gc, x1, y1, x2, y2, c);
                break;
            case "text":
                if (s.getTextValue() != null) {
                    // CORRECCIÓN: Escalamos la fuente con 'sx' para que mantenga proporción
                    double fontSize = scaledSize * 2.5 * sx;

                    gc.setFont(Font.font("Arial", FontWeight.BOLD, fontSize));
                    gc.setStroke(Color.BLACK);
                    gc.setLineWidth(Math.max(1.5, (unifiedStroke * 0.5) * sx));
                    gc.strokeText(s.getTextValue(), x1, y1);
                    gc.setFill(c);
                    gc.fillText(s.getTextValue(), x1, y1);
                }
                break;
            case "zoom-circle":
                gc.setLineWidth(unifiedStroke * sx);
                if (backgroundImage != null)
                    drawRealZoom(gc, x1, y1, x2, y2, c, backgroundImage, sx,
                            0, 0, exportW, exportH, true);
                break;
            case "zoom-rect":
                gc.setLineWidth(unifiedStroke * sx);
                if (backgroundImage != null)
                    drawRealZoom(gc, x1, y1, x2, y2, c, backgroundImage, sx,
                            0, 0, exportW, exportH, false);
                break;
            case "tracking":
                // --- 1. CONFIGURACIÓN ---
                double radius = 30.0;
                if (sx != 0) radius *= sx; // Ajuste para exportación si sx (escala) es distinto de 0
                double heightRatio = 0.5; // Círculo achatado (perspectiva)

                // Coordenadas: (x2, y2) son los pies (Kalman), (x1, y1) será la cabeza
                double feetX = x2;
                double feetY = y2;
                double headX = x2; // Centramos el triángulo horizontalmente con los pies
                double headY = y1;

                // --- 2. DIBUJAR CÍRCULO EN LOS PIES ---
                gc.setLineWidth(3.0 * (sx != 0 ? sx : 1.0));
                gc.setStroke(c);
                gc.strokeOval(feetX - radius, feetY - (radius * heightRatio), radius * 2, radius * 2 * heightRatio);

                gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.35));
                gc.fillOval(feetX - radius, feetY - (radius * heightRatio), radius * 2, radius * 2 * heightRatio);

                // --- 3. DIBUJAR TRIÁNGULO EN LA CABEZA ---
                double triSize = 14.0 * (sx != 0 ? sx : 1.0);
                // El triángulo flota un poco por encima de y1
                double triBaseY = headY - 10;

                double[] xPts = { headX, headX - triSize/2, headX + triSize/2 };
                double[] yPts = { triBaseY + triSize, triBaseY, triBaseY }; // Triángulo invertido (apunta abajo)

                // O si prefieres que apunte al jugador (V):
                // double[] yPts = { triBaseY + triSize, triBaseY, triBaseY };
                // Ajuste clásico (triángulo sólido encima):
                double[] yTri = { headY - 5, headY - 20, headY - 20 };

                gc.setFill(c);
                gc.fillPolygon(xPts, yTri, 3);

                // Opcional: Línea fina que une triángulo y círculo (estilo TV pro)
                // gcDraw.setLineWidth(1);
                // gcDraw.strokeLine(feetX, feetY - (radius*heightRatio), headX, headY - 5);
                break;

            case "line_defense":
                gc.setStroke(c);
                gc.setLineWidth(3.0 * sx); // Escalamos grosor
                gc.setLineCap(StrokeLineCap.ROUND);
                gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

                List<java.awt.Point.Double> rawPts = s.getKeyPoints();
                if (rawPts == null || rawPts.isEmpty()) break;

                // Pre-calcular puntos escalados
                double[] sX = new double[rawPts.size()];
                double[] sY = new double[rawPts.size()];

                for(int i=0; i<rawPts.size(); i++) {
                    sX[i] = (rawPts.get(i).x - offX) * sx;
                    sY[i] = (rawPts.get(i).y - offY) * sy;
                }

                // 1. Dibujar línea conectora escalada
                gc.beginPath();
                gc.moveTo(sX[0], sY[0]);
                for (int i = 1; i < rawPts.size(); i++) {
                    gc.lineTo(sX[i], sY[i]);
                }
                gc.stroke();

                // 2. Alturas
                Map<Integer, Double> expHeights = (s.getUserData() instanceof Map)
                        ? (Map<Integer, Double>) s.getUserData()
                        : new HashMap<>();
                double expDefH = 60.0;

                // 3. Marcadores escalados
                for (int i = 0; i < rawPts.size(); i++) {
                    double fx = sX[i];
                    double fy = sY[i];

                    // Importante: Escalar la altura verticalmente
                    double rawH = expHeights.getOrDefault(i, expDefH);
                    double h = rawH * sy;
                    double head_Y = fy - h;

                    // A. CÍRCULO BASE
                    double rx = 15 * sx;
                    double ry = 7 * sy;
                    gc.setLineWidth(2 * sx);
                    gc.setStroke(c);
                    gc.strokeOval(fx - rx, fy - ry, rx * 2, ry * 2);
                    gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.3));
                    gc.fillOval(fx - rx, fy - ry, rx * 2, ry * 2);

                    // B. TRIÁNGULO CABEZA
                    double tSize = 8 * sx;
                    double tBaseY = head_Y - (5 * sy);

                    double[] xT = { fx, fx - tSize, fx + tSize };
                    double[] yT = { tBaseY, tBaseY - tSize*1.5, tBaseY - tSize*1.5 };

                    gc.setFill(c);
                    gc.fillPolygon(xT, yT, 3);
                }
                break;
        }
    }

    // =========================================================
    //   3. PRIMITIVAS DE DIBUJO (ACTUALIZADAS PARA RECIBIR GROSOR)
    // =========================================================

    private void drawProArrow(GraphicsContext gc, double x1, double y1, double x2, double y2, Color color, double size, double strokeWidth, boolean dashed) {
        gc.setLineWidth(strokeWidth);

        if (dashed) {
            gc.setLineDashes(4.0 * strokeWidth, 2.5 * strokeWidth);
        } else {
            gc.setLineDashes(null);
        }

        double angle = Math.atan2(y2 - y1, x2 - x1);
        double headLen = size * 1.5;

        gc.strokeLine(x1, y1, x2 - (headLen * 0.8 * Math.cos(angle)), y2 - (headLen * 0.8 * Math.sin(angle)));
        gc.setLineDashes(null);
        drawArrowHead(gc, x2, y2, angle, size, color);
    }

    private void drawArrowHead(GraphicsContext gc, double x, double y, double angle, double size, Color color) {
        double headLen = size * 1.5;
        double xBase = x - headLen * Math.cos(angle);
        double yBase = y - headLen * Math.sin(angle);
        double x3 = xBase + size * Math.cos(angle - Math.PI/2);
        double y3 = yBase + size * Math.sin(angle - Math.PI/2);
        double x4 = xBase + size * Math.cos(angle + Math.PI/2);
        double y4 = yBase + size * Math.sin(angle + Math.PI/2);
        gc.setFill(color);
        gc.fillPolygon(new double[]{x, x3, x4}, new double[]{y, y3, y4}, 3);
    }

    private void drawCurvedArrow(GraphicsContext gc, double x1, double y1, double cx, double cy, double x2, double y2, Color color, double size, double strokeWidth) {
        gc.setStroke(color);
        gc.setFill(color);
        gc.setLineWidth(strokeWidth);

        double angle = Math.atan2(y2 - cy, x2 - cx);
        double headLen = size * 1.5;
        double lineEndX = x2 - (headLen * 0.5) * Math.cos(angle);
        double lineEndY = y2 - (headLen * 0.5) * Math.sin(angle);
        gc.beginPath();
        gc.moveTo(x1, y1);
        gc.quadraticCurveTo(cx, cy, lineEndX, lineEndY);
        gc.stroke();
        drawArrowHead(gc, x2, y2, angle, size, color);

        double dotSize = strokeWidth * 2.0;
        gc.fillOval(x1 - dotSize/2, y1 - dotSize/2, dotSize, dotSize);
    }

    private void drawSimpleWall(GraphicsContext gc, double x1, double y1, double x2, double y2, Color c, double wallHeight) {
        gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.25));
        gc.fillPolygon(new double[]{x1, x2, x2, x1},
                new double[]{y1, y2, y2 - wallHeight, y1 - wallHeight}, 4);

        gc.setStroke(c);
        // HEREDA EL UNIFICADO

        gc.strokeLine(x1, y1, x2, y2);
        gc.strokeLine(x1, y1, x1, y1 - wallHeight);
        gc.strokeLine(x2, y2, x2, y2 - wallHeight);

        double currentW = gc.getLineWidth();
        gc.setLineWidth(Math.max(1, currentW * 0.5));
        gc.setLineDashes(5);
        gc.strokeLine(x1, y1 - wallHeight/2, x2, y2 - wallHeight/2);
        gc.setLineDashes(null);
        gc.setLineWidth(currentW);
    }

    private void drawFilledPolygon(GraphicsContext gc, List<Double> pts, Color c) {
        int n = pts.size() / 2;
        double[] xs = new double[n]; double[] ys = new double[n];
        for (int i = 0; i < n; i++) { xs[i] = pts.get(i*2); ys[i] = pts.get(i*2+1); }

        gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.4));
        gc.fillPolygon(xs, ys, n);

        gc.setStroke(c);
        // HEREDA EL UNIFICADO
        gc.strokePolygon(xs, ys, n);
    }

    private void drawShadedRect(GraphicsContext gc, double x1, double y1, double x2, double y2, Color c) {
        double l = Math.min(x1, x2);
        double t = Math.min(y1, y2);
        double w = Math.abs(x2-x1);
        double h = Math.abs(y2-y1);

        gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.3));
        gc.fillRect(l, t, w, h);

        gc.setStroke(c);
        // HEREDA EL UNIFICADO
        gc.strokeRect(l, t, w, h);
    }

    private void drawProSpotlight(GraphicsContext gc, double x1, double y1, double x2, double y2, Color c, double size) {
        double radiusOrigin = size * 2.5;
        double radiusTarget = size * 5.0 + Math.abs(x2 - x1) * 0.2;
        double hOrigin = radiusOrigin * 0.4;
        double hTarget = radiusTarget * 0.4;

        gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.15));
        double[] xPoints = { x1 - radiusOrigin, x2 - radiusTarget, x2 + radiusTarget, x1 + radiusOrigin };
        double[] yPoints = { y1, y2, y2, y1 };
        gc.fillPolygon(xPoints, yPoints, 4);

        gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.3));
        gc.fillOval(x2 - radiusTarget, y2 - hTarget, radiusTarget * 2, hTarget * 2);

        gc.setFill(c);
        gc.fillOval(x1 - 4, y1 - 4, 8, 8);
    }

    private void drawProBase(GraphicsContext gc, double x1, double y1, double x2, double y2, Color c, double size) {
        double radius = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.3));
        gc.fillOval(x1 - radius, y1 - radius * 0.4, radius * 2, radius * 0.8);
        gc.setStroke(c);
        // HEREDA UNIFICADO
        gc.strokeOval(x1 - radius, y1 - radius * 0.4, radius * 2, radius * 0.8);
    }

    private void drawRealZoom(GraphicsContext gc, double x1, double y1, double x2, double y2,
                              Color c, Image img, double sx,
                              double destX, double destY, double destW, double destH,
                              boolean circle) {
        double w = Math.abs(x2 - x1);
        double h = Math.abs(y2 - y1);
        double left = Math.min(x1, x2);
        double top = Math.min(y1, y2);

        if (img == null) return;

        gc.save();
        gc.beginPath();
        if (circle) gc.arc(left + w/2, top + h/2, w/2, h/2, 0, 360);
        else gc.rect(left, top, w, h);
        gc.closePath();
        gc.clip();

        double zoomFactor = 2.0;
        double centerX = left + w/2;
        double centerY = top + h/2;

        Affine transform = new Affine();
        transform.appendTranslation(centerX, centerY);
        transform.appendScale(zoomFactor, zoomFactor);
        transform.appendTranslation(-centerX, -centerY);
        gc.setTransform(transform);

        gc.drawImage(img, destX, destY, destW, destH);

        gc.restore();

        gc.setStroke(c);
        gc.setEffect(new DropShadow(10, Color.BLACK));
        if (circle) gc.strokeOval(left, top, w, h);
        else gc.strokeRect(left, top, w, h);
        gc.setEffect(null);
    }

    public void drawSelectionOverlay(DrawingShape s, double size, double x1, double y1, double x2, double y2) {
        gcDraw.setLineWidth(1);
        gcDraw.setStroke(Color.CYAN);
        gcDraw.setLineDashes(5);

        if ("text".equals(s.getType())) {
            double fontSize = size * 2.5;
            javafx.scene.text.Text tempText = new javafx.scene.text.Text(s.getTextValue());
            tempText.setFont(Font.font("Arial", FontWeight.BOLD, fontSize));
            double realWidth = tempText.getLayoutBounds().getWidth();
            double realHeight = tempText.getLayoutBounds().getHeight();
            gcDraw.strokeRect(x1, y1, realWidth, realHeight);

            double handleX = x1 + realWidth;
            double handleY = y1 + realHeight;
            gcDraw.setLineDashes(null);
            gcDraw.setFill(Color.YELLOW);
            gcDraw.setStroke(Color.BLACK);
            gcDraw.fillRect(handleX - 6, handleY - 6, 12, 12);
            gcDraw.strokeRect(handleX - 6, handleY - 6, 12, 12);

        } else if ("polygon".equals(s.getType())) {
            List<Double> pts = s.getPoints();
            for(int i=0; i<pts.size(); i+=2) {
                double px = pts.get(i); double py = pts.get(i+1);
                gcDraw.strokeRect(px-5, py-5, 10, 10);
            }
        } else {
            gcDraw.setLineDashes(null);
            gcDraw.setFill(Color.CYAN);
            gcDraw.fillOval(s.getStartX() - 5, s.getStartY() - 5, 10, 10);
            gcDraw.fillOval(s.getEndX() - 5, s.getEndY() - 5, 10, 10);

            if ("arrow-3d".equals(s.getType()) && !s.getPoints().isEmpty()) {
                double cx = s.getPoints().get(0);
                double cy = s.getPoints().get(1);
                gcDraw.setStroke(Color.GRAY);
                gcDraw.setLineWidth(1);
                gcDraw.setLineDashes(3);
                gcDraw.strokeLine(x1, y1, cx, cy);
                gcDraw.strokeLine(x2, y2, cx, cy);
                gcDraw.setLineDashes(null);
                gcDraw.setFill(Color.YELLOW);
                gcDraw.setStroke(Color.BLACK);
                gcDraw.fillOval(cx - 6, cy - 6, 12, 12);
                gcDraw.strokeOval(cx - 6, cy - 6, 12, 12);
            }
        }
        gcDraw.setLineDashes(null);
    }
}