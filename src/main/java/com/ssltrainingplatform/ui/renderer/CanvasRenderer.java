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

import java.util.List;

public class CanvasRenderer {

    private final GraphicsContext gcDraw;

    public CanvasRenderer(GraphicsContext gcDraw) {
        this.gcDraw = gcDraw;
    }

    // =========================================================
    //   1. DIBUJO EN PANTALLA (EDITOR)
    // =========================================================
    public void drawShape(Image vidImg, double vidDispW, double vidDispH, double offX, double offY, DrawingShape s, Color c, double size, double x1, double y1, double x2, double y2) {

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
                double triSize = 14;
                gcDraw.setFill(c);
                double[] xPts = {x1, x1 - triSize/2, x1 + triSize/2 };
                double[] yPts = { y1 - 5, y1 - 20, y1 - 20 };
                gcDraw.fillPolygon(xPts, yPts, 3);

                double radius = 25.0;
                gcDraw.setStroke(c);
                gcDraw.setLineWidth(2);
                gcDraw.strokeOval(x2 - radius, y2 - (radius * 0.4), radius * 2, radius * 0.8);
                gcDraw.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.25));
                gcDraw.fillOval(x2 - radius, y2 - (radius * 0.4), radius * 2, radius * 0.8);
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
        gc.setLineWidth(unifiedStroke);
        gc.setLineCap(StrokeLineCap.ROUND);

        switch (s.getType()) {
            case "arrow":
                drawProArrow(gc, x1, y1, x2, y2, c, scaledSize, unifiedStroke, false);
                break;
            case "arrow-dashed":
                drawProArrow(gc, x1, y1, x2, y2, c, scaledSize, unifiedStroke, true);
                break;
            case "arrow-3d":
                double cx3d = (x1 + x2) / 2;
                double cy3d = Math.min(y1, y2) - Math.abs(x2 - x1) * 0.3;
                drawCurvedArrow(gc, x1, y1, cx3d, cy3d, x2, y2, c, scaledSize, unifiedStroke);
                break;
            case "pen":
                gc.setLineWidth(unifiedStroke);
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
                gc.setLineWidth(unifiedStroke);
                drawSimpleWall(gc, x1, y1, x2, y2, c, 80.0 * sy);
                break;
            case "base":
                gc.setLineWidth(unifiedStroke);
                drawProBase(gc, x1, y1, x2, y2, c, scaledSize);
                break;
            case "spotlight":
                drawProSpotlight(gc, x1, y1, x2, y2, c, scaledSize);
                break;
            case "polygon":
                if (s.getPoints() != null && s.getPoints().size() >= 4) {
                    gc.setLineWidth(unifiedStroke);
                    java.util.List<Double> scaledPts = new java.util.ArrayList<>();
                    for (int i = 0; i < s.getPoints().size(); i += 2) {
                        scaledPts.add((s.getPoints().get(i) - offX) * sx);
                        scaledPts.add((s.getPoints().get(i + 1) - offY) * sy);
                    }
                    drawFilledPolygon(gc, scaledPts, c);
                }
                break;
            case "rect-shaded":
                gc.setLineWidth(unifiedStroke);
                drawShadedRect(gc, x1, y1, x2, y2, c);
                break;
            case "text":
                if (s.getTextValue() != null) {
                    double fontSize = scaledSize * 2.5;
                    gc.setFont(Font.font("Arial", FontWeight.BOLD, fontSize));
                    gc.setStroke(Color.BLACK);
                    gc.setLineWidth(Math.max(1.5, unifiedStroke * 0.5));
                    gc.strokeText(s.getTextValue(), x1, y1);
                    gc.setFill(c);
                    gc.fillText(s.getTextValue(), x1, y1);
                }
                break;
            case "zoom-circle":
                gc.setLineWidth(unifiedStroke);
                if (backgroundImage != null)
                    drawRealZoom(gc, x1, y1, x2, y2, c, backgroundImage, sx,
                            0, 0, exportW, exportH, true);
                break;
            case "zoom-rect":
                gc.setLineWidth(unifiedStroke);
                if (backgroundImage != null)
                    drawRealZoom(gc, x1, y1, x2, y2, c, backgroundImage, sx,
                            0, 0, exportW, exportH, false);
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