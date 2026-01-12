package com.ssltrainingplatform.util;

import javafx.scene.shape.SVGPath;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;

public class AppIcons {

    public static SVGPath getIcon(String name, int size) {
        SVGPath path = new SVGPath();
        path.setContent(getPathContent(name));
        path.setFill(Color.LIGHTGRAY);
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

            // --- FILA 2: Flecha y Flecha Discontinua ---
            case "arrow" -> "M19 12l-7-7v5h-9v4h9v5z"; // Flecha simple derecha
            case "arrow-dashed" -> "M19 13l-4 4v-3h-2v-2h2v-3l4 4z M11 14h-2v-2h2v2z M7 14h-2v-2h2v2z M3 14h-2v-2h2v2z"; // Punteada

            // --- FILA 3: Flecha 3D y Texto ---
            case "arrow-3d" -> "M1.056 21.928c0-6.531 5.661-9.034 10.018-9.375V18.1L22.7 9.044 11.073 0v4.836a10.5 10.5 0 0 0-7.344 3.352C-.618 12.946-.008 21 .076 21.928z";
            case "text" -> "M5 4v3h5.5v12h3v-12h5.5v-3z"; // T mayúscula serif

            // --- FILA 4: Lápiz y Polígono ---
            case "pen" -> "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z";
            case "polygon" ->
                // 1. Marco exterior irregular (Forma asimétrica para indicar "libre")
                "M4,8 L18,4 L22,14 L6,20 Z " +
                // Hueco interno para crear el efecto de borde grueso
                "M6,9.5 L7.5,18.5 L19.5,13.5 L16.5,5.5 Z " +
                // 2. Líneas de rayado interno (Hatched) adaptadas a la forma irregular
                "M9,18 L18,9 l1,1 l-9,9 z " +
                "M13,19 L21,11 l1,1 l-8,8 z";
            // --- FILA 5: Zooms ---
            case "zoom-circle" -> "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z M10 7H9v2H7v1h2v2h1v-2h2V9h-2z"; // Con símbolo +
            case "zoom-rect" ->
                // 1. Esquinas (Rectángulos en ángulo)
                "M4,4 h4 v1.5 H5.5 V8 H4 V4 z " +
                "M16,4 h4 v4 h-1.5 V5.5 H16 V4 z " +
                "M20,16 v4 h-4 v-1.5 h2.5 V16 H20 z " +
                "M4,16 h1.5 v2.5 H8 V20 H4 V16 z " +
                // 2. Cruz central (Forma de '+' con área)
                "M12.5,9.5 h-1 v2 h-2 v1 h2 v2 h1 v-2 h2 v-1 h-2 v-2 z";
            // --- FILA 6: Tracking y Base ---
            case "tracking" ->
                // 1. Figura Humana (Cabeza y Cuerpo)
                "M12,12 a3,3 0 1,0 0,-6 a3,3 0 1,0 0,6 " +
                "M12,14 c-4,0 -6,2 -6,4 v1 h12 v-1 c0,-2 -2,-4 -6,-4 " +
                // 2. Esquinas de Enfoque (Simétricas y cerradas para Fill)
                "M3,8 V3 H8 v1.5 H4.5 V8 z " +      // Top-Left
                "M16,3 H21 V8 h-1.5 V4.5 H16 z " +  // Top-Right (CORREGIDA)
                "M3,16 v5 H8 v-1.5 H4.5 V16 z " +   // Bottom-Left
                "M16,21 H21 V16 h-1.5 v3.5 H16 z";  // Bottom-Right            case "base" -> "M2,12a10,5 0 1,0 20,0a10,5 0 1,0 -20,0 M6,12a6,3 0 1,1 12,0a6,3 0 1,1 -12,0"; // Elipse con punto central

            // --- FILA 7: Muro y Rectángulo Relleno ---
            case "wall" -> // Poste izquierdo
                "M6,10 h1.5 v10 h-1.5 z " +
                // Poste derecho (más elevado por perspectiva)
                "M16.5,5 h1.5 v12 h-1.5 z " +
                // Línea base diagonal sólida
                "M6,18.5 l12,-4 v1.5 l-12,4 z " +
                // Línea media discontinua (3 segmentos diagonales)
                "M7.5,15 l2.5,-0.8 v0.8 l-2.5,0.8 z " +
                "M11,13.8 l2.5,-0.8 v0.8 l-2.5,0.8 z " +
                "M14.5,12.7 l2.5,-0.8 v0.8 l-2.5,0.8 z";

            case "rect-shaded" ->
                // Borde exterior del rectángulo (marco hueco)
                "M3,3v18h18V3H3zm16,16H5V5h14v14z " +
                // Tres líneas diagonales internas con grosor
                "M7,21l14,-14v1.5l-14,14z " +
                "M3,13l10,-10h1.5l-11.5,11.5z " +
                "M11,21l10,-10v1.5l-11.5,11.5z";

            // --- OTROS ICONOS EXISTENTES ---
            case "undo" -> "M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z";
            case "redo" -> "M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16c1.05-3.19 4.05-5.5 7.6-5.5 1.95 0 3.73.72 5.12 1.88L13 16h9V7l-3.6 3.6z";
            case "skipBack" -> "M6 6h2v12H6zm3.5 6l8.5 6V6z";
            case "skipForward" -> "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z";
            case "video-camera" -> "M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z";
            // --- FILA 8: Borrado (Nuevos) ---
            case "trash" -> "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"; // Papelera
            case "clear" ->
                // 1. Mango sólido diagonal
                "M18.4,3 L21,5.6 L10,16.6 L7.4,14 Z " +
                // 2. Base de sujeción fina
                "M6,13 L11.5,18.5 L10.5,19.5 L5,14 Z " +
                // 3. Cerdas individuales (rayado)
                "M5.5,18.5 L7.5,20.5 L6.5,21.5 L4.5,19.5 Z " + // Cerca 1
                "M3.5,16.5 L5.5,18.5 L4.5,19.5 L2.5,17.5 Z " + // Cerca 2
                "M1.5,14.5 L3.5,16.5 L2.5,17.5 L0.5,15.5 Z";   // Cerca 3
            case "spotlight" -> "M12 2L18.5 17C18.5 17 16 19 12 19C8 19 5.5 17 5.5 17L12 2Z M 12,15 a 6.5,2 0 1,0 0,4 a 6.5,2 0 1,0 0,-4 M 12,1.5 a 1.5,1.5 0 1,0 0,3 a 1.5,1.5 0 1,0 0,-3";
            default -> "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"; // Signo de interrogación por defecto
        };
    }
}