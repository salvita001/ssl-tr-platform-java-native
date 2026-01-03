package com.ssltrainingplatform.dto;

public class ExportItem {
    private String type;       // "VIDEO" o "IMAGE"
    private double start;      // Inicio del segmento (solo VIDEO)
    private double end;        // Fin del segmento (solo VIDEO)
    private String imageBase64;// La foto con dibujos (solo IMAGE)
    private double duration;   // Cuánto dura el congelado (solo IMAGE)

    // Constructor vacío
    public ExportItem() {}

    // Factory para VIDEO
    public static ExportItem video(double start, double end) {
        ExportItem item = new ExportItem();
        item.type = "VIDEO";
        item.start = start;
        item.end = end;
        return item;
    }

    // Factory para IMAGEN
    public static ExportItem image(String base64, double duration) {
        ExportItem item = new ExportItem();
        item.type = "IMAGE";
        item.imageBase64 = base64;
        item.duration = duration;
        return item;
    }

    // Getters para que GSON genere el JSON
    public String getType() { return type; }
    public double getStart() { return start; }
    public double getEnd() { return end; }
    public String getImageBase64() { return imageBase64; }
    public double getDuration() { return duration; }
}