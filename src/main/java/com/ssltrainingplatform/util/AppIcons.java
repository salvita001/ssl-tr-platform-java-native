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
            // --- FILA 1: Cursor y Mano ---
            case "cursor" -> "M7 2l12 11.2-5.8 1.3 3.5 7.5-2.8 1.3-3.5-7.5-4.2 3.8z"; // Cursor relleno
            case "hand" -> "M18 13c0-1.1-.9-2-2-2s-2 .9-2 2v-4c0-1.1-.9-2-2-2s-2 .9-2 2v-2c0-1.1-.9-2-2-2s-2 .9-2 2v3.2l-1.8-1.5c-.4-.3-.9-.4-1.3-.1l-1.1.8 4.5 8.2c.4.7 1.1 1.1 1.9 1.1h8.6c1.1 0 2-.9 2-2v-5z";

            // --- FILA 2: Flecha y Flecha Discontinua ---
            case "arrow" -> "M19 12l-7-7v5h-9v4h9v5z"; // Flecha simple derecha
            case "arrow-dashed" -> "M19 13l-4 4v-3h-2v-2h2v-3l4 4z M11 14h-2v-2h2v2z M7 14h-2v-2h2v2z M3 14h-2v-2h2v2z"; // Punteada

            // --- FILA 3: Flecha 3D y Texto ---
            case "arrow-3d" -> "M4 15s2-6 8-6 8 6 8 6v-4l4 6-4 6v-4s-4 0-8-6-8 6-8 6v-4zm11-5.5c-2.5-1.5-5-1.5-7.5 0 .5 1.5 1.5 3 3.5 3.5 2-.5 3-2 3.5-3.5z";
            case "text" -> "M5 4v3h5.5v12h3v-12h5.5v-3z"; // T mayúscula serif

            // --- FILA 4: Lápiz y Polígono ---
            case "pen" -> "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z";
            case "polygon" -> "M12 2l-5.5 9h-6.5l5 5.5-4.5 8.5 9.5-3.5 9.5 3.5-4.5-8.5 5-5.5h-6.5z"; // Estrella irregular

            // --- FILA 5: Zooms ---
            case "zoom-circle" -> "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z M10 7H9v2H7v1h2v2h1v-2h2V9h-2z"; // Con símbolo +
            case "zoom-rect" -> "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z M7 7h5v5h-5z"; // Con rectángulo dentro

            // --- FILA 6: Tracking y Base ---
            case "tracking" -> "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4zm-9-9v-3h3v-2h-5v5h2zm16-5h-3v2h5v-5h-2zm0 18h3v-3h2v5h-5v-2zm-18 0h3v2h-5v-5h2v3z"; // Persona con esquinas
            case "base" -> "M12 8c-4.42 0-8 1.79-8 4s3.58 4 8 4 8-1.79 8-4-3.58-4-8-4zm0 6c-3.31 0-6-1.34-6-3s2.69-3 6-3 6 1.34 6 3-2.69 3-6 3z M12 11c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"; // Elipse con punto central

            // --- FILA 7: Muro y Rectángulo Relleno ---
            case "wall" -> "M12 2L2 7v10l10 5 10-5V7l-10-5zm0 2.8l7 3.5-7 3.5-7-3.5 7-3.5zm-8 4.7l7 3.5v7.6l-7-3.5v-7.6zm16 7.6l-7 3.5v-7.6l7-3.5v7.6z"; // Muro isométrico 3D
            case "rect-shaded" -> "M3 5v14h18V5H3zm16 12H5V7h14v10z"; // Rectángulo relleno con borde

            // --- OTROS ICONOS EXISTENTES ---
            case "undo" -> "M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z";
            case "redo" -> "M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16c1.05-3.19 4.05-5.5 7.6-5.5 1.95 0 3.73.72 5.12 1.88L13 16h9V7l-3.6 3.6z";
            case "skipBack" -> "M6 6h2v12H6zm3.5 6l8.5 6V6z";
            case "skipForward" -> "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z";
            case "video-camera" -> "M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z";
            // --- FILA 8: Borrado (Nuevos) ---
            case "trash" -> "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"; // Papelera
            case "clear" -> "M15 16h4v2h-4v-2zm0-8h4v2h-4V8zm0 4h4v2h-4v-2zM3 18c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V8H3v10zM14 5h-3l-1-1H6l-1 1H2v2h12V5z"; // Escoba/Limpiar todo

            default -> "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"; // Signo de interrogación por defecto
        };
    }
}