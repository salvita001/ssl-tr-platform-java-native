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

    public void drawShape(Image vidImg, double vidDispW, double vidDispH, double offX, double offY, DrawingShape s, Color c, double size, double x1, double y1, double x2, double y2) {
        // DIBUJAR LA FORMA
        switch (s.getType()) {
            case "arrow":
                drawProArrow(x1, y1, x2, y2, c, size, false);
                break;
            case "arrow-dashed":
                drawProArrow(x1, y1, x2, y2, c, size, true);
                break;
            case "arrow-3d":
                if (!s.getPoints().isEmpty()) {
                    double ctrlX = s.getPoints().get(0);
                    double ctrlY = s.getPoints().get(1);
                    drawCurvedArrow(x1, y1, ctrlX, ctrlY, x2, y2, c, size);
                } else {
                    // Fallback por si acaso
                    double midX = (x1 + x2) / 2;
                    double midY = Math.min(y1, y2) - 50;
                    drawCurvedArrow(x1, y1, midX, midY, x2, y2, c, size);
                }
                break;
            case "pen":
                // Dibujamos el lápiz conectando todos los puntos
                if (s.getPoints() != null && s.getPoints().size() > 2) {
                    gcDraw.setStroke(c);
                    gcDraw.setLineWidth(size);
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
                drawSimpleWall(x1, y1, x2, y2, c);
                break;

            case "base":
                drawProBase(x1, y1, x2, y2, c, size);
                break;

            case "spotlight":
                drawProSpotlight(x1, y1, x2, y2, c, size);
                break;

            case "polygon":
                if (s.getPoints() != null && s.getPoints().size() >= 4) drawFilledPolygon(s.getPoints(), c);
                break;
            case "rect-shaded":
                drawShadedRect(x1, y1, x2, y2, c);
                break;
            case "zoom-circle":
                if (vidImg != null) drawRealZoom(x1, y1, x2, y2, c, vidImg, offX, offY, vidDispW, vidDispH, true);
                break;
            case "zoom-rect":
                if (vidImg != null) drawRealZoom(x1, y1, x2, y2, c, vidImg, offX, offY, vidDispW, vidDispH, false);
                break;
            case "text":
                if (s.getTextValue() != null) {
                    // Aumentamos el tamaño base para que sea legible
                    double fontSize = size * 2.5;
                    gcDraw.setFont(Font.font("Arial", FontWeight.BOLD, fontSize));

                    // IMPORTANTE: Definir el origen arriba a la izquierda
                    gcDraw.setTextBaseline(javafx.geometry.VPos.TOP);

                    // Sombra negra para legibilidad
                    gcDraw.setStroke(Color.BLACK);
                    gcDraw.setLineWidth(2.0); // Borde un poco más grueso
                    gcDraw.strokeText(s.getTextValue(), x1, y1);

                    // Relleno de color
                    gcDraw.setFill(c);
                    gcDraw.fillText(s.getTextValue(), x1, y1);
                }
                break;
            case "tracking":
                // 1. Triángulo en la cabeza (StartX/Y)
                double triSize = 14;
                gcDraw.setFill(c);
                double[] xPts = {x1, x1 - triSize/2, x1 + triSize/2 };
                double[] yPts = { y1 - 5, y1 - 20, y1 - 20 }; // Punta hacia abajo
                gcDraw.fillPolygon(xPts, yPts, 3);

                // 2. Base circular en los pies (EndX/Y)
                double radius = 25.0;
                gcDraw.setStroke(c); gcDraw.setLineWidth(2);
                gcDraw.strokeOval(x2 - radius, y2 - (radius * 0.4), radius * 2, radius * 0.8);
                gcDraw.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.25));
                gcDraw.fillOval(x2 - radius, y2 - (radius * 0.4), radius * 2, radius * 0.8);
                break;
            default:
                gcDraw.setStroke(c); gcDraw.setLineWidth(3);
                gcDraw.strokeRect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
        }
    }

    public void drawSelectionOverlay(DrawingShape s, double size, double x1, double y1, double x2, double y2) {

            gcDraw.setLineWidth(1);
            gcDraw.setStroke(Color.CYAN);
            gcDraw.setLineDashes(5);

            if ("text".equals(s.getType())) {
                // CÁLCULO PRECISO DEL ANCHO DEL TEXTO
                double fontSize = size * 2.5; // Factor de escala para que no se vea enano
                javafx.scene.text.Text tempText = new javafx.scene.text.Text(s.getTextValue());
                tempText.setFont(Font.font("Arial", FontWeight.BOLD, fontSize));
                double realWidth = tempText.getLayoutBounds().getWidth();
                double realHeight = tempText.getLayoutBounds().getHeight();

                // Caja ajustada al texto
                gcDraw.strokeRect(x1 - 5, y1 - 5, realWidth + 10, realHeight + 10);

                // Solo dibujamos un punto de control (esquina superior izquierda)
                gcDraw.setLineDashes(null);
                gcDraw.setFill(Color.CYAN);
                gcDraw.fillOval(x1 - 5, y1 - 5, 10, 10);
            } else if ("polygon".equals(s.getType())) {
                List<Double> pts = s.getPoints();
                for(int i=0; i<pts.size(); i+=2) {
                    double px = pts.get(i); double py = pts.get(i+1);
                    gcDraw.strokeRect(px-5, py-5, 10, 10); // Cuadraditos en vértices
                }
            } else {
                gcDraw.setLineDashes(null);
                gcDraw.setFill(Color.CYAN);
                gcDraw.fillOval(s.getStartX() - 5, s.getStartY() - 5, 10, 10);
                gcDraw.fillOval(s.getEndX() - 5, s.getEndY() - 5, 10, 10);

                // ✅ NUEVO: DIBUJAR PUNTO DE CONTROL PARA FLECHA 3D
                if ("arrow-3d".equals(s.getType()) && !s.getPoints().isEmpty()) {
                    double cx = s.getPoints().get(0);
                    double cy = s.getPoints().get(1);

                    // Líneas guía punteadas
                    gcDraw.setStroke(Color.GRAY);
                    gcDraw.setLineWidth(1);
                    gcDraw.setLineDashes(3);
                    gcDraw.strokeLine(x1, y1, cx, cy);
                    gcDraw.strokeLine(x2, y2, cx, cy);

                    // Círculo amarillo de control (Radio)
                    gcDraw.setLineDashes(null);
                    gcDraw.setFill(Color.YELLOW);
                    gcDraw.setStroke(Color.BLACK);
                    gcDraw.fillOval(cx - 6, cy - 6, 12, 12);
                    gcDraw.strokeOval(cx - 6, cy - 6, 12, 12);
                }
            }
            gcDraw.setLineDashes(null);
    }

    // --- MÉTODOS DE DIBUJO ---

    private void drawSimpleWall(double x1, double y1, double x2, double y2, Color c) {
        double wallHeight = 80.0; // Altura del muro

        LinearGradient gradient =
                new LinearGradient(0, y1, 0, y1 - wallHeight, false, CycleMethod.NO_CYCLE,
                new Stop(0, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.4)), // Base más opaca
                new Stop(1, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.05)) // Tope casi invisible
        );

        // 1. Cara frontal semitransparente
        gcDraw.setFill(gradient);
        gcDraw.fillPolygon(
                new double[]{x1, x2, x2, x1},
                new double[]{y1, y2, y2 - wallHeight, y1 - wallHeight},
                4
        );

        // 2. Bordes sólidos
        gcDraw.setStroke(c);
        gcDraw.setLineWidth(2.0);

        // Base y Tope
        gcDraw.strokeLine(x1, y1, x2, y2); // Línea base (suelo)
        gcDraw.strokeLine(x1, y1, x1, y1 - wallHeight); // Poste izq
        gcDraw.strokeLine(x2, y2, x2, y2 - wallHeight); // Poste der

        // Detalle: Línea media para efecto "valla" o "red"
        gcDraw.setLineWidth(1);
        gcDraw.setLineDashes(5); // Punteado para la red
        gcDraw.strokeLine(x1, y1 - wallHeight/2, x2, y2 - wallHeight/2);
        gcDraw.setLineDashes(null);
    }

    private void drawFilledPolygon(List<Double> pts, Color c) {
        int n = pts.size() / 2;
        double[] xPoints = new double[n];
        double[] yPoints = new double[n];
        for (int i = 0; i < n; i++) {
            xPoints[i] = pts.get(i*2);
            yPoints[i] = pts.get(i*2+1);
        }
        gcDraw.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.4)); // Transparente
        gcDraw.fillPolygon(xPoints, yPoints, n);
        gcDraw.setStroke(c);
        gcDraw.setLineWidth(2);
        gcDraw.strokePolygon(xPoints, yPoints, n);
    }

    private void drawShadedRect(double x1, double y1, double x2, double y2, Color c) {
        double left = Math.min(x1, x2);
        double top = Math.min(y1, y2);
        double w = Math.abs(x2 - x1);
        double h = Math.abs(y2 - y1);

        gcDraw.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.3));
        gcDraw.fillRect(left, top, w, h);
        gcDraw.setStroke(c);
        gcDraw.setLineWidth(2);
        gcDraw.strokeRect(left, top, w, h);
    }

    private void drawRealZoom(double x1, double y1, double x2, double y2, Color c, javafx.scene.image.Image img, double imgX, double imgY, double imgW, double imgH, boolean circle) {
        double w = Math.abs(x2 - x1);
        double h = Math.abs(y2 - y1);
        double left = Math.min(x1, x2);
        double top = Math.min(y1, y2);

        gcDraw.save();
        gcDraw.beginPath();
        if (circle) gcDraw.arc(left + w/2, top + h/2, w/2, h/2, 0, 360);
        else gcDraw.rect(left, top, w, h);
        gcDraw.closePath();
        gcDraw.clip();

        double zoomFactor = 2.0;
        double centerX = left + w/2; double centerY = top + h/2;
        Affine transform = new Affine();
        transform.appendTranslation(centerX, centerY);
        transform.appendScale(zoomFactor, zoomFactor);
        transform.appendTranslation(-centerX, -centerY);
        gcDraw.setTransform(transform);
        gcDraw.drawImage(img, imgX, imgY, imgW, imgH);
        gcDraw.restore();

        gcDraw.setLineWidth(1.0);
        gcDraw.setStroke(c);
        gcDraw.setEffect(new DropShadow(10, Color.BLACK));
        if (circle) {
            gcDraw.strokeOval(left, top, w, h);
        } else {
            gcDraw.strokeRect(left, top, w, h);
        }
        gcDraw.setEffect(null);
    }

    private void drawProSpotlight(double x1, double y1, double x2, double y2, Color c, double size) {
        double radiusTop = size * 1.5;
        double rxTop = radiusTop;
        double ryTop = radiusTop * 0.3;
        double radiusBottom = size * 3.0 + Math.abs(x2-x1) * 0.2;
        double rxBot = radiusBottom;
        double ryBot = radiusBottom * 0.3;
        double xTopL = x1 - rxTop;
        double xTopR = x1 + rxTop;
        double xBotL = x2 - rxBot;
        double xBotR = x2 + rxBot;
        LinearGradient grad = new LinearGradient(x1, y1, x2, y2, false, CycleMethod.NO_CYCLE,
                new Stop(0, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.3)),
                new Stop(1, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.1)));
        gcDraw.setFill(grad);
        gcDraw.fillPolygon(new double[]{xTopL, xBotL, xBotR, xTopR}, new double[]{y1, y2, y2, y1}, 4);
        gcDraw.setStroke(c);
        gcDraw.setLineWidth(2);
        gcDraw.setLineDashes(null);
        gcDraw.strokeOval(x1 - rxTop, y1 - ryTop, rxTop * 2, ryTop * 2);
        gcDraw.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.4));
        gcDraw.fillOval(x2 - rxBot, y2 - ryBot, rxBot * 2, ryBot * 2);
        gcDraw.setStroke(c.deriveColor(0, 1, 1, 0.5));
        gcDraw.strokeOval(x2 - rxBot, y2 - ryBot, rxBot * 2, ryBot * 2);
    }

    private void drawProArrow(double x1, double y1, double x2, double y2, Color color, double size, boolean dashed) {
        gcDraw.setStroke(color);
        gcDraw.setFill(color);
        gcDraw.setLineWidth(size / 3.0);
        if (dashed) gcDraw.setLineDashes(10, 10);
        else gcDraw.setLineDashes(null);
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double headLen = size * 1.5;
        double lineEndX = x2 - headLen * Math.cos(angle);
        double lineEndY = y2 - headLen * Math.sin(angle);
        gcDraw.strokeLine(x1, y1, lineEndX, lineEndY);
        gcDraw.setLineDashes(null);
        drawArrowHead(x2, y2, angle, size, color);
    }

    private void drawCurvedArrow(double x1, double y1, double cx, double cy, double x2, double y2, Color color, double size) {
        gcDraw.setStroke(color);
        gcDraw.setFill(color);
        gcDraw.setLineWidth(size / 3.0);

        // 1. Calcular el ángulo de la punta basado en el punto de control
        double angle = Math.atan2(y2 - cy, x2 - cx);
        double headLen = size * 1.5;

        // 2. RETROCESO: La línea termina un poco antes para no tapar la punta afilada
        double lineEndX = x2 - (headLen * 0.5) * Math.cos(angle);
        double lineEndY = y2 - (headLen * 0.5) * Math.sin(angle);

        // 3. Dibujar la curva
        gcDraw.beginPath();
        gcDraw.moveTo(x1, y1);
        gcDraw.quadraticCurveTo(cx, cy, lineEndX, lineEndY);
        gcDraw.stroke();

        // 4. Dibujar la punta de flecha en la coordenada real final
        drawArrowHead(x2, y2, angle, size, color);

        // 5. Círculo en la base (estilo iMovie)
        gcDraw.fillOval(x1 - size/6, y1 - size/6, size/3, size/3);
    }

    private void drawArrowHead(double x, double y, double angle, double size, Color color) {
        double headLen = size * 1.5; double headWidth = size;
        double xBase = x - headLen * Math.cos(angle); double yBase = y - headLen * Math.sin(angle);
        double x3 = xBase + headWidth * Math.cos(angle - Math.PI/2);
        double y3 = yBase + headWidth * Math.sin(angle - Math.PI/2);
        double x4 = xBase + headWidth * Math.cos(angle + Math.PI/2);
        double y4 = yBase + headWidth * Math.sin(angle + Math.PI/2);
        gcDraw.setFill(color);
        gcDraw.fillPolygon(new double[]{x, x3, x4}, new double[]{y, y3, y4}, 3);
    }

    private void drawProBase(double x1, double y1, double x2, double y2, Color c, double size) {
        double radius = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        double rx = radius;
        double ry = radius * 0.4;
        gcDraw.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.3));
        gcDraw.fillOval(x1 - rx, y1 - ry, rx * 2, ry * 2);
        gcDraw.setStroke(c);
        gcDraw.setLineWidth(3);
        gcDraw.setLineDashes(10, 5);
        gcDraw.strokeOval(x1 - rx, y1 - ry, rx * 2, ry * 2);
        gcDraw.setLineDashes(null);
    }


}
