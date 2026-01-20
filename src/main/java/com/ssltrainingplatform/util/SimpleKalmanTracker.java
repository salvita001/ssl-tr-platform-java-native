package com.ssltrainingplatform.util;

public class SimpleKalmanTracker {
    private double x, y;
    private double vx, vy;
    private long lastUpdate;

    // CAMBIO 1: Fricción 1.0 (Sin frenos). El jugador no patina sobre hielo.
    private double friction = 1.0;

    public SimpleKalmanTracker(double startX, double startY) {
        this.x = startX;
        this.y = startY;
        this.vx = 0;
        this.vy = 0;
        this.lastUpdate = System.currentTimeMillis();
    }

    public void predict() {
        x += vx;
        y += vy;
        vx *= friction;
        vy *= friction;
    }

    public void update(double measuredX, double measuredY) {
        // CAMBIO 2: Alpha 0.9.
        // Significa: "Confía al 90% en lo que dice la IA (Cuadrado Verde) y solo 10% en la inercia".
        // Esto hará que el círculo rojo se pegue al verde inmediatamente.
        double alpha = 0.9;

        double newVx = measuredX - x;
        double newVy = measuredY - y;

        vx = vx * (1.0 - alpha) + newVx * alpha;
        vy = vy * (1.0 - alpha) + newVy * alpha;

        x = measuredX;
        y = measuredY;
        lastUpdate = System.currentTimeMillis();
    }

    public double getX() { return x; }
    public double getY() { return y; }
}