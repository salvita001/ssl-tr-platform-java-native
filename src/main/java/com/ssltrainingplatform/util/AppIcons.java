package com.ssltrainingplatform.util;

import javafx.scene.shape.SVGPath;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;

public class AppIcons {

    public static SVGPath getIcon(String name, int size) {
        SVGPath path = new SVGPath();
        path.setContent(getPathContent(name));
        path.setFill(Color.BLUEVIOLET);
        path.setFillRule(FillRule.NON_ZERO);

        // Escalado para ajustar al tamaño deseado (basado en viewport 24x24)
        double scale = size / 24.0;
        path.setScaleX(scale);
        path.setScaleY(scale);
        return path;
    }

    private static String getPathContent(String name) {
        return switch (name) {
            // --- PLAYER ---
            case "play" -> "M8 5v14l11-7z";
            case "pause" -> "M6 19h4V5H6v14zm8-14v14h4V5h-4z";
            case "skipBack" -> "M6 6h2v12H6zm3.5 6l8.5 6V6z";
            case "skipForward" -> "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z";

            // --- HERRAMIENTAS ---
            // Cursor (Flecha de selección más nítida)
            case "cursor" -> "M7 2l12 11.2-5.8.5 3.3 7.3-2.2.9-3.2-7.4-4.4 4z";
            // Lápiz (Estilo edición moderno)
            case "pen" -> "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z";
            // Texto (T serif)
            case "text" -> "M5 4v3h5.5v12h3V7H19V4z";

            // --- FORMAS ---
            // Flecha Sólida
            case "arrow" -> "M16.01 11H4v2h12.01v3L20 12l-3.99-4z";
            // Flecha Discontinua (Icono visual representando el estilo)
            case "arrow-dashed" -> "M4 11h2v2H4zm4 0h2v2H8zm4 0h2v2h-2zm4 0h2v2h-2z M20 12l-3.99-4v3H16v2h.01v3z";
            // Flecha Curva 3D
            case "arrow-3d" -> "M19 6c0-1.1-.9-2-2-2H8c-1.1 0-2 .9-2 2v3h-2l4 4 4-4h-2V6h11v5h2V6z";

            // Foco (Spotlight)
            case "spotlight" -> "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z";
            // Base (Doble círculo)
            case "base" -> "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z";

            // Muro (Wall)
            case "wall" -> "M19 5v14H5V5h14m0-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z";

            // Polígono
            case "polygon" -> "M12 2l-5.5 9h11z"; // Triángulo simple que representa polígono

            // Rectángulo Sombreado
            case "rect-shaded" -> "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14z";

            // Lupa
            case "zoom-circle" -> "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z";
            case "zoom-rect" -> "M3 5v4h2V5h4V3H5c-1.1 0-2 .9-2 2zm2 10H3v4c0 1.1.9 2 2 2h4v-2H5v-4zm14 4h-4v2h4c1.1 0 2-.9 2-2v-4h-2v4zm0-16h-4v2h4v4h2V5c0-1.1-.9-2-2-2z";

            case "undo" -> "M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z";
            case "redo" -> "M18.4 10.6C16.55 9 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16c1.05-3.19 4.05-5.5 7.6-5.5 1.95 0 3.73.72 5.12 1.88L13 16h9V7l-3.6 3.6z";

            default -> "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z";
        };
    }
}