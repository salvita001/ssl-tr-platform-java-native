package com.ssltrainingplatform.controller;

import com.google.gson.Gson;
import com.ssltrainingplatform.SslTpApp;
import com.ssltrainingplatform.dto.ExportItem;
import com.ssltrainingplatform.dto.ExportRequest;
import com.ssltrainingplatform.dto.SaveFrameRequest;
import com.ssltrainingplatform.model.EditorState;
import com.ssltrainingplatform.model.VideoSegment;
import com.ssltrainingplatform.service.AIService;
import com.ssltrainingplatform.service.FilmstripService;
import com.ssltrainingplatform.service.VideoApiClient;
import com.ssltrainingplatform.service.VideoService;
import com.ssltrainingplatform.model.DrawingShape;
import com.ssltrainingplatform.util.AppIcons;
import ai.djl.modality.cv.output.DetectedObjects;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Affine;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

public class EditorController {

    // --- UI ---
    @FXML private StackPane container;
    @FXML private ImageView videoView;
    @FXML private Canvas drawCanvas;
    @FXML private Canvas timelineCanvas;
    @FXML private ScrollBar timelineScroll;
    @FXML private Label lblTime;
    @FXML private TextField floatingInput;

    // --- BOTONES ---
    @FXML private ToggleButton btnCursor, btnPen, btnText;
    @FXML private ToggleButton btnArrow, btnArrowDashed, btnArrow3D;
    @FXML private ToggleButton btnSpotlight, btnBase, btnWall, btnRectShaded;
    @FXML private ToggleButton btnZoomCircle, btnZoomRect;
    @FXML private Button btnUndo, btnRedo;
    @FXML private ToggleButton btnToggleAI;
    @FXML private ToggleButton btnRectangle, btnPolygon, btnCurve;

    // --- CONTROLES ---
    @FXML private ColorPicker colorPicker;
    @FXML private Slider sizeSlider;
    @FXML private Slider zoomSlider;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private Button btnSkipStart;
    @FXML private Button btnSkipEnd;
    @FXML private Button btnPlayPause;

    // --- SERVICIOS ---
    private VideoService videoService;
    private AIService aiService;
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private AtomicBoolean isProcessingAI = new AtomicBoolean(false);
    private VideoApiClient apiClient = new VideoApiClient();
    private String serverVideoId = null; // Aquí guardaremos el ID que nos da el backen

    // --- ESTADO ---
    private List<VideoSegment> segments = new ArrayList<>();

    private VideoSegment selectedSegment = null;

    private double currentTimelineTime = 0.0;
    private double currentRealVideoTime = 0.0;
    private double totalTimelineDuration = 0.0;
    private double totalOriginalDuration = 0.0;
    private double pixelsPerSecond = 50.0;

    private AnimationTimer freezeTimer;
    private boolean isPlayingFreeze = false;
    private long lastTimerTick = 0;

    // --- DIBUJO ---
    private GraphicsContext gcDraw;
    private GraphicsContext gcTimeline;
    private List<DrawingShape> shapes = new ArrayList<>();

    private DrawingShape currentShape;
    private DrawingShape selectedShapeToMove;

    // ESTADO EDICIÓN
    private int dragMode = 0;
    private int dragPointIndex = -1;
    private double lastMouseX, lastMouseY;

    // AÑADIDAS NUEVAS HERRAMIENTAS AL ENUM
    public enum ToolType {
        CURSOR, PEN, TEXT, ARROW, ARROW_DASHED, ARROW_3D, SPOTLIGHT, BASE, WALL,
        POLYGON, RECT_SHADED, ZOOM_CIRCLE, ZOOM_RECT, RECTANGLE, CURVE
    }
    private ToolType currentTool = ToolType.CURSOR;

    private List<DetectedObjects.DetectedObject> currentDetections = new ArrayList<>();

    private TreeMap<Double, Image> filmstripMap = new TreeMap<>();

    private VideoSegment segmentBeingDragged = null;
    private boolean isDraggingTimeline = false;
    private double currentDragX = 0.0;
    private long lastSeekTime = 0;

    private boolean isSeeking = false;
    private double targetSeekTime = -1;

    private long seekFinishedTime = 0;

    private int stickySegmentIndex = -1;
    private long stickyStartTime = 0;

    private java.util.Stack<EditorState> undoStack = new java.util.Stack<>();
    private java.util.Stack<EditorState> redoStack = new java.util.Stack<>();
    private long ignoreUpdatesUntil = 0;
    private Map<String, List<DrawingShape>> annotationsCache = new HashMap<>();

    @FXML private ProgressBar exportProgressBar; // Nueva barra en tu FXML
    @FXML private Label lblStatus; // Label para mensajes como "Generando frame 1/5..."

    // =========================================================================
    //                               INICIALIZACIÓN
    // =========================================================================

    public void initialize() {
        videoService = new VideoService(videoView);
        aiService = new AIService();
        try {
            aiService.loadModel("models/yolo11l.torchscript");
        } catch (Exception ignored) {}

        applyIcons();

        setupTooltips();

        container.setMinWidth(0); container.setMinHeight(0);
        videoView.setPreserveRatio(true);
        videoView.fitWidthProperty().bind(container.widthProperty());
        videoView.fitHeightProperty().bind(container.heightProperty());

        gcDraw = drawCanvas.getGraphicsContext2D();
        drawCanvas.widthProperty().bind(container.widthProperty());
        drawCanvas.heightProperty().bind(container.heightProperty());

        gcTimeline = timelineCanvas.getGraphicsContext2D();
        if (timelineCanvas.getParent() instanceof Region) {
            timelineCanvas.widthProperty().bind(((Region) timelineCanvas.getParent()).widthProperty());
        }

        if (colorPicker != null) {
            colorPicker.setValue(Color.web("#7c3aed"));
            colorPicker.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (selectedShapeToMove != null) {
                    selectedShapeToMove.setColor(toHex(newVal));
                    redrawVideoCanvas();
                }
            });
        }

        if (sizeSlider != null) {
            sizeSlider.setValue(20);
            sizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (selectedShapeToMove != null) {
                    selectedShapeToMove.setStrokeWidth(newVal.doubleValue());
                    redrawVideoCanvas();
                }
            });
        }

        if (zoomSlider != null) {
            zoomSlider.setValue(pixelsPerSecond);
            zoomSlider.valueProperty().addListener((o, old, val) -> {
                pixelsPerSecond = val.doubleValue();
                updateScrollbarAndRedraw();
            });
        }

        timelineCanvas.widthProperty().addListener(o -> updateScrollbarAndRedraw());
        timelineScroll.valueProperty().addListener(o -> redrawTimeline());

        if (floatingInput != null) {
            floatingInput.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) confirmFloatingText();
                if (e.getCode() == KeyCode.ESCAPE) floatingInput.setVisible(false);
            });
        }

        setupMouseEvents();
        setupVideoEvents();
        setupFreezeTimer();
        // 1. Permitir que el contenedor reciba eventos de teclado
        container.setFocusTraversable(true);
        container.requestFocus(); // Pedir foco al arrancar (o cuando hagas clic)

        // 2. Escuchar la tecla ESCAPE
        container.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                // CASO A: Estamos dibujando un polígono o muro -> TERMINAR FORMA
                if (currentShape != null && (currentTool == ToolType.POLYGON || currentTool == ToolType.WALL)) {
                    finishPolyShape();
                }
                // CASO B: Estamos con cualquier otra herramienta -> VOLVER AL CURSOR
                else {
                    setToolCursor();
                    // También deseleccionamos cualquier cosa seleccionada
                    selectedSegment = null;
                    selectedShapeToMove = null;
                    redrawVideoCanvas();
                    redrawTimeline();
                }
            }

            // Opcional: Borrar con SUPR (Delete)
            if (e.getCode() == KeyCode.DELETE) {
                // Si hay una forma seleccionada (modo edición)
                if (selectedShapeToMove != null) {
                    shapes.remove(selectedShapeToMove);
                    selectedShapeToMove = null;
                    redrawVideoCanvas();
                }
                // O si hay un segmento seleccionado
                else if (selectedSegment != null) {
                    onDeleteSegment();
                }
            }
        });

        // IMPORTANTE: Asegurarnos de recuperar el foco al hacer clic en el canvas
        // (Si no, si haces clic en un botón, el canvas pierde el foco y el ESC deja de ir)
        drawCanvas.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            container.requestFocus();
        });

        timelineCanvas.setOnMouseReleased(this::onTimelineReleased);

        // 3. INYECTAR EL TOKEN AL CLIENTE API
        if (SslTpApp.AUTH_TOKEN != null) {
            apiClient.setAuthToken(SslTpApp.AUTH_TOKEN);
            System.out.println("✅ Cliente API autenticado con el token recibido.");
        } else {
            System.out.println("⚠️ Aviso: No hay token. La subida fallará si el backend requiere auth.");
        }

        if (exportProgressBar != null) exportProgressBar.setVisible(false);
        if (lblStatus != null) lblStatus.setText("");
    }

    @FXML
    public void onTimelineClick(MouseEvent e) {
        double y = e.getY();
        double topMargin = 22; // La altura de la regla que definimos en redrawTimeline

        // A. CLIC EN LA REGLA (ARRIBA) -> SEEK (Mover cabezal)
        if (y < topMargin) {
            isDraggingTimeline = false;
            segmentBeingDragged = null;
            seekTimeline(e.getX());
        }
        // B. CLIC EN LAS PISTAS (ABAJO) -> SELECCIONAR PARA ARRASTRAR
        else {
            handleSegmentSelection(e.getX());
            if (selectedSegment != null) {
                isDraggingTimeline = true;
                segmentBeingDragged = selectedSegment;
            }
        }
    }

    @FXML
    public void onTimelineDrag(MouseEvent e) {
        if (!isDraggingTimeline) {
            seekTimeline(e.getX());
        } else {
            // Guardamos la posición visual para el "fantasma"
            currentDragX = e.getX();

            // Redibujamos para que se vea moverse
            redrawTimeline();
        }
    }

    // NUEVO MÉTODO: SOLTAR Y REORDENAR
    private void onTimelineReleased(MouseEvent e) {
        saveState();
        if (isDraggingTimeline && segmentBeingDragged != null) {

            // 1. Calcular dónde lo hemos soltado
            double scrollOffset = timelineScroll.getValue();
            double dropTime = (e.getX() + scrollOffset) / pixelsPerSecond;

            // 2. Buscar en qué índice cae ese tiempo
            int newIndex = segments.size(); // Por defecto al final
            for (int i = 0; i < segments.size(); i++) {
                VideoSegment s = segments.get(i);
                // Si soltamos antes de la mitad de un segmento, insertamos ahí
                if (dropTime < (s.getStartTime() + s.getEndTime()) / 2) {
                    newIndex = i;
                    break;
                }
            }

            // 3. Mover el segmento en la lista
            int oldIndex = segments.indexOf(segmentBeingDragged);

            if (oldIndex != -1 && oldIndex != newIndex) {
                segments.remove(oldIndex);

                // Ajustar índice si el borrado afectó a la posición de inserción
                if (newIndex > oldIndex) newIndex--;

                // Insertar en nueva posición
                if (newIndex >= segments.size()) {
                    segments.add(segmentBeingDragged);
                } else {
                    segments.add(newIndex, segmentBeingDragged);
                }

                // 4. RECALCULAR TIEMPOS Y REDIBUJAR
                recalculateSegmentTimes();
                System.out.println("Segmento movido a la posición " + newIndex);
            }

            isDraggingTimeline = false;
            segmentBeingDragged = null;
        }
    }

    // Método auxiliar para seleccionar sin mover el cabezal
    private void handleSegmentSelection(double x) {
        double scrollOffset = timelineScroll.getValue();
        double t = (x + scrollOffset) / pixelsPerSecond;

        selectedSegment = null;
        for (VideoSegment s : segments) {
            if (t >= s.getStartTime() && t < s.getEndTime()) {
                selectedSegment = s;
                break;
            }
        }
        redrawTimeline();
    }

    private void applyIcons() {
        if(btnCursor != null) btnCursor.setGraphic(AppIcons.getIcon("cursor", 16));
        if(btnPen != null) btnPen.setGraphic(AppIcons.getIcon("pen", 16));
        if(btnText != null) btnText.setGraphic(AppIcons.getIcon("text", 16));
        if(btnArrow != null) btnArrow.setGraphic(AppIcons.getIcon("arrow", 16));
        if(btnArrowDashed != null) btnArrowDashed.setGraphic(AppIcons.getIcon("arrow-dashed", 16));
        if(btnArrow3D != null) btnArrow3D.setGraphic(AppIcons.getIcon("arrow-3d", 16));
        if(btnSpotlight != null) btnSpotlight.setGraphic(AppIcons.getIcon("spotlight", 16));
        if(btnBase != null) btnBase.setGraphic(AppIcons.getIcon("base", 16));
        if(btnWall != null) btnWall.setGraphic(AppIcons.getIcon("wall", 16));
        if(btnPolygon != null) btnPolygon.setGraphic(AppIcons.getIcon("polygon", 16));
        if(btnRectShaded != null) btnRectShaded.setGraphic(AppIcons.getIcon("rect-shaded", 16));
        if(btnZoomCircle != null) btnZoomCircle.setGraphic(AppIcons.getIcon("zoom-circle", 16));
        if(btnZoomRect != null) btnZoomRect.setGraphic(AppIcons.getIcon("zoom-rect", 16));
        if(btnUndo != null) btnUndo.setGraphic(AppIcons.getIcon("undo", 20));
        if(btnRedo != null) btnRedo.setGraphic(AppIcons.getIcon("redo", 20));
        if(btnSkipStart != null) btnSkipStart.setGraphic(AppIcons.getIcon("skipBack", 16));
        if(btnSkipEnd != null) btnSkipEnd.setGraphic(AppIcons.getIcon("skipForward", 16));
        if(btnCurve != null) btnCurve.setGraphic(AppIcons.getIcon("curve", 16));
        if(btnRectangle != null) btnRectangle.setGraphic(AppIcons.getIcon("rectangle", 16));
    }

    // --- SELECTORES HERRAMIENTAS ---
    @FXML public void setToolCursor() { changeTool(ToolType.CURSOR); }
    @FXML public void setToolPen() { changeTool(ToolType.PEN); }
    @FXML public void setToolText() { changeTool(ToolType.TEXT); }
    @FXML public void setToolArrow() { changeTool(ToolType.ARROW); }
    @FXML public void setToolArrowDashed() { changeTool(ToolType.ARROW_DASHED); }
    @FXML public void setToolArrow3D() { changeTool(ToolType.ARROW_3D); }
    @FXML public void setToolSpotlight() { changeTool(ToolType.SPOTLIGHT); }
    @FXML public void setToolBase() { changeTool(ToolType.BASE); }
    @FXML public void setToolWall() { changeTool(ToolType.WALL); }
    @FXML public void setToolPolygon() { changeTool(ToolType.POLYGON); }
    @FXML public void setToolRectShaded() { changeTool(ToolType.RECT_SHADED); }
    @FXML public void setToolZoomCircle() { changeTool(ToolType.ZOOM_CIRCLE); }
    @FXML public void setToolZoomRect() { changeTool(ToolType.ZOOM_RECT); }
    @FXML public void setToolRectangle() { changeTool(ToolType.RECTANGLE); }
    @FXML public void setToolCurve() { changeTool(ToolType.CURVE); }

    private void changeTool(ToolType type) {
        finishPolyShape();
        currentTool = type;
        selectedShapeToMove = null;
        redrawVideoCanvas();
    }

    // --- CORRECCIÓN DEL MURO Y POLÍGONO ---
    private void finishPolyShape() {
        if (currentShape != null && ("wall".equals(currentShape.getType()) || "polygon".equals(currentShape.getType()))) {
            List<Double> pts = currentShape.getPoints();

            // Eliminamos los puntos "fantasma" del cursor.
            // A veces pueden quedar 1 o 2 puntos sobrantes al final.
            // Si el número de coordenadas es impar, borramos 1.
            if (pts.size() % 2 != 0) pts.remove(pts.size() - 1);

            // Eliminamos el último par que corresponde a la posición del mouse
            // SOLO si tenemos suficientes puntos para formar una línea.
            if (pts.size() >= 4) {
                pts.remove(pts.size() - 1); // Y del mouse
                pts.remove(pts.size() - 1); // X del mouse
            }

            // Si después de limpiar quedan menos de 2 puntos reales (4 coordenadas), no es válido.
            if (pts.size() < 4) {
                shapes.remove(currentShape);
            } else {
                switchToCursorAndSelect(currentShape);
            }
            currentShape = null;
            redrawVideoCanvas();
        }
    }

    private void switchToCursorAndSelect(DrawingShape shape) {
        // 1. Volver a la herramienta Cursor (para no seguir dibujando flechas sin querer)
        currentTool = ToolType.CURSOR;
        if (btnCursor != null) btnCursor.setSelected(true);

        // 2. CORRECCIÓN AQUÍ: NO seleccionar la forma automáticamente
        // Antes tenías: selectedShapeToMove = shape;
        // Ahora ponemos:
        selectedShapeToMove = null;

        // 3. (Opcional) Sí actualizamos los controles de color/tamaño para que coincidan
        // con lo que acabas de dibujar, por si quieres dibujar otra cosa igual.
        if (colorPicker != null) colorPicker.setValue(Color.web(shape.getColor()));
        if (sizeSlider != null) sizeSlider.setValue(shape.getStrokeWidth());

        // 4. Redibujar para asegurar que se ve limpio (sin puntos azules)
        redrawVideoCanvas();
    }

    // =========================================================================
    //               EVENTOS MOUSE
    // =========================================================================

    private void setupMouseEvents() {
        drawCanvas.setOnMousePressed(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            dragMode = 0;

            // 1. OBTENER EL SEGMENTO ACTUAL AL PRINCIPIO
            VideoSegment currentSeg = getCurrentSegment(); // <--- IMPORTANTE: Necesitamos esto accesible desde el principio

            // ---------------------------------------------------------
            // 1. MODO EDICIÓN (Seleccionar)
            // ---------------------------------------------------------
            if (currentTool == ToolType.CURSOR) {
                if (selectedShapeToMove != null) {
                    if (checkHandles(selectedShapeToMove, e.getX(), e.getY())) {
                        redrawVideoCanvas();
                        return;
                    }
                }
                selectedShapeToMove = null;
                for (int i = shapes.size() - 1; i >= 0; i--) {
                    DrawingShape s = shapes.get(i);

                    // --- AÑADIR ESTO: FILTRO DE VISIBILIDAD ---
                    // Si la forma pertenece a otro clip, la ignoramos (no se puede seleccionar)
                    if (s.getClipId() != null && currentSeg != null && !s.getClipId().equals(currentSeg.getId())) {
                        continue;
                    }
                    // ------------------------------------------

                    if (s.isHit(e.getX(), e.getY())) {
                        selectedShapeToMove = s;
                        dragMode = 1;
                        if(colorPicker != null) colorPicker.setValue(Color.web(s.getColor()));
                        if(sizeSlider != null) sizeSlider.setValue(s.getStrokeWidth());
                        break;
                    }
                }
                redrawVideoCanvas();
                return;
            }

            // ---------------------------------------------------------
            // AUTO-CONGELADO INTELIGENTE
            // ---------------------------------------------------------
            // Nota: Ya hemos obtenido 'currentSeg' arriba
            if (currentSeg != null && !currentSeg.isFreezeFrame()) {
                System.out.println("Auto-congelando para editar...");

                if (videoService.isPlaying()) {
                    videoService.pause();
                    btnPlayPause.setText("▶");
                }

                insertFreezeFrame(5.0);

                // --- AÑADIR ESTO: ACTUALIZAR EL SEGMENTO ---
                // Al insertar el freeze, el tiempo ha cambiado o la estructura ha cambiado.
                // Necesitamos obtener el NUEVO segmento (el azul) para asignarle el dibujo a él.
                currentSeg = getCurrentSegment();
                // -------------------------------------------
            }

            // ---------------------------------------------------------
            // 2. MODO CREACIÓN
            // ---------------------------------------------------------
            selectedShapeToMove = null;
            if (currentTool == ToolType.TEXT) { showFloatingInput(e.getX(), e.getY()); return; }

            Color c = colorPicker != null ? colorPicker.getValue() : Color.RED;
            double s = sizeSlider != null ? sizeSlider.getValue() : 5;

            // A. CASO LÁPIZ
            if (currentTool == ToolType.PEN) {
                saveState();
                currentShape = new DrawingShape("pen"+System.currentTimeMillis(), "pen", e.getX(), e.getY(), toHex(c));

                // --- AÑADIR ESTO ---
                if (currentSeg != null) currentShape.setClipId(currentSeg.getId());
                // -------------------

                currentShape.setStrokeWidth(s);
                currentShape.addPoint(e.getX(), e.getY());
                shapes.add(currentShape);
                redrawVideoCanvas();
                return;
            }

            // B. CASO POLÍGONO
            if (currentTool == ToolType.POLYGON) {
                if (e.getClickCount() == 2 || e.getButton() == MouseButton.SECONDARY) {
                    finishPolyShape();
                    return;
                }
                if (currentShape == null || !"polygon".equals(currentShape.getType())) {
                    saveState();
                    currentShape = new DrawingShape("p"+System.currentTimeMillis(), "polygon", e.getX(), e.getY(), toHex(c));

                    // --- AÑADIR ESTO ---
                    if (currentSeg != null) currentShape.setClipId(currentSeg.getId());
                    // -------------------

                    currentShape.setStrokeWidth(s);
                    currentShape.addPoint(e.getX(), e.getY());
                    currentShape.addPoint(e.getX(), e.getY());
                    shapes.add(currentShape);
                } else {
                    currentShape.addPoint(e.getX(), e.getY());
                    if (currentShape.getPoints().size() >= 10) {
                        finishPolyShape();
                        return;
                    }
                }
                redrawVideoCanvas();
                return;
            }

            // C. CASO CURVA
            if (currentTool == ToolType.CURVE) {
                saveState();
                currentShape = new DrawingShape("c"+System.currentTimeMillis(), "curve", e.getX(), e.getY(), toHex(c));

                // --- AÑADIR ESTO ---
                if (currentSeg != null) currentShape.setClipId(currentSeg.getId());
                // -------------------

                currentShape.setStrokeWidth(s);
                currentShape.addPoint(e.getX(), e.getY());
                shapes.add(currentShape);
                redrawVideoCanvas();
                return;
            }

            // D. ARRASTRE NORMAL (Flechas, Rectángulos, etc.)
            String type = switch(currentTool) {
                case ARROW -> "arrow";
                case ARROW_DASHED -> "arrow-dashed";
                case ARROW_3D -> "arrow-3d";
                case SPOTLIGHT -> "spotlight";
                case BASE -> "base";
                case WALL -> "wall";
                case RECT_SHADED -> "rect-shaded";
                case ZOOM_CIRCLE -> "zoom-circle";
                case ZOOM_RECT -> "zoom-rect";
                case RECTANGLE -> "rectangle";
                default -> "arrow";
            };

            saveState();

            currentShape = new DrawingShape("s"+System.currentTimeMillis(), type, e.getX(), e.getY(), toHex(c));

            // --- AÑADIR ESTO ---
            if (currentSeg != null) currentShape.setClipId(currentSeg.getId());
            // -------------------

            currentShape.setStrokeWidth(s);
            shapes.add(currentShape);
        });

        drawCanvas.setOnMouseDragged(e -> {
            double dx = e.getX() - lastMouseX;
            double dy = e.getY() - lastMouseY;

            // 1. MODO EDICIÓN (Cursor)
            if (currentTool == ToolType.CURSOR && selectedShapeToMove != null) {

                // Mover toda la forma
                if (dragMode == 1) {
                    selectedShapeToMove.move(dx, dy);
                } else if (dragMode == 2) {
                    selectedShapeToMove.setStartX(selectedShapeToMove.getStartX() + dx);
                    selectedShapeToMove.setStartY(selectedShapeToMove.getStartY() + dy);
                } else if (dragMode == 3) {
                    selectedShapeToMove.setEndX(selectedShapeToMove.getEndX() + dx);
                    selectedShapeToMove.setEndY(selectedShapeToMove.getEndY() + dy);
                } else if (dragMode == 4 && dragPointIndex >= 0) {
                    List<Double> pts = selectedShapeToMove.getPoints();
                    if (dragPointIndex + 1 < pts.size()) {
                        pts.set(dragPointIndex, pts.get(dragPointIndex) + dx);
                        pts.set(dragPointIndex + 1, pts.get(dragPointIndex + 1) + dy);
                    }
                } // MOVER PUNTO DE CONTROL DE CURVA
                else if (dragMode == 6 && "curve".equals(selectedShapeToMove.getType())) {
                    List<Double> pts = selectedShapeToMove.getPoints();
                    if (!pts.isEmpty()) {
                        pts.set(0, pts.get(0) + dx); // Mover X del control
                        pts.set(1, pts.get(1) + dy); // Mover Y del control
                    }
                }

                redrawVideoCanvas();
            } else if (currentTool == ToolType.PEN && currentShape != null) {
                currentShape.addPoint(e.getX(), e.getY());
                redrawVideoCanvas();
            } else if (currentShape != null && currentTool != ToolType.POLYGON) {
                currentShape.setEndX(e.getX());
                currentShape.setEndY(e.getY());
                if (currentTool == ToolType.CURVE && !currentShape.getPoints().isEmpty()) {
                    double midX = (currentShape.getStartX() + e.getX()) / 2;
                    double midY = (currentShape.getStartY() + e.getY()) / 2;
                    currentShape.getPoints().set(0, midX);
                    currentShape.getPoints().set(1, midY);
                }
                redrawVideoCanvas();
            }

            lastMouseX = e.getX();
            lastMouseY = e.getY();
        });

        drawCanvas.setOnMouseReleased(e -> {
            // Al soltar, finalizamos cualquier forma (excepto Polígono que sigue vivo)
            if (currentTool != ToolType.POLYGON && currentShape != null) {
                switchToCursorAndSelect(currentShape);
                currentShape = null;
            }
            dragMode = 0;
        });
    }

    private boolean checkHandles(DrawingShape s, double mx, double my) {
        double dist = 20.0; // Aumentamos un poco el área de detección para que sea fácil atinar

        // 1. CASO POLÍGONO (Solo el polígono real usa lista de puntos)
        if ("polygon".equals(s.getType())) {
            List<Double> pts = s.getPoints();
            for (int i = 0; i < pts.size(); i+=2) {
                if (Math.abs(mx - pts.get(i)) < dist && Math.abs(my - pts.get(i+1)) < dist) {
                    dragMode = 4;
                    dragPointIndex = i;
                    return true;
                }
            }
            return false;
        }

        // 2. CURVA (Punto de Control)
        if ("curve".equals(s.getType()) && !s.getPoints().isEmpty()) {
            double cx = s.getPoints().get(0);
            double cy = s.getPoints().get(1);
            // Si haces clic en el punto de control (el de en medio)
            if (Math.abs(mx - cx) < dist && Math.abs(my - cy) < dist) {
                dragMode = 6; // MODO 6: MOVER CURVATURA
                return true;
            }
        }

        // 2. CASO TEXTO (Redimensión especial)
        if ("text".equals(s.getType())) {
            javafx.scene.text.Text temp = new javafx.scene.text.Text(s.getTextValue());
            temp.setFont(Font.font("Arial", FontWeight.BOLD, s.getStrokeWidth() * 2.5));
            double w = temp.getLayoutBounds().getWidth();
            double h = temp.getLayoutBounds().getHeight();
            double resizeX = s.getStartX() + w;
            double resizeY = s.getStartY() + h;

            // Esquina inferior derecha (Redimensionar texto)
            if (Math.abs(mx - resizeX) < dist && Math.abs(my - resizeY) < dist) {
                dragMode = 5;
                return true;
            }
            return false;
        }

        // C. FORMAS ESTÁNDAR (Flechas, Muro, Zoom, etc.)
        // Punto INICIO
        if (Math.abs(mx - s.getStartX()) < dist && Math.abs(my - s.getStartY()) < dist) {
            dragMode = 2; // Modificar Inicio
            return true;
        }
        // Punto FIN (Estirar)
        if (Math.abs(mx - s.getEndX()) < dist && Math.abs(my - s.getEndY()) < dist) {
            dragMode = 3; // Modificar Fin
            return true;
        }

        return false;
    }

    // =========================================================================
    //                        RENDERIZADO
    // =========================================================================

    private void redrawVideoCanvas() {
        gcDraw.clearRect(0, 0, drawCanvas.getWidth(), drawCanvas.getHeight());

        double canvasW = drawCanvas.getWidth(); double canvasH = drawCanvas.getHeight();
        javafx.scene.image.Image vidImg = videoView.getImage();
        double scale = 1.0; double vidDispW = 0, vidDispH = 0; double offX = 0, offY = 0;

        if (vidImg != null) {
            scale = Math.min(canvasW / vidImg.getWidth(), canvasH / vidImg.getHeight());
            vidDispW = vidImg.getWidth() * scale; vidDispH = vidImg.getHeight() * scale;
            offX = (canvasW - vidDispW) / 2.0; offY = (canvasH - vidDispH) / 2.0;
        }
        final double AI_INPUT_SIZE = 640.0;

        // IA
        if (btnToggleAI.isSelected() && currentDetections != null && vidImg != null) {
            gcDraw.setLineWidth(2);
            for (var obj : currentDetections) {
                if (obj.getClassName().equals("person") || obj.getClassName().equals("sports ball")) {
                    var r = obj.getBoundingBox().getBounds();
                    double x = (r.getX() / AI_INPUT_SIZE) * vidDispW + offX;
                    double y = (r.getY() / AI_INPUT_SIZE) * vidDispH + offY;
                    double w = (r.getWidth() / AI_INPUT_SIZE) * vidDispW;
                    double h = (r.getHeight() / AI_INPUT_SIZE) * vidDispH;
                    Color boxColor = obj.getClassName().equals("sports ball") ? Color.ORANGE : Color.LIMEGREEN;
                    gcDraw.setStroke(boxColor); gcDraw.strokeRect(x, y, w, h);
                }
            }
        }

        VideoSegment activeSeg = getCurrentSegment();
        String activeSegId = (activeSeg != null) ? activeSeg.getId() : null;

        // Combinar dibujos en vivo con los de la caché
        List<DrawingShape> cachedShapes = (activeSegId != null) ? annotationsCache.get(activeSegId) : null;
        List<DrawingShape> allToDraw = new ArrayList<>(shapes);
        if (cachedShapes != null) {
            allToDraw.addAll(cachedShapes);
        }

        for (DrawingShape s : allToDraw) {
            // FILTRO CRÍTICO: Si el dibujo tiene dueño (clipId), solo se ve si ese clip es el activo
            if (s.getClipId() != null) {
                if (activeSegId == null || !activeSegId.equals(s.getClipId())) {
                    continue;
                }
            }
            Color c = Color.web(s.getColor());
            double size = s.getStrokeWidth();
            double x1 = s.getStartX(); double y1 = s.getStartY();
            double x2 = s.getEndX(); double y2 = s.getEndY();

            // Lógica de Selección (Borde Punteado)
            if (s == selectedShapeToMove) {
                gcDraw.setLineWidth(1); gcDraw.setStroke(Color.CYAN); gcDraw.setLineDashes(5);

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
                    gcDraw.setStroke(Color.WHITE);
                    gcDraw.setLineWidth(1);

                    // Punto A (Inicio)
                    gcDraw.fillOval(s.getStartX() - 5, s.getStartY() - 5, 10, 10);
                    gcDraw.strokeOval(s.getStartX() - 5, s.getStartY() - 5, 10, 10);

                    // Punto B (Fin)
                    gcDraw.fillOval(s.getEndX() - 5, s.getEndY() - 5, 10, 10);
                    gcDraw.strokeOval(s.getEndX() - 5, s.getEndY() - 5, 10, 10);

                    if ("curve".equals(s.getType()) && !s.getPoints().isEmpty()) {
                        double cx = s.getPoints().get(0);
                        double cy = s.getPoints().get(1);

                        // Líneas guía (opcional, ayuda visual)
                        gcDraw.setStroke(Color.GRAY); gcDraw.setLineWidth(1); gcDraw.setLineDashes(3);
                        gcDraw.strokeLine(x1, y1, cx, cy);
                        gcDraw.strokeLine(x2, y2, cx, cy);

                        // Punto de control
                        gcDraw.setLineDashes(null);
                        gcDraw.setFill(Color.YELLOW); gcDraw.setStroke(Color.BLACK);
                        gcDraw.fillOval(cx - 6, cy - 6, 12, 12);
                        gcDraw.strokeOval(cx - 6, cy - 6, 12, 12);
                    }
                }
                gcDraw.setLineDashes(null);
            }

            // DIBUJAR LA FORMA
            switch (s.getType()) {
                case "arrow": drawProArrow(x1, y1, x2, y2, c, size, false); break;
                case "arrow-dashed": drawProArrow(x1, y1, x2, y2, c, size, true); break;
                case "arrow-3d":
                    double cx = (x1 + x2) / 2; double cy = Math.min(y1, y2) - Math.abs(x2 - x1) * 0.3;
                    drawCurvedArrow(x1, y1, cx, cy, x2, y2, c, size);
                    break;
                case "pen":
                    // Dibujamos el lápiz conectando todos los puntos
                    if (s.getPoints() != null && s.getPoints().size() > 2) {
                        gcDraw.setStroke(c);
                        gcDraw.setLineWidth(size);
                        gcDraw.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
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
                case "base": drawProBase(x1, y1, x2, y2, c, size); break;
                case "spotlight": drawProSpotlight(x1, y1, x2, y2, c, size); break;
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
                case "rectangle":
                    gcDraw.setStroke(c); gcDraw.setLineWidth(size);
                    double rLeft = Math.min(x1, x2); double rTop = Math.min(y1, y2);
                    gcDraw.strokeRect(rLeft, rTop, Math.abs(x2-x1), Math.abs(y2-y1));
                    break;
                case "curve":
                    if (s.getPoints() != null && !s.getPoints().isEmpty()) {
                        double c_x = s.getPoints().get(0);
                        double c_y = s.getPoints().get(1);

                        // 1. CALCULAR ÁNGULO Y PUNTO DE CORTE
                        // Calculamos el ángulo desde el punto de control hacia el final
                        double angle = Math.atan2(y2 - c_y, x2 - c_x);

                        // Calculamos la longitud de la cabeza de la flecha (la misma que usas en drawArrowHead)
                        double arrowLen = size * 1.5;

                        // Calculamos dónde debe terminar la línea (retrocedemos desde la punta)
                        // Esto evita que el grosor de la línea tape la punta afilada
                        double lineEndX = x2 - arrowLen * Math.cos(angle);
                        double lineEndY = y2 - arrowLen * Math.sin(angle);

                        // 2. DIBUJAR LA LÍNEA CURVA (HASTA LA BASE DE LA FLECHA)
                        gcDraw.setStroke(c);
                        gcDraw.setLineWidth(size);
                        gcDraw.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);

                        gcDraw.beginPath();
                        gcDraw.moveTo(x1, y1);
                        // Usamos lineEndX/Y en lugar de x2/y2 para la curva
                        gcDraw.quadraticCurveTo(c_x, c_y, lineEndX, lineEndY);
                        gcDraw.stroke();

                        // 3. DIBUJAR LA PUNTA DE FLECHA (EN EL PUNTO FINAL REAL)
                        drawArrowHead(x2, y2, angle, size, c);
                    }
                    break;
                default:
                    gcDraw.setStroke(c); gcDraw.setLineWidth(3);
                    gcDraw.strokeRect(Math.min(x1,x2), Math.min(y1,y2), Math.abs(x2-x1), Math.abs(y2-y1));
            }
        }
        gcDraw.setEffect(null); gcDraw.setLineDashes(null);
    }

    // --- MÉTODOS DE DIBUJO ---

    private void drawSimpleWall(double x1, double y1, double x2, double y2, Color c) {
        double wallHeight = 80.0; // Altura del muro

        // 1. Cara frontal semitransparente
        gcDraw.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.3));
        gcDraw.fillPolygon(
                new double[]{x1, x2, x2, x1},
                new double[]{y1, y2, y2 - wallHeight, y1 - wallHeight},
                4
        );

        // 2. Bordes sólidos
        gcDraw.setStroke(c);
        gcDraw.setLineWidth(2);

        // Base y Tope
        gcDraw.strokeLine(x1, y1, x2, y2); // Línea base (suelo)
        gcDraw.strokeLine(x1, y1 - wallHeight, x2, y2 - wallHeight); // Línea tope

        // Postes verticales
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
        gcDraw.setStroke(c); gcDraw.setLineWidth(2);
        gcDraw.strokePolygon(xPoints, yPoints, n);
    }

    private void drawShadedRect(double x1, double y1, double x2, double y2, Color c) {
        double left = Math.min(x1, x2); double top = Math.min(y1, y2);
        double w = Math.abs(x2 - x1); double h = Math.abs(y2 - y1);

        gcDraw.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.3));
        gcDraw.fillRect(left, top, w, h);
        gcDraw.setStroke(c); gcDraw.setLineWidth(2);
        gcDraw.strokeRect(left, top, w, h);
    }

    private void drawRealZoom(double x1, double y1, double x2, double y2, Color c, javafx.scene.image.Image img, double imgX, double imgY, double imgW, double imgH, boolean circle) {
        double w = Math.abs(x2 - x1); double h = Math.abs(y2 - y1);
        double left = Math.min(x1, x2); double top = Math.min(y1, y2);

        gcDraw.save();
        gcDraw.beginPath();
        if (circle) gcDraw.arc(left + w/2, top + h/2, w/2, h/2, 0, 360); else gcDraw.rect(left, top, w, h);
        gcDraw.closePath(); gcDraw.clip();

        double zoomFactor = 2.0;
        double centerX = left + w/2; double centerY = top + h/2;
        Affine transform = new Affine();
        transform.appendTranslation(centerX, centerY); transform.appendScale(zoomFactor, zoomFactor); transform.appendTranslation(-centerX, -centerY);
        gcDraw.setTransform(transform);
        gcDraw.drawImage(img, imgX, imgY, imgW, imgH);
        gcDraw.restore();

        gcDraw.setLineWidth(1.0);
        gcDraw.setStroke(c);
        gcDraw.setEffect(new javafx.scene.effect.DropShadow(10, Color.BLACK));
        if (circle) {
            gcDraw.strokeOval(left, top, w, h);
            if(w < 150) {
                double cx = left + w*0.85; double cy = top + h*0.85; gcDraw.setLineWidth(6); gcDraw.strokeLine(cx, cy, cx+20, cy+20);
            }
        } else { gcDraw.strokeRect(left, top, w, h); }
        gcDraw.setEffect(null);
    }

    private void drawProSpotlight(double x1, double y1, double x2, double y2, Color c, double size) {
        double radiusTop = size * 1.5;
        double rxTop = radiusTop; double ryTop = radiusTop * 0.3;
        double radiusBottom = size * 3.0 + Math.abs(x2-x1)*0.2;
        double rxBot = radiusBottom; double ryBot = radiusBottom * 0.3;
        double xTopL = x1 - rxTop; double xTopR = x1 + rxTop;
        double xBotL = x2 - rxBot; double xBotR = x2 + rxBot;
        LinearGradient grad = new LinearGradient(x1, y1, x2, y2, false, CycleMethod.NO_CYCLE, new Stop(0, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.3)), new Stop(1, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.1)));
        gcDraw.setFill(grad); gcDraw.fillPolygon(new double[]{xTopL, xBotL, xBotR, xTopR}, new double[]{y1, y2, y2, y1}, 4);
        gcDraw.setStroke(c); gcDraw.setLineWidth(2); gcDraw.setLineDashes(null); gcDraw.strokeOval(x1 - rxTop, y1 - ryTop, rxTop * 2, ryTop * 2);
        gcDraw.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.4)); gcDraw.fillOval(x2 - rxBot, y2 - ryBot, rxBot * 2, ryBot * 2);
        gcDraw.setStroke(c.deriveColor(0, 1, 1, 0.5)); gcDraw.strokeOval(x2 - rxBot, y2 - ryBot, rxBot * 2, ryBot * 2);
    }

    private void drawPolyWall(List<Double> pts, Color c) {
        double wallHeight = 100.0; // Muro alto
        gcDraw.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.2));
        gcDraw.setStroke(c); gcDraw.setLineWidth(2);
        for (int i = 0; i < pts.size() - 2; i += 2) {
            double sx = pts.get(i);     double sy = pts.get(i+1);
            double ex = pts.get(i+2);   double ey = pts.get(i+3);
            double sxTop = sx; double syTop = sy - wallHeight;
            double exTop = ex; double eyTop = ey - wallHeight;
            // Cara
            gcDraw.fillPolygon(new double[]{sx, ex, exTop, sxTop}, new double[]{sy, ey, eyTop, syTop}, 4);
            // Bordes (sin cerrar arriba para efecto valla)
            gcDraw.strokeLine(sx, sy, ex, ey);
            gcDraw.strokeLine(sx, sy, sxTop, syTop);
            if (i == pts.size() - 4) gcDraw.strokeLine(ex, ey, exTop, eyTop);

            gcDraw.setLineWidth(0.5);
            gcDraw.strokeLine(sx, sy - wallHeight/2, ex, ey - wallHeight/2);
            gcDraw.setLineWidth(2);
        }
    }

    private void drawProArrow(double x1, double y1, double x2, double y2, Color color, double size, boolean dashed) {
        gcDraw.setStroke(color); gcDraw.setFill(color); gcDraw.setLineWidth(size / 3.0);
        if (dashed) gcDraw.setLineDashes(10, 10); else gcDraw.setLineDashes(null);
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double headLen = size * 1.5;
        double lineEndX = x2 - headLen * Math.cos(angle); double lineEndY = y2 - headLen * Math.sin(angle);
        gcDraw.strokeLine(x1, y1, lineEndX, lineEndY);
        gcDraw.setLineDashes(null); drawArrowHead(x2, y2, angle, size, color);
    }

    private void drawCurvedArrow(double x1, double y1, double cx, double cy, double x2, double y2, Color color, double size) {
        gcDraw.setStroke(color); gcDraw.setFill(color); gcDraw.setLineWidth(size / 3.0); gcDraw.setLineDashes(null);
        gcDraw.beginPath(); gcDraw.moveTo(x1, y1); gcDraw.quadraticCurveTo(cx, cy, x2, y2); gcDraw.stroke();
        double angle = Math.atan2(y2 - cy, x2 - cx);
        drawArrowHead(x2, y2, angle, size, color);
        gcDraw.fillOval(x1 - size/4, y1 - size/4, size/2, size/2);
    }

    private void drawArrowHead(double x, double y, double angle, double size, Color color) {
        double headLen = size * 1.5; double headWidth = size;
        double xBase = x - headLen * Math.cos(angle); double yBase = y - headLen * Math.sin(angle);
        double x3 = xBase + headWidth * Math.cos(angle - Math.PI/2); double y3 = yBase + headWidth * Math.sin(angle - Math.PI/2);
        double x4 = xBase + headWidth * Math.cos(angle + Math.PI/2); double y4 = yBase + headWidth * Math.sin(angle + Math.PI/2);
        gcDraw.setFill(color); gcDraw.fillPolygon(new double[]{x, x3, x4}, new double[]{y, y3, y4}, 3);
    }

    private void drawProBase(double x1, double y1, double x2, double y2, Color c, double size) {
        double radius = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        double rx = radius; double ry = radius * 0.4;
        gcDraw.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.3)); gcDraw.fillOval(x1 - rx, y1 - ry, rx * 2, ry * 2);
        gcDraw.setStroke(c); gcDraw.setLineWidth(3); gcDraw.setLineDashes(10, 5); gcDraw.strokeOval(x1 - rx, y1 - ry, rx * 2, ry * 2); gcDraw.setLineDashes(null);
    }

    private String toHex(Color c) { return String.format("#%02X%02X%02X", (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255)); }
    private void showFloatingInput(double x, double y) {
        if (floatingInput == null) return;

        // Posicionar donde hiciste clic
        floatingInput.setTranslateX(x - (drawCanvas.getWidth() / 2) + 100); // Ajuste de centrado relativo
        floatingInput.setTranslateY(y - (drawCanvas.getHeight() / 2));

        // Mostrar y dar foco para escribir
        floatingInput.setVisible(true);
        floatingInput.setText("");
        floatingInput.requestFocus();

        // Guardamos la posición exacta X,Y para usarla al confirmar
        floatingInput.setUserData(new double[]{x, y});
    }
    private void confirmFloatingText() {
        if (!floatingInput.isVisible()) return;
        double[] pos = (double[]) floatingInput.getUserData();
        String text = floatingInput.getText();
        Color c = colorPicker.getValue();
        if (!text.isEmpty()) {
            saveState();
            DrawingShape txtShape = new DrawingShape("t"+System.currentTimeMillis(),
                    "text", pos[0], pos[1], toHex(c));
            if (getCurrentSegment() != null) {
                txtShape.setClipId(getCurrentSegment().getId());
            }
            txtShape.setTextValue(text);
            shapes.add(txtShape);
            switchToCursorAndSelect(txtShape);
        }
        floatingInput.setVisible(false);
        redrawVideoCanvas();
    }

    private void setupFreezeTimer() {
        freezeTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!isPlayingFreeze) return;

                // Si acabamos de entrar o hemos hecho un seek, inicializamos el tick
                if (lastTimerTick == 0) {
                    lastTimerTick = now;
                    return;
                }

                // Solo avanzamos el tiempo si el botón de reproducción general está en modo "Play" (||)
                if (btnPlayPause.getText().equals("||")) {
                    double delta = (now - lastTimerTick) / 1_000_000_000.0;
                    currentTimelineTime += delta;
                }

                lastTimerTick = now;

                Platform.runLater(() -> {
                    updateTimeLabel();
                    redrawTimeline();
                    redrawVideoCanvas(); // Fundamental para ver las ediciones guardadas

                    // Si el tiempo avanzado nos saca del segmento azul, cerramos el modo freeze
                    VideoSegment current = getCurrentSegment();
                    if (current == null || !current.isFreezeFrame()) {
                        checkPlaybackJump();
                    }
                });
            }
        };
    }

    private void setupVideoEvents() {
        videoService.setOnProgressUpdate(percent -> {

            // ✅ SI ESTAMOS EN PAUSA TÁCTICA, IGNORAMOS EL VIDEO REAL
            if (isPlayingFreeze) return;

            if (!videoService.isPlaying()) return;

            if (totalOriginalDuration > 0) {
                currentRealVideoTime = (percent / 100.0) * totalOriginalDuration;

                // 2. ¿DÓNDE DEBERÍAMOS ESTAR SEGÚN EL TIMELINE?
                VideoSegment expectedSeg = getCurrentSegment();

                // 3. DETECCIÓN DE ENTRADA A PAUSA: Si el segmento bajo el cabezal es azul
                if (expectedSeg != null && expectedSeg.isFreezeFrame()) {
                    checkPlaybackJump(); // 🛑 Esto detendrá el vídeo y activará los dibujos
                    return;
                }

                if (expectedSeg != null && !expectedSeg.isFreezeFrame()) {
                    // Tiempo real ideal
                    double offsetInSegment = currentTimelineTime - expectedSeg.getStartTime();
                    double expectedRealTime = expectedSeg.getSourceStartTime() + offsetInSegment;

                    // 3. CALCULAR DESFASE
                    double drift = Math.abs(currentRealVideoTime - expectedRealTime);

                    // 4. CORRECCIÓN DE DESFASE
                    // Si el error es mayor a 0.25s, forzamos la sincronización
                    if (drift > 0.25) {
                        System.out.println("Corrección de desfase: " + drift + "s. Sincronizando...");
                        performSafeSeek((expectedRealTime / totalOriginalDuration) * 100.0);
                    } else {
                        // Todo bien, avanzamos el timeline suavemente
                        currentTimelineTime = expectedSeg.getStartTime() + (currentRealVideoTime - expectedSeg.getSourceStartTime());
                    }

                    // 5. SALTO AL ACABAR SEGMENTO
                    // Usamos un pequeño margen (-0.05) para asegurar el salto
                    if (currentTimelineTime >= expectedSeg.getEndTime() - 0.05) {
                        jumpToNextSegment(expectedSeg);
                    }

                } else if (expectedSeg == null) {
                    // Estamos en un hueco vacío -> Saltar al siguiente
                    jumpToNextSegment(null);
                }

                // Actualizar UI
                Platform.runLater(() -> {
                    updateTimeLabel();
                    redrawTimeline();
                    checkAutoScroll();
                    redrawVideoCanvas();
                });
            }
        });

        // Configuración de IA y Frames (se mantiene igual)
        videoService.setOnFrameCaptured(bufferedImage -> {
            if (!btnToggleAI.isSelected() || isProcessingAI.get()) return;
            isProcessingAI.set(true);
            aiExecutor.submit(() -> {
                try {
                    var results = aiService.detectPlayers(bufferedImage);
                    Platform.runLater(() -> {
                        this.currentDetections = results;
                        redrawVideoCanvas();
                        isProcessingAI.set(false);
                    });
                } catch (Exception e) { isProcessingAI.set(false); }
            });
        });
    }

// --- MÉTODOS AUXILIARES ---

    // Método centralizado para saltar de forma segura
    private void performSafeSeek(double percent) {
        // 1. Ponemos la venda: Ignorar updates durante 1 segundo (1000ms)
        // Esto da tiempo al video a moverse y estabilizarse
        ignoreUpdatesUntil = System.currentTimeMillis() + 1000;

        // 2. Ordenar el salto
        videoService.seek(percent);
    }

    private void jumpToNextSegment(VideoSegment currentSeg) {
        int nextIndex = -1;

        if (currentSeg != null) {
            nextIndex = segments.indexOf(currentSeg) + 1;
        } else {
            nextIndex = getNextSegmentIndexByTime();
        }

        if (nextIndex < segments.size()) {
            VideoSegment nextSeg = segments.get(nextIndex);

            System.out.println("Salto al segmento " + nextIndex);

            // Actualizamos lógica
            currentTimelineTime = nextSeg.getStartTime();

            // Forzamos video con seguridad
            double seekPercent = (nextSeg.getSourceStartTime() / totalOriginalDuration) * 100.0;
            performSafeSeek(seekPercent);

        } else {
            // Fin del video
            Platform.runLater(() -> {
                onPlayPause(); // Pausar
                currentTimelineTime = totalTimelineDuration;
                updateTimeLabel();
                redrawTimeline();
            });
        }
    }

    private void checkAutoScroll() { if (totalTimelineDuration <= 0) return; double w = timelineCanvas.getWidth(); double playheadPos = (currentTimelineTime * pixelsPerSecond) - timelineScroll.getValue(); if (playheadPos > w) { timelineScroll.setValue(timelineScroll.getValue() + playheadPos - w + 50); } else if (playheadPos < 0) { timelineScroll.setValue(timelineScroll.getValue() + playheadPos - 50); } }

    private void checkPlaybackJump() {
        VideoSegment currentSeg = getCurrentSegment();

        if (currentSeg != null) {
            if (currentSeg.isFreezeFrame()) {
                // --- ACTIVAR MODO CONGELADO ---
                if (!isPlayingFreeze) {
                    videoService.pause();
                    isPlayingFreeze = true;
                    lastTimerTick = 0; // Resetear para que el timer se sincronice con el tiempo real de la CPU
                    freezeTimer.start();
                }
            } else {
                // --- VOLVER A MODO VÍDEO ---
                if (isPlayingFreeze) {
                    freezeTimer.stop();
                    isPlayingFreeze = false;

                    // Si el botón está en modo Play (||), reanudamos la marcha real
                    if ("||".equals(btnPlayPause.getText())) {
                        videoService.play();
                    }
                }
            }
        }
    }

    @FXML public void onCaptureFrame() { if (segments.isEmpty()) return; if (videoService.isPlaying()) onPlayPause(); TextInputDialog dialog = new TextInputDialog("3"); dialog.setTitle("Capturar Frame"); dialog.setHeaderText("Congelar imagen"); dialog.setContentText("Duración en segundos:"); Optional<String> result = dialog.showAndWait(); if (result.isPresent()) { try { double duration = Double.parseDouble(result.get()); if (duration <= 0) return; insertFreezeFrame(duration); } catch (NumberFormatException e) {} } }
    private void insertFreezeFrame(double duration) { VideoSegment activeSeg = null; for (VideoSegment seg : segments) { if (currentTimelineTime >= seg.getStartTime() && currentTimelineTime < seg.getEndTime()) { activeSeg = seg; break; } } if (activeSeg != null) { double splitTimeTimeline = currentTimelineTime; double splitTimeSource = activeSeg.getSourceStartTime() + (currentTimelineTime - activeSeg.getStartTime()); double originalEndTimeTimeline = activeSeg.getEndTime(); double originalEndTimeSource = activeSeg.getSourceEndTime(); activeSeg.setEndTime(splitTimeTimeline); activeSeg.setSourceEndTime(splitTimeSource); VideoSegment freezeSeg = new VideoSegment(splitTimeTimeline, splitTimeTimeline + duration, splitTimeSource, splitTimeSource, "#00bcd4", true); String colorRight = activeSeg.getColor().equals("#3b82f6") ? "#10b981" : "#3b82f6"; VideoSegment rightSeg = new VideoSegment(splitTimeTimeline + duration, originalEndTimeTimeline + duration, splitTimeSource, originalEndTimeSource, colorRight, false); int idx = segments.indexOf(activeSeg); segments.add(idx + 1, freezeSeg); segments.add(idx + 2, rightSeg); for (int i = idx + 3; i < segments.size(); i++) { VideoSegment s = segments.get(i); s.setStartTime(s.getStartTime() + duration); s.setEndTime(s.getEndTime() + duration); } totalTimelineDuration += duration; updateScrollbarAndRedraw(); } }

    @FXML public void onCutVideo() {
        saveState();
        if (segments.isEmpty()) return;
        VideoSegment activeSegment = null;

        // Buscar segmento bajo el cabezal
        for (VideoSegment seg : segments) {
            if (currentTimelineTime >= seg.getStartTime() && currentTimelineTime < seg.getEndTime()) {
                activeSegment = seg; break;
            }
        }

        if (activeSegment != null) {
            if (activeSegment.isFreezeFrame()) return; // No cortar congelados

            double offset = currentTimelineTime - activeSegment.getStartTime();

            // Evitar cortes muy cerca de los bordes
            if (offset < 0.5 || (activeSegment.getEndTime() - currentTimelineTime) < 0.5) return;

            double oldSourceEnd = activeSegment.getSourceEndTime();
            double oldTimelineEnd = activeSegment.getEndTime();
            double cutPointSource = activeSegment.getSourceStartTime() + offset;

            // 1. ACORTAR EL PRIMER TROZO
            activeSegment.setEndTime(currentTimelineTime);
            activeSegment.setSourceEndTime(cutPointSource);

            // --- AQUÍ CREAMOS EL HUECO (GAP) ---
            double gap = 0.1; // 1 segundo de separación visual
            double newStartTime = currentTimelineTime + gap;
            double newDuration = oldTimelineEnd - currentTimelineTime;
            // -----------------------------------

            // 2. CREAR EL SEGUNDO TROZO
            // Cambiamos el color para distinguirlo
            String newColor = activeSegment.getColor().equals("#3b82f6") ? "#10b981" : "#3b82f6";

            VideoSegment newSegment = new VideoSegment(
                    newStartTime,
                    newStartTime + newDuration,
                    cutPointSource,
                    oldSourceEnd,
                    newColor,
                    false
            );

            if (videoView.getImage() != null) {
                newSegment.setThumbnail(videoView.getImage());
            }

            // Insertar
            int idx = segments.indexOf(activeSegment);
            segments.add(idx + 1, newSegment);

            // 3. EMPUJAR EL RESTO DE SEGMENTOS
            // Como hemos metido un hueco de 1s, todo lo que haya a la derecha debe moverse +1s
            for(int i = idx + 2; i < segments.size(); i++) {
                VideoSegment s = segments.get(i);
                s.setStartTime(s.getStartTime() + gap);
                s.setEndTime(s.getEndTime() + gap);
            }

            totalTimelineDuration += gap; // La duración total crece

            selectedSegment = newSegment;
            updateScrollbarAndRedraw();
        }
    }

    @FXML public void onDeleteSegment() {
        saveState();
        if (selectedSegment == null) selectSegmentUnderPlayhead();
        if (selectedSegment != null && segments.size() > 1) {
            int idx = segments.indexOf(selectedSegment);
            double delDur = selectedSegment.getDuration();
            segments.remove(selectedSegment);
            for (int i = idx; i < segments.size(); i++) {
                VideoSegment s = segments.get(i);
                s.setStartTime(s.getStartTime() - delDur);
                s.setEndTime(s.getEndTime() - delDur);
            }
            totalTimelineDuration -= delDur;
            selectedSegment = null;
            updateScrollbarAndRedraw();
            if (currentTimelineTime > totalTimelineDuration) currentTimelineTime = totalTimelineDuration - 0.1;
        }
    }
    private void selectSegmentUnderPlayhead() { for (VideoSegment seg : segments) { if (currentTimelineTime >= seg.getStartTime() && currentTimelineTime < seg.getEndTime()) { selectedSegment = seg; break; } } }
    private boolean calculateTimelineTimeFromReal() {
        if (isPlayingFreeze) return true; // Si es congelado, consideramos que estamos "bien"

        for (VideoSegment seg : segments) {
            // Verificamos si el tiempo REAL del video cae dentro del rango original de este segmento
            if (!seg.isFreezeFrame() &&
                    currentRealVideoTime >= seg.getSourceStartTime() - 0.1 && // Margen de tolerancia
                    currentRealVideoTime <= seg.getSourceEndTime() + 0.1) {

                double offset = currentRealVideoTime - seg.getSourceStartTime();
                currentTimelineTime = seg.getStartTime() + offset;
                return true; // ¡Encontrado! Estamos en zona segura
            }
        }
        return false; // ¡Peligro! Estamos en una zona recortada
    }

    private void redrawTimeline() {
        double w = timelineCanvas.getWidth();
        double h = timelineCanvas.getHeight();
        double scrollOffset = timelineScroll.getValue();

        // Márgenes
        double topMargin = 22;
        double barHeight = h - topMargin - 5;

        // 1. Limpiar fondo
        gcTimeline.clearRect(0, 0, w, h);
        gcTimeline.setFill(Color.web("#1a1a1a"));
        gcTimeline.fillRect(0, 0, w, h);

        // 2. DIBUJAR SEGMENTOS ESTÁTICOS
        for (int i = 0; i < segments.size(); i++) {
            VideoSegment seg = segments.get(i);

            // TRUCO: Si estamos arrastrando este segmento, NO lo dibujamos en su sitio original.
            // Esto crea el efecto de que lo hemos "levantado" de la pista.
            if (isDraggingTimeline && seg == segmentBeingDragged) continue;

            double startX = (seg.getStartTime() * pixelsPerSecond) - scrollOffset;
            double width = seg.getDuration() * pixelsPerSecond;

            // Solo dibujar si es visible en pantalla
            if (startX + width > 0 && startX < w) {
                // Opacidad 1.0 (totalmente visible)
                drawStyledClip(gcTimeline, seg, startX, topMargin, width, barHeight, 1.0);
            }
        }

        // 3. DIBUJAR EL "FANTASMA" (GHOST) SI ESTAMOS ARRASTRANDO
        if (isDraggingTimeline && segmentBeingDragged != null) {
            double ghostW = segmentBeingDragged.getDuration() * pixelsPerSecond;
            // Lo dibujamos centrado en el cursor (currentDragX es la variable que creamos antes)
            double ghostX = currentDragX - (ghostW / 2.0);

            // Dibujamos con opacidad 0.6 para que sea semitransparente
            drawStyledClip(gcTimeline, segmentBeingDragged, ghostX, topMargin - 5, ghostW, barHeight + 10, 0.6);
        }

        // 4. DIBUJAR REGLA Y CABEZAL (Método separado para limpieza)
        drawRulerAndPlayhead(w, h, scrollOffset);
    }

    private void drawStyledClip(GraphicsContext gc, VideoSegment seg, double x, double y, double w, double h, double alpha) {
        gc.save();

        // 1. Definir el color base con transparencia (si es fantasma)
        Color c = Color.web(seg.getColor());
        if (alpha < 1.0) {
            c = new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
        }

        // 2. DEFINIR LA FORMA (PATH) CON BORDES REDONDEADOS
        // Hacemos que parezcan cápsulas individuales
        double arcSize = 10; // Radio de la curva

        gc.beginPath();
        gc.moveTo(x + arcSize, y);
        gc.lineTo(x + w - arcSize, y);
        gc.arcTo(x + w, y, x + w, y + arcSize, arcSize);
        gc.lineTo(x + w, y + h - arcSize);
        gc.arcTo(x + w, y + h, x + w - arcSize, y + h, arcSize);
        gc.lineTo(x + arcSize, y + h);
        gc.arcTo(x, y + h, x, y + h - arcSize, arcSize);
        gc.lineTo(x, y + arcSize);
        gc.arcTo(x, y, x + arcSize, y, arcSize);
        gc.closePath();

        // 3. RECORTAR (CLIP): Todo lo que dibujemos ahora se quedará dentro de la forma redonda
        gc.save(); // Guardar estado antes del clip
        gc.clip();

        // 4. DIBUJAR CONTENIDO (Imágenes o Color Sólido)
        boolean hasImages = !seg.isFreezeFrame() && !filmstripMap.isEmpty();

        if (hasImages) {
            // --- TÚ LÓGICA DE FILMSTRIP (INTEGRADA) ---
            double step = 5.0;
            double startSource = seg.getSourceStartTime();
            double endSource = seg.getSourceEndTime();
            double firstSample = Math.floor(startSource / step) * step;

            for (double t = firstSample; t < endSource + step; t += step) {
                var entry = filmstripMap.floorEntry(t);
                if (entry != null) {
                    Image img = entry.getValue();
                    double timeOffset = entry.getKey() - startSource;
                    double pixelOffset = timeOffset * pixelsPerSecond;
                    double imgX = x + pixelOffset; // Nota: Usamos 'x' que viene por parámetro
                    double imgW = step * pixelsPerSecond;

                    gc.drawImage(img, imgX, y, imgW, h);
                }
            }
            // Tinte de color encima de las fotos
            gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.35 * alpha));
            gc.fillRect(x, y, w, h);
        } else {
            // Si no hay fotos (o es FreezeFrame), relleno sólido
            gc.setFill(c);
            gc.fillRect(x, y, w, h);
        }

        gc.restore(); // Quitamos el recorte (clip) para poder dibujar el borde externo

        // 5. DIBUJAR EL BORDE (STROKE)
        // Volvemos a trazar la ruta para el borde (porque el clip la consume)
        gc.beginPath();
        gc.moveTo(x + arcSize, y);
        gc.lineTo(x + w - arcSize, y);
        gc.arcTo(x + w, y, x + w, y + arcSize, arcSize);
        gc.lineTo(x + w, y + h - arcSize);
        gc.arcTo(x + w, y + h, x + w - arcSize, y + h, arcSize);
        gc.lineTo(x + arcSize, y + h);
        gc.arcTo(x, y + h, x, y + h - arcSize, arcSize);
        gc.lineTo(x, y + arcSize);
        gc.arcTo(x, y, x + arcSize, y, arcSize);
        gc.closePath();

        if (seg == selectedSegment && alpha == 1.0) {
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2);
        } else {
            gc.setStroke(Color.BLACK.deriveColor(0,0,0,0.3));
            gc.setLineWidth(1);
        }
        gc.stroke();

        // 6. TEXTO DE DURACIÓN (CORREGIDO SIN TOOLKIT)
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        if (w > 40) { // Solo si cabe
            String label = String.format("%.1fs", seg.getDuration());
            if (seg.isFreezeFrame()) label = "❄ " + label;

            // --- SOLUCIÓN AL ERROR DE TOOLKIT ---
            javafx.scene.text.Text tempText = new javafx.scene.text.Text(label);
            tempText.setFont(gc.getFont());
            double textWidth = tempText.getLayoutBounds().getWidth();
            // ------------------------------------

            // Centrar texto
            gc.fillText(label, x + (w - textWidth)/2, y + h/2 + 4);
        }

        gc.restore(); // Restaurar estado inicial del save() principal
    }

    private void drawRulerAndPlayhead(double w, double h, double scrollOffset) {
        // 5. REGLA DE TIEMPO (Copiado de tu código)
        int stepTime = (pixelsPerSecond > 80) ? 1 : (pixelsPerSecond > 40) ? 5 : (pixelsPerSecond > 20) ? 10 : 30;
        int startSec = ((int) (scrollOffset / pixelsPerSecond) / stepTime) * stepTime;
        int endSec = (int) ((scrollOffset + w) / pixelsPerSecond) + 1;

        gcTimeline.setStroke(Color.GRAY);
        gcTimeline.setLineWidth(1);

        for (int i = startSec; i <= endSec; i += stepTime) {
            double x = (i * pixelsPerSecond) - scrollOffset;

            // Rayita grande
            gcTimeline.strokeLine(x, 0, x, 15);

            gcTimeline.setFill(Color.GRAY);
            gcTimeline.setFont(Font.font("Arial", 10));
            gcTimeline.fillText(formatShortTime(i), x + 2, 12);

            // Rayitas pequeñas
            if (stepTime >= 5) {
                for (int j = 1; j < stepTime; j++) {
                    double sx = x + j * pixelsPerSecond;
                    if (sx - scrollOffset < w) {
                        gcTimeline.strokeLine(sx, 0, sx, 5);
                    }
                }
            }
        }

        // 6. CABEZAL DE REPRODUCCIÓN
        double phX = (currentTimelineTime * pixelsPerSecond) - scrollOffset;
        if (phX >= 0 && phX <= w) {
            gcTimeline.setStroke(Color.RED);
            gcTimeline.setLineWidth(2);
            gcTimeline.strokeLine(phX, 0, phX, h);

            gcTimeline.setFill(Color.RED);
            gcTimeline.fillPolygon(new double[]{phX - 7, phX + 7, phX}, new double[]{0, 0, 15}, 3);
        }
    }

    private void updateScrollbarAndRedraw() { if (totalTimelineDuration <= 0) return; double canvasW = timelineCanvas.getWidth(); double totalW = totalTimelineDuration * pixelsPerSecond; timelineScroll.setMax(Math.max(0, totalW - canvasW)); timelineScroll.setVisibleAmount((canvasW / totalW) * timelineScroll.getMax()); redrawTimeline(); }
    private void updateTimeLabel() { int m = (int) currentTimelineTime / 60; int s = (int) currentTimelineTime % 60; lblTime.setText(String.format("%02d:%02d", m, s) + " / " + String.format("%02d:%02d", (int)totalTimelineDuration/60, (int)totalTimelineDuration%60)); }
    private String formatShortTime(int totalSeconds) { int m = totalSeconds / 60; int s = totalSeconds % 60; return (m > 0) ? String.format("%d:%02d", m, s) : s + "s"; }
    private void handleTimelineInteraction(double x) { seekTimeline(x); selectedSegment=null; double t = (x+timelineScroll.getValue())/pixelsPerSecond; for(VideoSegment s:segments) if(t>=s.getStartTime() && t<s.getEndTime()){selectedSegment=s; break;} redrawTimeline(); }

    public void onImportVideo() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Video", "*.mp4", "*.mov"));
        File f = fc.showOpenDialog(container.getScene().getWindow());

        if (f != null) {
            // 1. MOSTRAR SPINNER
            if (loadingSpinner != null) loadingSpinner.setVisible(true);

            // Limpiar canvas visualmente mientras carga
            gcTimeline.clearRect(0,0, timelineCanvas.getWidth(), timelineCanvas.getHeight());

            try {
                videoService.loadVideo(f.getAbsolutePath());
                new Thread(() -> {
                    try {
                        System.out.println("Subiendo al servidor...");
                        // Llamamos al backend
                        this.serverVideoId = apiClient.uploadVideo(f);
                        System.out.println("✅ Video subido. ID Servidor: " + this.serverVideoId);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.WARNING);
                            alert.setTitle("Conexión");
                            alert.setHeaderText("Modo Offline");
                            alert.setContentText("No se pudo subir al servidor. La exportación no funcionará.");
                            alert.show();
                        });
                    }
                }).start();
            } catch (Exception e) {
                e.printStackTrace();
            }

            Task<TreeMap<Double, Image>> task = new Task<>() {
                @Override
                protected TreeMap<Double, Image> call() throws Exception {
                    // Intervalo de 5 segundos, altura de miniatura 100px (para aprovechar la nueva altura)
                    return new FilmstripService().generateFilmstrip(f, 5.0, 70);
                }
            };

            task.setOnSucceeded(event -> {
                // 2. OCULTAR SPINNER AL TERMINAR
                if (loadingSpinner != null) loadingSpinner.setVisible(false);

                this.filmstripMap = task.getValue();
                totalOriginalDuration = videoService.getTotalDuration();
                totalTimelineDuration = totalOriginalDuration;
                segments.clear();
                segments.add(new VideoSegment(0, totalTimelineDuration, 0, totalOriginalDuration,
                        "#3b82f6", false));
                // Resetear posición
                currentTimelineTime = 0;
                if (timelineScroll != null) {
                    timelineScroll.setValue(0);
                    timelineScroll.setMax(totalTimelineDuration * pixelsPerSecond);
                }
                updateScrollbarAndRedraw();
                videoService.pause();
                videoService.seek(0); // Asegura que se vea el primer frame
                // Sincronizar el botón visualmente (Icono Play)
                if (btnPlayPause != null) {
                    btnPlayPause.setText("▶"); // Asegurar que muestra el icono de "Darle Play"
                }
            });

            task.setOnFailed(e -> {
                if (loadingSpinner != null) loadingSpinner.setVisible(false);
                System.err.println("Error generando miniaturas");
            });

            new Thread(task).start();
        }
    }

    private void seekTimeline(double mouseX) {
        isSeeking = false;
        stickySegmentIndex = -1;
        double scrollOffset = timelineScroll.getValue();
        double time = (mouseX + scrollOffset) / pixelsPerSecond;

        if (time < 0) time = 0;
        if (time > totalTimelineDuration) time = totalTimelineDuration;
        currentTimelineTime = time;

        // 1. Buscamos el segmento correspondiente y posicionamos el vídeo directamente
        for (VideoSegment seg : segments) {
            if (time >= seg.getStartTime() && time <= seg.getEndTime()) {
                // Si es freeze frame, vamos al punto de origen; si es video, calculamos el offset
                double seekTarget = (seg.isFreezeFrame())
                        ? seg.getSourceStartTime()
                        : seg.getSourceStartTime() + (time - seg.getStartTime());

                videoService.seek((seekTarget / totalOriginalDuration) * 100.0);
                break; // Salimos del bucle en cuanto lo encontramos
            }
        }

        checkPlaybackJump();

        redrawTimeline();
        redrawVideoCanvas();
    }

    @FXML public void onPlayPause() { if (videoService.isPlaying() || isPlayingFreeze) { videoService.pause(); if (isPlayingFreeze) { freezeTimer.stop(); isPlayingFreeze = false; } btnPlayPause.setText("▶"); isProcessingAI.set(false); } else { checkPlaybackJump(); if (!isPlayingFreeze) { videoService.play(); } btnPlayPause.setText("||"); } }
    @FXML
    public void onSkipToStart() {
        currentTimelineTime = 0;
        if (timelineScroll != null) timelineScroll.setValue(0);
        if (videoService != null) videoService.seek(0);
        redrawTimeline();
        updateTimeLabel();
    }

    @FXML
    public void onSkipToEnd() {
        if (totalTimelineDuration > 0) {
            currentTimelineTime = totalTimelineDuration;
            if (timelineScroll != null) timelineScroll.setValue(timelineScroll.getMax());
            if (videoService != null) {
                if (videoService.isPlaying()) onPlayPause(); // Pausar al llegar al final
                videoService.seek(100);
            }
            redrawTimeline();
            updateTimeLabel();
        }
    }

    private VideoSegment getCurrentSegment() {
        double epsilon = 0.05; // 50 milisegundos de margen de error
        for (VideoSegment seg : segments) {
            // Comprobamos con un poquito de margen por si el clic no fue micrométrico
            if (currentTimelineTime >= seg.getStartTime() - epsilon &&
                    currentTimelineTime < seg.getEndTime() - epsilon) {
                return seg;
            }
        }
        return null;
    }

    private void recalculateSegmentTimes() {
        double currentTime = 0.0;

        for (VideoSegment seg : segments) {
            double duration = seg.getDuration(); // La duración se mantiene (SourceEnd - SourceStart)

            seg.setStartTime(currentTime);
            seg.setEndTime(currentTime + duration);

            currentTime += duration;
        }

        totalTimelineDuration = currentTime;
        updateScrollbarAndRedraw();
    }

    private int getNextSegmentIndexByTime() {
        // Recorre todos los segmentos para encontrar cuál es el primero
        // que empieza después del tiempo actual (para saber a dónde saltar).
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).getStartTime() > currentTimelineTime) {
                return i;
            }
        }
        // Si no encuentra ninguno (estamos al final), devuelve el tamaño
        // para indicar que ya no hay más videos.
        return segments.size();
    }

    // LLAMA A ESTE MÉTODO ANTES DE HACER CUALQUIER CAMBIO (Cortar, Mover, Dibujar, Borrar)
    private void saveState() {
        if (segments == null) return;
        undoStack.push(new EditorState(segments, shapes, totalTimelineDuration));
        redoStack.clear(); // Limpiar el futuro
        updateUndoRedoButtons();
    }

    private void updateUndoRedoButtons() {
        if (btnUndo != null) btnUndo.setDisable(undoStack.isEmpty());
        if (btnRedo != null) btnRedo.setDisable(redoStack.isEmpty());
    }

    @FXML public void onUndo() {
        if (undoStack.isEmpty()) return;

        // 1. Guardar estado actual en Redo antes de volver atrás
        redoStack.push(new EditorState(segments, shapes, totalTimelineDuration));

        // 2. Recuperar el pasado
        restoreState(undoStack.pop());
        updateUndoRedoButtons();
    }

    @FXML public void onRedo() {
        if (redoStack.isEmpty()) return;

        // 1. Guardar estado actual en Undo antes de volver al futuro
        undoStack.push(new EditorState(segments, shapes, totalTimelineDuration));

        // 2. Recuperar el futuro
        restoreState(redoStack.pop());
        updateUndoRedoButtons();
    }

    private void restoreState(EditorState state) {
        this.segments = new ArrayList<>(state.segmentsSnapshot);
        this.shapes = new ArrayList<>(state.shapesSnapshot);
        this.totalTimelineDuration = state.durationSnapshot;

        // Forzar actualización total
        currentTimelineTime = 0; // Opcional: volver al inicio o intentar mantener posición
        updateScrollbarAndRedraw();
        redrawVideoCanvas();
    }

    // =========================================================================
    //                           EXPORTACIÓN (NUEVO)
    // =========================================================================

    @FXML
    public void onExportVideo() {
        if (serverVideoId == null) {
            mostrarAlerta("Error", "Sube el video primero.");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setInitialFileName("analisis_tactico.mp4");
        File destFile = fc.showSaveDialog(container.getScene().getWindow());
        if (destFile == null) return;

        // Mostrar UI de progreso
        if (loadingSpinner != null) loadingSpinner.setVisible(true);
        if (exportProgressBar != null) {
            exportProgressBar.setVisible(true);
            exportProgressBar.setProgress(0);
        }

        new Thread(() -> {
            try {
                List<ExportItem> items = new ArrayList<>();
                int totalSegments = segments.size();

                // --- FASE 1: GENERACIÓN DE SNAPSHOTS LOCALES (0% a 50%) ---
                for (int i = 0; i < totalSegments; i++) {
                    final int currentIdx = i + 1;
                    final double progress = (double) currentIdx / totalSegments * 0.5; // Escala al 50%

                    Platform.runLater(() -> {
                        if (lblStatus != null) lblStatus.setText("Procesando segmento " + currentIdx + " de " + totalSegments);
                        if (exportProgressBar != null) exportProgressBar.setProgress(progress);
                    });

                    VideoSegment seg = segments.get(i);
                    if (seg.isFreezeFrame()) {
                        // Generamos la imagen con dibujos usando el hilo de UI
                        java.util.concurrent.FutureTask<String> task = new java.util.concurrent.FutureTask<>(() -> generateSnapshotForSegment(seg));
                        Platform.runLater(task);
                        String base64 = task.get();
                        items.add(ExportItem.image(base64, seg.getDuration()));
                    } else {
                        items.add(ExportItem.video(seg.getSourceStartTime(), seg.getSourceEndTime()));
                    }
                }

                // --- FASE 2: PROCESAMIENTO EN SERVIDOR (50% a 90%) ---
                Platform.runLater(() -> {
                    if (lblStatus != null) lblStatus.setText("Enviando al servidor para montaje final...");
                    if (exportProgressBar != null) exportProgressBar.setProgress(0.7);
                });

                ExportRequest req = new ExportRequest(serverVideoId, items);
                byte[] videoBytes = apiClient.exportVideo(req); // Llamada bloqueante a la API

                // --- FASE 3: ESCRITURA EN DISCO (90% a 100%) ---
                Platform.runLater(() -> {
                    if (lblStatus != null) lblStatus.setText("Guardando archivo final...");
                    if (exportProgressBar != null) exportProgressBar.setProgress(0.95);
                });

                Files.write(destFile.toPath(), videoBytes);

                Platform.runLater(() -> {
                    resetExportUI();
                    mostrarAlerta("Éxito", "Vídeo exportado correctamente en: " + destFile.getName());
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    resetExportUI();
                    mostrarAlerta("Error", "Fallo en la exportación: " + e.getMessage());
                });
            }
        }).start();
    }

    private void resetExportUI() {
        if (loadingSpinner != null) loadingSpinner.setVisible(false);
        if (exportProgressBar != null) {
            exportProgressBar.setVisible(false);
            exportProgressBar.setProgress(0);
        }
        if (lblStatus != null) lblStatus.setText("");
    }

    private void exportCutsWithFFmpeg(File destFile) throws Exception {
        // NOTA: Asumimos que el usuario tiene 'ffmpeg' instalado y en el PATH del sistema.
        // Si no, habría que poner la ruta completa ej: "C:/ffmpeg/bin/ffmpeg.exe"
        String ffmpegPath = "ffmpeg";

        // Obtenemos la ruta del video original (asumiendo que todos los segmentos vienen del mismo)
        // Si tienes múltiples videos, la lógica sería más compleja.
        String sourcePath = videoService.getSourcePath();
        if (sourcePath == null) throw new Exception("No se encuentra el video origen.");

        // Construir el 'filter_complex' para concatenar cortes
        StringBuilder filter = new StringBuilder();
        int segCount = 0;

        for (int i = 0; i < segments.size(); i++) {
            VideoSegment seg = segments.get(i);

            // Ignoramos frames congelados en esta exportación básica
            // (Exportar freeze-frames requiere filtros 'loop' o 'tpad' complejos)
            if (seg.isFreezeFrame()) continue;

            // Cálculo de tiempos
            double start = seg.getSourceStartTime();
            double end = seg.getSourceEndTime();
            double duration = end - start;

            // Evitar segmentos nulos
            if (duration <= 0.01) continue;

            // [0:v]trim=start=10:duration=5,setpts=PTS-STARTPTS[v0];
            filter.append(String.format("[0:v]trim=start=%.3f:duration=%.3f,setpts=PTS-STARTPTS[v%d];",
                    start, duration, segCount));

            // Si tuvieras audio, habría que añadir: [0:a]atrim=start=...[a0];

            segCount++;
        }

        if (segCount == 0) throw new Exception("No hay segmentos válidos para exportar.");

        // Concatenación: [v0][v1]...concat=n=X:v=1[outv]
        for (int i = 0; i < segCount; i++) {
            filter.append("[v").append(i).append("]");
        }
        filter.append("concat=n=").append(segCount).append(":v=1:a=0[outv]");

        // Construir comando final
        // ffmpeg -i input.mp4 -filter_complex "..." -map "[outv]" output.mp4
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegPath);
        cmd.add("-y"); // Sobrescribir
        cmd.add("-i");
        cmd.add(sourcePath);
        cmd.add("-filter_complex");
        cmd.add(filter.toString());
        cmd.add("-map");
        cmd.add("[outv]");
        cmd.add("-c:v");
        cmd.add("libx264"); // Codec estándar
        cmd.add("-preset");
        cmd.add("fast");
        cmd.add(destFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true); // Ver logs de ffmpeg en consola Java
        Process p = pb.start();

        // Leer salida para debug
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[FFmpeg] " + line);
            }
        }

        int exitCode = p.waitFor();
        if (exitCode != 0) throw new Exception("FFmpeg terminó con error " + exitCode);
    }

    private void mostrarAlerta(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    private void setupTooltips() {
        // Edición
        addTooltip(btnUndo, "Deshacer");
        addTooltip(btnRedo, "Rehacer");

        // Herramientas Básicas
        addTooltip(btnCursor, "Cursor - Seleccionar y mover ediciones");
        addTooltip(btnPen, "Lápiz - Dibujo libre a mano alzada");
        addTooltip(btnText, "Texto - Añadir etiquetas de texto (Clic + Escribir + Enter)");

        // Flechas
        addTooltip(btnArrow, "Flecha Simple");
        addTooltip(btnArrowDashed, "Flecha Discontinua (Movimiento de balón/jugador)");
        addTooltip(btnArrow3D, "Flecha Curva 3D");

        // Objetos
        addTooltip(btnSpotlight, "Foco (Spotlight) - Resaltar zona");
        addTooltip(btnBase, "Base - Círculo de jugador");
        addTooltip(btnWall, "Muro - Pared defensiva 3D");

        // Geometría
        addTooltip(btnRectangle, "Rectángulo (Hueco)");
        addTooltip(btnRectShaded, "Zona (Rectángulo relleno)");
        addTooltip(btnPolygon, "Polígono - Área libre (Clic para puntos, Doble clic para cerrar)");
        addTooltip(btnCurve, "Curva - Línea curva con punto de control");

        // Zoom
        addTooltip(btnZoomCircle, "Lupa Circular - Ampliar detalle");
        addTooltip(btnZoomRect, "Lupa Rectangular - Ampliar zona");

        // Video
        addTooltip(btnSkipStart, "Ir al inicio");
        addTooltip(btnSkipEnd, "Ir al final");
    }

    // Método auxiliar para crear el tooltip con estilo
    private void addTooltip(Control node, String text) {
        if (node != null) {
            Tooltip t = new Tooltip(text);
            // Hacemos que aparezca rápido (200ms) en lugar de esperar el segundo por defecto
            t.setShowDelay(javafx.util.Duration.millis(200));
            // Estilo opcional para que se vea moderno
            t.setStyle("-fx-font-size: 12px; -fx-background-color: #333; -fx-text-fill: white;");
            node.setTooltip(t);
        }
    }

    private String generateSnapshotForSegment(VideoSegment seg) {
        try {
            double exportW = 1280;
            double exportH = 720;
            Canvas tempCanvas = new Canvas(exportW, exportH);
            GraphicsContext gc = tempCanvas.getGraphicsContext2D();

            Image bg = seg.getThumbnail();
            if (bg == null) return null;

            // 1. Dibujamos el fondo ocupando todo el canvas de exportación (1280x720)
            gc.drawImage(bg, 0, 0, exportW, exportH);

            // 2. RECUPERAR PARÁMETROS DE ESCALA DEL MOMENTO DEL DIBUJO
            // Usamos los valores guardados en el request (originalWidth/Height)
            double canvasW = drawCanvas.getWidth();
            double canvasH = drawCanvas.getHeight();

            // Calculamos cómo se veía el video en tu pantalla
            double scale = Math.min(canvasW / bg.getWidth(), canvasH / bg.getHeight());
            double vidDispW = bg.getWidth() * scale;
            double vidDispH = bg.getHeight() * scale;
            double offX = (canvasW - vidDispW) / 2.0;
            double offY = (canvasH - vidDispH) / 2.0;

            List<DrawingShape> cachedShapes = annotationsCache.get(seg.getId());

            if (cachedShapes != null) {
                for (DrawingShape s : cachedShapes) {
                    // ✅ PASO CLAVE: Nueva lógica de escalado relativo al video, no al canvas
                    drawShapeScaledToVideo(gc, s, offX, offY, vidDispW, vidDispH, exportW, exportH, bg);
                }
            }

            // ... resto del código de conversión a Base64 ...
            WritableImage snap = tempCanvas.snapshot(null, null);
            java.awt.image.BufferedImage bImage = SwingFXUtils.fromFXImage(snap, null);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bImage, "png", out);
            return java.util.Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) { return null; }
    }

    private void drawShapeScaledToVideo(GraphicsContext gc, DrawingShape s,
                                        double offX, double offY,
                                        double vidDispW, double vidDispH,
                                        double exportW, double exportH, Image bg) {

        // 1. Factores de escala (Relación entre lo que ves y el vídeo real)
        double sx = exportW / vidDispW;
        double sy = exportH / vidDispH;

        // 2. Mapeo de coordenadas principales (Restamos margen y escalamos)
        double x1 = (s.getStartX() - offX) * sx;
        double y1 = (s.getStartY() - offY) * sy;
        double x2 = (s.getEndX() - offX) * sx;
        double y2 = (s.getEndY() - offY) * sy;

        // 3. Escalado del grosor (Usamos sx para mantener la proporción)
        double size = s.getStrokeWidth() * sx;

        // 4. Llamamos al dibujante enviando los márgenes para ajustar puntos internos
        drawShapeOnExternalGC(gc, s, bg, x1, y1, x2, y2, size, sx, sy, offX, offY);
    }

    @FXML
    public void onSaveFrame() {
        if (serverVideoId == null) {
            mostrarAlerta("Error", "Sube el video primero.");
            return;
        }

        double currentTime = videoService.getCurrentTime();
        List<DrawingShape> currentShapes = new ArrayList<>(shapes);

        if (currentShapes.isEmpty()) {
            mostrarAlerta("Aviso", "Dibuja algo antes de guardar.");
            return;
        }

        VideoSegment activeSeg = getCurrentSegment();
        final boolean alreadyHasSegment = (activeSeg != null && activeSeg.isFreezeFrame());

        final String segmentIdToUse = alreadyHasSegment ? activeSeg.getId() : UUID.randomUUID().toString();

        if (loadingSpinner != null) loadingSpinner.setVisible(true);

        new Thread(() -> {
            try {
                String frameId = apiClient.captureFrame(serverVideoId, currentTime);

                SaveFrameRequest saveReq = new SaveFrameRequest();
                saveReq.setFrameId(frameId);
                saveReq.setPausePointId(segmentIdToUse);
                saveReq.setDuration(3.0);
                saveReq.setAnnotationsJson(new Gson().toJson(currentShapes));
                saveReq.setOriginalWidth((int) drawCanvas.getWidth());
                saveReq.setOriginalHeight((int) drawCanvas.getHeight());

                apiClient.saveAnnotation(saveReq);

                Platform.runLater(() -> {
                    Image rawVideoImage = videoView.getImage();

                    if (!alreadyHasSegment) {
                        double duration = 3.0;
                        VideoSegment freezeSeg = new VideoSegment(
                                currentTimelineTime,
                                currentTimelineTime + duration,
                                currentTime,
                                currentTime,
                                "#00bcd4",
                                true
                        );

                        // Asignar la imagen pura al segmento
                        freezeSeg.setThumbnail(rawVideoImage);

                        int index = segments.indexOf(activeSeg);
                        if (index != -1) {
                            segments.add(index + 1, freezeSeg);
                        } else {
                            segments.add(freezeSeg);
                        }
                        totalTimelineDuration += duration;
                    } else {
                        activeSeg.setThumbnail(rawVideoImage);
                    }

                    annotationsCache.put(segmentIdToUse, new ArrayList<>(currentShapes));

                    for(DrawingShape s : currentShapes) {
                        s.setClipId(segmentIdToUse);
                    }

                    shapes.clear();
                    redrawVideoCanvas();
                    updateScrollbarAndRedraw();

                    if (loadingSpinner != null) loadingSpinner.setVisible(false);
                    mostrarAlerta("Éxito", "Análisis guardado correctamente.");
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    if (loadingSpinner != null) loadingSpinner.setVisible(false);
                });
            }
        }).start();
    }

    private void drawShapeOnExternalGC(GraphicsContext gc, DrawingShape s, Image backgroundImage,
                                       double x1, double y1, double x2, double y2,
                                       double size, double sx, double sy, double offX, double offY) {

        Color c = Color.web(s.getColor());
        gc.setStroke(c);
        gc.setFill(c);
        gc.setLineWidth(size);
        gc.setLineCap(StrokeLineCap.ROUND);

        switch (s.getType()) {
            case "arrow":
                drawProArrowOnGC(gc, x1, y1, x2, y2, c, size, false);
                break;
            case "arrow-dashed":
                drawProArrowOnGC(gc, x1, y1, x2, y2, c, size, true);
                break;
            case "arrow-3d":
                double cx3d = (x1 + x2) / 2;
                double cy3d = Math.min(y1, y2) - Math.abs(x2 - x1) * 0.3;
                drawCurvedArrowOnGC(gc, x1, y1, cx3d, cy3d, x2, y2, c, size);
                break;
            case "pen":
                gc.setLineWidth(size);
                if (s.getPoints() != null && s.getPoints().size() > 2) {
                    gc.beginPath();
                    // Ajustamos cada punto: (Coordenada - Margen) * Escala
                    gc.moveTo((s.getPoints().get(0) - offX) * sx, (s.getPoints().get(1) - offY) * sy);
                    for (int i = 2; i < s.getPoints().size(); i += 2) {
                        gc.lineTo((s.getPoints().get(i) - offX) * sx, (s.getPoints().get(i + 1) - offY) * sy);
                    }
                    gc.stroke();
                }
                break;
            case "wall":
                gc.setLineWidth(2.0 * sx);
                drawSimpleWallOnGC(gc, x1, y1, x2, y2, c, 80.0 * sy);
                break;
            case "base":
                gc.setLineWidth(2.0 * sx);
                drawProBaseOnGC(gc, x1, y1, x2, y2, c, size);
                break;
            case "spotlight":
                gc.setLineWidth(2.0 * sx);
                drawProSpotlightOnGC(gc, x1, y1, x2, y2, c, size);
                break;
            case "polygon":
                if (s.getPoints() != null && s.getPoints().size() >= 4) {
                    gc.setLineWidth(2.0 * sx);
                    List<Double> scaledPts = new ArrayList<>();
                    for (int i = 0; i < s.getPoints().size(); i += 2) {
                        scaledPts.add((s.getPoints().get(i) - offX) * sx);
                        scaledPts.add((s.getPoints().get(i + 1) - offY) * sy);
                    }
                    drawFilledPolygonOnGC(gc, scaledPts, c);
                }
                break;
            case "rect-shaded":
                gc.setLineWidth(2.0 * sx);
                drawShadedRectOnGC(gc, x1, y1, x2, y2, c);
                break;
            case "rectangle":
                gc.setLineWidth(size);
                gc.strokeRect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
                break;
            case "text":
                if (s.getTextValue() != null) {
                    double fontSize = size * 2.5;
                    gc.setFont(Font.font("Arial", FontWeight.BOLD, fontSize));
                    gc.setStroke(Color.BLACK);
                    gc.setLineWidth(1.5 * sx);
                    gc.strokeText(s.getTextValue(), x1, y1);
                    gc.setFill(c);
                    gc.fillText(s.getTextValue(), x1, y1);
                }
                break;
            case "curve":
                if (s.getPoints() != null && !s.getPoints().isEmpty()) {
                    double ctrlX = (s.getPoints().get(0) - offX) * sx;
                    double ctrlY = (s.getPoints().get(1) - offY) * sy;
                    double angle = Math.atan2(y2 - ctrlY, x2 - ctrlX);
                    double arrowLen = size * 1.5;
                    gc.beginPath();
                    gc.moveTo(x1, y1);
                    gc.quadraticCurveTo(ctrlX, ctrlY, x2 - arrowLen * Math.cos(angle), y2 - arrowLen * Math.sin(angle));
                    gc.stroke();
                    drawArrowHeadOnGC(gc, x2, y2, angle, size, c);
                }
                break;
            case "zoom-circle":
                drawRealZoomOnGC(gc, x1, y1, x2, y2, c, backgroundImage, sx, sy, true);
                break;
            case "zoom-rect":
                drawRealZoomOnGC(gc, x1, y1, x2, y2, c, backgroundImage, sx, sy, false);
                break;
        }
    }

    private void drawProArrowOnGC(GraphicsContext gc, double x1, double y1, double x2, double y2, Color color, double size, boolean dashed) {
        // ✅ LA CLAVE: El grosor de la línea debe ser reducido
        gc.setLineWidth(size / 3.0);

        if (dashed) gc.setLineDashes(10 * size/20.0, 10 * size/20.0);
        else gc.setLineDashes(null);

        double angle = Math.atan2(y2 - y1, x2 - x1);
        double headLen = size * 1.5;

        // Dibujar el cuerpo de la flecha
        gc.strokeLine(x1, y1, x2 - (headLen * 0.8 * Math.cos(angle)), y2 - (headLen * 0.8 * Math.sin(angle)));

        gc.setLineDashes(null);
        drawArrowHeadOnGC(gc, x2, y2, angle, size, color);
    }

    private void drawArrowHeadOnGC(GraphicsContext gc, double x, double y, double angle, double size, Color color) {
        double headLen = size * 1.5; double headWidth = size;
        double xBase = x - headLen * Math.cos(angle); double yBase = y - headLen * Math.sin(angle);
        double x3 = xBase + headWidth * Math.cos(angle - Math.PI/2); double y3 = yBase + headWidth * Math.sin(angle - Math.PI/2);
        double x4 = xBase + headWidth * Math.cos(angle + Math.PI/2); double y4 = yBase + headWidth * Math.sin(angle + Math.PI/2);
        gc.setFill(color);
        gc.fillPolygon(new double[]{x, x3, x4}, new double[]{y, y3, y4}, 3);
    }

    private void drawCurvedArrowOnGC(GraphicsContext gc, double x1, double y1, double cx, double cy, double x2, double y2, Color color, double size) {
        gc.beginPath(); gc.moveTo(x1, y1); gc.quadraticCurveTo(cx, cy, x2, y2); gc.stroke();
        double angle = Math.atan2(y2 - cy, x2 - cx);
        drawArrowHeadOnGC(gc, x2, y2, angle, size, color);
    }

    private void drawSimpleWallOnGC(GraphicsContext gc, double x1, double y1, double x2, double y2, Color c, double wallHeight) {
        gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.3));
        gc.fillPolygon(new double[]{x1, x2, x2, x1}, new double[]{y1, y2, y2 - wallHeight, y1 - wallHeight}, 4);
        gc.setStroke(c);
        gc.strokeLine(x1, y1, x2, y2);
        gc.strokeLine(x1, y1 - wallHeight, x2, y2 - wallHeight);
        gc.strokeLine(x1, y1, x1, y1 - wallHeight);
        gc.strokeLine(x2, y2, x2, y2 - wallHeight);
    }

    private void drawFilledPolygonOnGC(GraphicsContext gc, List<Double> pts, Color c) {
        int n = pts.size() / 2;
        double[] xs = new double[n]; double[] ys = new double[n];
        for (int i = 0; i < n; i++) { xs[i] = pts.get(i*2); ys[i] = pts.get(i*2+1); }
        gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.4));
        gc.fillPolygon(xs, ys, n);
        gc.strokePolygon(xs, ys, n);
    }

    private void drawShadedRectOnGC(GraphicsContext gc, double x1, double y1, double x2, double y2, Color c) {
        double l = Math.min(x1, x2); double t = Math.min(y1, y2);
        double w = Math.abs(x2-x1); double h = Math.abs(y2-y1);
        gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.3));
        gc.fillRect(l, t, w, h);
        gc.strokeRect(l, t, w, h);
    }

    private void drawProSpotlightOnGC(GraphicsContext gc, double x1, double y1, double x2, double y2, Color c, double size) {
        double rxTop = size * 1.5; double ryTop = rxTop * 0.3;
        double rxBot = size * 3.0 + Math.abs(x2-x1)*0.2; double ryBot = rxBot * 0.3;
        gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.2));
        gc.fillPolygon(new double[]{x1-rxTop, x2-rxBot, x2+rxBot, x1+rxTop}, new double[]{y1, y2, y2, y1}, 4);
        gc.strokeOval(x1-rxTop, y1-ryTop, rxTop*2, ryTop*2);
        gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.4));
        gc.fillOval(x2-rxBot, y2-ryBot, rxBot*2, ryBot*2);
    }

    private void drawProBaseOnGC(GraphicsContext gc, double x1, double y1, double x2, double y2, Color c, double size) {
        double radius = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.3));
        gc.fillOval(x1 - radius, y1 - radius * 0.4, radius * 2, radius * 0.8);
        gc.strokeOval(x1 - radius, y1 - radius * 0.4, radius * 2, radius * 0.8);
    }

    private void drawRealZoomOnGC(GraphicsContext gc, double x1, double y1, double x2, double y2,
                                  Color c, Image img, double sx, double sy, boolean circle) {
        double w = Math.abs(x2 - x1);
        double h = Math.abs(y2 - y1);
        double left = Math.min(x1, x2);
        double top = Math.min(y1, y2);

        if (img == null) return;

        // Dimensiones de exportación (deben ser las mismas que en generateSnapshotForSegment)
        double exportW = 1280;
        double exportH = 720;

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

        // ✅ CORRECCIÓN: Dibujar la imagen de fondo ajustada EXACTAMENTE al tamaño de exportación
        // Esto asegura que el centro del zoom coincida con el punto de la pantalla
        gc.drawImage(img, 0, 0, exportW, exportH);

        gc.restore();

        gc.setStroke(c);
        gc.setLineWidth(2.0 * sx); // Borde proporcional
        if (circle) gc.strokeOval(left, top, w, h);
        else gc.strokeRect(left, top, w, h);
    }

}

