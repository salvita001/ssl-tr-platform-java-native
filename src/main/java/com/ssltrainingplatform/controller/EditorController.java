package com.ssltrainingplatform.controller;

import com.google.gson.Gson;
import com.ssltrainingplatform.SslTpApp;
import com.ssltrainingplatform.dto.ExportItem;
import com.ssltrainingplatform.dto.ExportRequest;
import com.ssltrainingplatform.dto.SaveFrameRequest;
import com.ssltrainingplatform.model.EditorState;
import com.ssltrainingplatform.model.VideoSegment;
import com.ssltrainingplatform.service.*;
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
import javafx.scene.layout.VBox;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
        POLYGON, RECT_SHADED, ZOOM_CIRCLE, ZOOM_RECT, TRACKING
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

    @FXML private ProgressBar exportProgressBar;
    @FXML private Label lblStatus;

    private int dropIndex = -1;
    private double dropIndicatorX = -1;

    private ContextMenu timelineContextMenu;

    private long lastScrubTime = 0;

    // 2. Variables de estado de seguimiento
    private DetectedObjects.DetectedObject trackedObject = null;
    private DrawingShape trackingShape = null;

    @FXML private ToggleButton btnTracking;

    private AtomicInteger frameCounter = new AtomicInteger(0);

    @FXML public void setThicknessSmall() { updateSize(5); }
    @FXML public void setThicknessMed() { updateSize(15); }
    @FXML public void setThicknessLarge() { updateSize(30); }
    @FXML public void setThicknessExtra() { updateSize(50); }

    @FXML private VBox placeholderView;
    @FXML private Region iconPlaceholder;

    private double currentStrokeWidth = 15.0;

    @FXML private ToggleButton btnDeleteShape;
    @FXML private ToggleButton btnClearCanvas;

    private double hoverTime = -1;

    private String localVideoPath = null;

    // =========================================================================
    //                               INICIALIZACIÓN
    // =========================================================================

    public void initialize() {
        if (btnToggleAI == null) {
            btnToggleAI = new ToggleButton();
        }
        videoService = new VideoService(videoView);
        aiService = new AIService();
        try {
            aiService.loadModel("models/yolo11l.torchscript");
        } catch (Exception ignored) {}

        applyIcons();

        setupTooltips();

        initTimelineContextMenu();

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
            sizeSlider.setValue(15);
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

        container.setOnKeyPressed(e -> {
            // --- ATAJOS DE HISTORIAL ---
            if (e.isControlDown() || e.isShortcutDown()) { // Detecta Ctrl (Win) o Cmd (Mac)
                if (e.getCode() == KeyCode.Z) {
                    onUndo();
                    e.consume();
                } else if (e.getCode() == KeyCode.Y) {
                    onRedo();
                    e.consume();
                }
            }

            // --- OTROS ATAJOS EXISTENTES ---
            if (e.getCode() == KeyCode.ESCAPE) {
                if (currentShape != null && (currentTool == ToolType.POLYGON || currentTool == ToolType.WALL)) {
                    finishPolyShape();
                } else {
                    setToolCursor();
                    selectedSegment = null;
                    selectedShapeToMove = null;
                    redrawVideoCanvas();
                    redrawTimeline();
                }
            }

            if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
                if (selectedShapeToMove != null) {
                    saveState(); // Guardar antes de borrar forma
                    shapes.remove(selectedShapeToMove);
                    selectedShapeToMove = null;
                    redrawVideoCanvas();
                } else if (selectedSegment != null) {
                    onDeleteSegment(); // Este ya tiene saveState() dentro
                }
            }
        });

        // --- DENTRO DE initialize() en EditorController.java ---

        timelineCanvas.setOnScroll(event -> {
            // 1. Detectar movimiento lateral (Apple) o vertical (Rueda)
            double delta = (event.getDeltaX() != 0) ? event.getDeltaX() : event.getDeltaY();

            // 2. Sensibilidad
            double scrollAmount = delta * 1.5;

            // 3. Calcular el nuevo valor potencial
            double currentValue = timelineScroll.getValue();
            double newValue = currentValue - scrollAmount;

            // 4. ✅ LIMITACIÓN (Clamping): No permitir menos de 0 ni más del máximo
            double min = 0;
            double max = timelineScroll.getMax();

            if (newValue < min) newValue = min; // Evita los segundos negativos
            if (newValue > max) newValue = max; // Evita pasarse del final del video

            // 5. Aplicar el valor y consumir el evento
            timelineScroll.setValue(newValue);
            event.consume();

            // No hace falta redibujar manualmente aquí porque el listener de
            // timelineScroll.valueProperty() ya se encarga de llamar a redrawTimeline().
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

        // ✅ ESTO ES LO QUE FALTA: Conectar el arrastre del ratón
        timelineCanvas.setOnMouseDragged(this::onTimelineDragged);

        // También asegúrate de que el scroll se actualice al arrastrar
        timelineCanvas.setOnMousePressed(this::onTimelinePressed);
        timelineCanvas.setOnMouseReleased(this::onTimelineReleased);

        container.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style.css"))
                .toExternalForm());

        // Vincular icono de cámara al placeholder
        iconPlaceholder.setShape(AppIcons.getIcon("video-camera", 40));

        // ✅ LÓGICA AUTOMÁTICA: El panel solo se ve si no hay vídeo cargado
        placeholderView.visibleProperty().bind(videoView.imageProperty().isNull());

        timelineCanvas.setOnMouseMoved(e -> {
            double topMargin = 22;
            double scrollOffset = timelineScroll.getValue();

            // 1. Calculamos el tiempo bajo el ratón para el tooltip
            if (e.getY() < topMargin) {
                hoverTime = (e.getX() + scrollOffset) / pixelsPerSecond;
                // 2. ✅ LÓGICA DE LIVE PREVIEW (SCRUBBING)
                long now = System.currentTimeMillis();
                // Throttling: Solo actualizamos el vídeo cada 30ms para fluidez
                if (now - lastScrubTime > 30) {
                    seekTimeline(e.getX());
                    lastScrubTime = now;
                }
            } else {
                hoverTime = -1;
            }
            redrawTimeline();
        });

        timelineCanvas.setOnMouseExited(e -> {
            hoverTime = -1;
            redrawTimeline();
        });
    }

    private void updateSize(double newSize) {
        this.currentStrokeWidth = newSize; // ✅ Actualizamos la variable de dibujo principal

        if (sizeSlider != null) {
            sizeSlider.setValue(newSize); // Mantenemos sincronizado el slider si existe
        }

        if (selectedShapeToMove != null) {
            selectedShapeToMove.setStrokeWidth(newSize);
            redrawVideoCanvas();
        }
    }

    private void onTimelinePressed(MouseEvent e) {

        autoSaveActiveSegmentDrawings();

        double scrollOffset = timelineScroll.getValue();
        double clickTime = (e.getX() + scrollOffset) / pixelsPerSecond;
        double topMargin = 22;

        if (timelineContextMenu != null) timelineContextMenu.hide();

        // A. BUSCAR QUÉ SEGMENTO SE HA PINCHADO
        VideoSegment clickedSeg = null;
        for (VideoSegment seg : segments) {
            if (clickTime >= seg.getStartTime() && clickTime <= seg.getEndTime()) {
                clickedSeg = seg;
                break;
            }
        }

        // B. TRATAMIENTO SEGÚN EL BOTÓN
        if (e.getButton() == MouseButton.SECONDARY) {
            selectedSegment = clickedSeg; // Seleccionar para el menú contextual
            if (selectedSegment != null) {
                timelineContextMenu.show(timelineCanvas, e.getScreenX(), e.getScreenY());
            }
            redrawTimeline();
            return;
        }

        // C. CLIC IZQUIERDO (Selección y Arrastre)
        selectedSegment = clickedSeg; // ✅ AHORA SE SELECCIONA AL HACER CLIC
        segmentBeingDragged = null;
        isDraggingTimeline = false;

        if (e.getY() < topMargin) {
            seekTimeline(e.getX());
            return;
        }

        if (clickedSeg != null) {
            // ✅ GUARDAR ESTADO ANTES DE MOVER (Para Undo paso a paso)
            saveState();
            segmentBeingDragged = clickedSeg;
            currentDragX = e.getX();
        } else {
            seekTimeline(e.getX());
        }

        redrawTimeline();
    }

    private void onTimelineReleased(MouseEvent e) {
        if (isDraggingTimeline && segmentBeingDragged != null) {
            // 1. Extraer el segmento
            segments.remove(segmentBeingDragged);

            // 2. Insertar en el hueco detectado por la línea amarilla
            if (dropIndex >= 0 && dropIndex <= segments.size()) {
                segments.add(dropIndex, segmentBeingDragged);
            } else {
                segments.add(segmentBeingDragged);
            }

            // 3. Recalcular tiempos (esto ahora fijará el clip en su nueva posición)
            recalculateSegmentTimes();

            // 4. Limpiar variables
            isDraggingTimeline = false;
            segmentBeingDragged = null;
            dropIndex = -1;
            dropIndicatorX = -1;

            redrawTimeline();
        }
    }

    private void applyIcons() {
        if(btnCursor != null) btnCursor.setGraphic(AppIcons.getIcon("cursor", 20));
        if(btnPen != null) btnPen.setGraphic(AppIcons.getIcon("pen", 20));
        if(btnText != null) btnText.setGraphic(AppIcons.getIcon("text", 20));
        if(btnArrow != null) btnArrow.setGraphic(AppIcons.getIcon("arrow", 20));
        if(btnArrowDashed != null) btnArrowDashed.setGraphic(AppIcons.getIcon("arrow-dashed", 26));
        if(btnArrow3D != null) btnArrow3D.setGraphic(AppIcons.getIcon("arrow-3d", 20));
        if(btnSpotlight != null) btnSpotlight.setGraphic(AppIcons.getIcon("spotlight", 30));
        if(btnBase != null) btnBase.setGraphic(AppIcons.getIcon("base", 20));
        if(btnWall != null) btnWall.setGraphic(AppIcons.getIcon("wall", 30));
        if(btnPolygon != null) btnPolygon.setGraphic(AppIcons.getIcon("polygon", 30));
        if(btnRectShaded != null) btnRectShaded.setGraphic(AppIcons.getIcon("rect-shaded", 20));
        if(btnZoomCircle != null) btnZoomCircle.setGraphic(AppIcons.getIcon("zoom-circle", 20));
        if(btnZoomRect != null) btnZoomRect.setGraphic(AppIcons.getIcon("zoom-rect", 20));
        if(btnUndo != null) btnUndo.setGraphic(AppIcons.getIcon("undo", 20));
        if(btnRedo != null) btnRedo.setGraphic(AppIcons.getIcon("redo", 20));
        if(btnSkipStart != null) btnSkipStart.setGraphic(AppIcons.getIcon("skipBack", 20));
        if(btnSkipEnd != null) btnSkipEnd.setGraphic(AppIcons.getIcon("skipForward", 20));
        if(btnTracking != null) btnTracking.setGraphic(AppIcons.getIcon("tracking", 20));
        if(btnDeleteShape != null) btnDeleteShape.setGraphic(AppIcons.getIcon("trash", 20));
        if(btnClearCanvas != null) btnClearCanvas.setGraphic(AppIcons.getIcon("clear", 26));
    }

    // --- SELECTORES HERRAMIENTAS ---
    @FXML
    public void setToolCursor() {
        if (btnCursor != null) btnCursor.setSelected(true);
        changeTool(ToolType.CURSOR);
    }
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
    @FXML
    public void setToolTracking() {
        changeTool(ToolType.TRACKING);
        // Forzamos la activación del botón de IA si no está puesto
        if (btnToggleAI != null && !btnToggleAI.isSelected()) {
            btnToggleAI.setSelected(true);
            System.out.println("DEBUG: IA activada automáticamente para Tracking.");
        }

        runAIDetectionManual();
    }

    private void changeTool(ToolType type) {
        finishPolyShape();
        currentTool = type;
        selectedShapeToMove = null;
        redrawVideoCanvas();
    }

    // --- CORRECCIÓN DEL MURO Y POLÍGONO ---
    private void finishPolyShape() {
        if (currentShape != null &&
                ("wall".equals(currentShape.getType()) || "polygon".equals(currentShape.getType()))) {
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
                if (selectedShapeToMove != null
                        && checkHandles(selectedShapeToMove, e.getX(), e.getY())) {
                    saveState();
                    redrawVideoCanvas();
                    return;
                }
                selectedShapeToMove = null;
                for (int i = shapes.size() - 1; i >= 0; i--) {
                    DrawingShape s = shapes.get(i);
                    // Si la forma pertenece a otro clip, la ignoramos (no se puede seleccionar)
                    if (s.getClipId() != null && currentSeg != null &&
                            !s.getClipId().equals(currentSeg.getId())) {
                        continue;
                    }
                    // ------------------------------------------

                    if (s.isHit(e.getX(), e.getY())) {
                        saveState();
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

            if (currentTool == ToolType.TRACKING) {
                VideoSegment currentSegTrack = getCurrentSegment();
                if (currentSegTrack == null) return; // No permitir tracking en el vacío

                if (currentDetections.isEmpty()) {
                    runAIDetectionManual();
                    return;
                }

                saveState();
                double minDist = 100.0;
                trackedObject = null;

                Image vidImg = videoView.getImage();
                double sc = Math.min(drawCanvas.getWidth() / vidImg.getWidth(), drawCanvas.getHeight() / vidImg.getHeight());
                double vDW = vidImg.getWidth() * sc; double vDH = vidImg.getHeight() * sc;
                double oX = (drawCanvas.getWidth() - vDW) / 2.0; double oY = (drawCanvas.getHeight() - vDH) / 2.0;

                for (var obj : currentDetections) {
                    if (obj.getClassName().equals("person")) {
                        var r = obj.getBoundingBox().getBounds();
                        double cx = ((r.getX() + r.getWidth()/2.0) / 640.0) * vDW + oX;
                        double cy = ((r.getY() + r.getHeight()) / 640.0) * vDH + oY;

                        double dist = Math.sqrt(Math.pow(e.getX() - cx, 2) + Math.pow(e.getY() - cy, 2));
                        if (dist < minDist) { trackedObject = obj; minDist = dist; }
                    }
                }

                if (trackedObject != null) {
                    var r = trackedObject.getBoundingBox().getBounds();
                    double cX = ((r.getX() + r.getWidth()/2.0) / 640.0) * vDW + oX;
                    double headY = (r.getY() / 640.0) * vDH + oY;
                    double footY = ((r.getY() + r.getHeight()) / 640.0) * vDH + oY;

                    trackingShape = new DrawingShape("track_" + System.currentTimeMillis(), "tracking", cX, headY, toHex(colorPicker.getValue()));
                    trackingShape.setEndX(cX);
                    trackingShape.setEndY(footY);
                    trackingShape.setClipId(currentSegTrack.getId()); // ✅ BLOQUEO AL SEGMENTO

                    shapes.add(trackingShape);
                    redrawVideoCanvas();
                }
                return;
            }

            // ---------------------------------------------------------
            // AUTO-CONGELADO INTELIGENTE
            // ---------------------------------------------------------
            if (currentSeg != null && !currentSeg.isFreezeFrame()) {
                if (videoService.isPlaying()) {
                    videoService.pause();
                    btnPlayPause.setText("▶");
                }
                saveState();
                insertFreezeFrame(3.0);
                currentSeg = getCurrentSegment();
                // -------------------------------------------
            }

            // ---------------------------------------------------------
            // 2. MODO CREACIÓN
            // ---------------------------------------------------------
            selectedShapeToMove = null;
            if (currentTool == ToolType.TEXT) { showFloatingInput(e.getX(), e.getY()); return; }

            Color c = colorPicker != null ? colorPicker.getValue() : Color.RED;
            double s = currentStrokeWidth;

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
                default -> "arrow";
            };

            saveState();

            currentShape = new DrawingShape("s"+System.currentTimeMillis(), type, e.getX(), e.getY(), toHex(c));

            if (currentSeg != null) currentShape.setClipId(currentSeg.getId());
            currentShape.setStrokeWidth(s);
            // ✅ NUEVO: Añadir punto de control por defecto para la flecha 3D
            if ("arrow-3d".equals(type)) {
                // Ponemos el punto de control un poco más arriba del inicio para dar curvatura inicial
                currentShape.addPoint(e.getX(), e.getY() - 50);
            }
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
                else if (dragMode == 6 && "arrow-3d".equals(selectedShapeToMove.getType())) {
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

        // ✅ NUEVO: CASO ARROW-3D (Punto de Control)
        if ("arrow-3d".equals(s.getType()) && !s.getPoints().isEmpty()) {
            double cx = s.getPoints().get(0);
            double cy = s.getPoints().get(1);
            if (Math.abs(mx - cx) < dist && Math.abs(my - cy) < dist) {
                dragMode = 6; // Reutilizamos el modo de movimiento de control
                return true;
            }
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

        // 1. Identificar el segmento bajo el playhead
        VideoSegment activeSeg = getCurrentSegment();

        // 2. ✅ PRIORIDAD: Si es azul (freeze), usamos su foto. Si no, el video real.
        // Esto hace que el frame "se mueva" con sus dibujos a cualquier posición.
        Image vidImg = (activeSeg != null && activeSeg.isFreezeFrame() && activeSeg.getThumbnail() != null)
                ? activeSeg.getThumbnail()
                : videoView.getImage();

        if (vidImg == null) return;

        // 3. Cálculos de escala usando 'vidImg' (ya sea la foto o el video)
        double canvasW = drawCanvas.getWidth();
        double canvasH = drawCanvas.getHeight();
        double scale = Math.min(canvasW / vidImg.getWidth(), canvasH / vidImg.getHeight());
        double vidDispW = vidImg.getWidth() * scale;
        double vidDispH = vidImg.getHeight() * scale;
        double offX = (canvasW - vidDispW) / 2.0;
        double offY = (canvasH - vidDispH) / 2.0;

        // Dibujar el fondo (foto o video)
        gcDraw.drawImage(vidImg, offX, offY, vidDispW, vidDispH);

        // ✅ IA: Solo dibujamos los cuadros de detección si la herramienta de Tracking está activa
        if (btnToggleAI.isSelected() && currentTool == ToolType.TRACKING && !videoService.isPlaying() && currentDetections != null) {
            gcDraw.setLineWidth(1.5);
            for (var obj : currentDetections) {
                if (obj.getClassName().equals("person")) {
                    var r = obj.getBoundingBox().getBounds();
                    double x = (r.getX() / 640.0) * vidDispW + offX;
                    double y = (r.getY() / 640.0) * vidDispH + offY;
                    double w = (r.getWidth() / 640.0) * vidDispW;
                    double h = (r.getHeight() / 640.0) * vidDispH;
                    gcDraw.setStroke(Color.LIMEGREEN); gcDraw.strokeRect(x, y, w, h);
                }
            }
        }

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
                    gcDraw.fillOval(s.getStartX() - 5, s.getStartY() - 5, 10, 10);
                    gcDraw.fillOval(s.getEndX() - 5, s.getEndY() - 5, 10, 10);

                    // ✅ NUEVO: DIBUJAR PUNTO DE CONTROL PARA FLECHA 3D
                    if ("arrow-3d".equals(s.getType()) && !s.getPoints().isEmpty()) {
                        double cx = s.getPoints().get(0);
                        double cy = s.getPoints().get(1);

                        // Líneas guía punteadas
                        gcDraw.setStroke(Color.GRAY); gcDraw.setLineWidth(1); gcDraw.setLineDashes(3);
                        gcDraw.strokeLine(x1, y1, cx, cy);
                        gcDraw.strokeLine(x2, y2, cx, cy);

                        // Círculo amarillo de control (Radio)
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
                case "tracking":
                    // 1. Triángulo en la cabeza (StartX/Y)
                    double triSize = 14;
                    gcDraw.setFill(c);
                    double[] xPts = { x1, x1 - triSize/2, x1 + triSize/2 };
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
                    gcDraw.strokeRect(Math.min(x1,x2), Math.min(y1,y2), Math.abs(x2-x1), Math.abs(y2-y1));
            }
        }
        gcDraw.setEffect(null); gcDraw.setLineDashes(null);
    }

    // --- MÉTODOS DE DIBUJO ---

    private void drawSimpleWall(double x1, double y1, double x2, double y2, Color c) {
        double wallHeight = 80.0; // Altura del muro

        LinearGradient gradient = new LinearGradient(0, y1, 0, y1 - wallHeight, false, CycleMethod.NO_CYCLE,
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
        } else {
            gcDraw.strokeRect(left, top, w, h);
        }
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
            if (System.currentTimeMillis() < ignoreUpdatesUntil) return;
            // ✅ SI ESTAMOS EN PAUSA TÁCTICA, IGNORAMOS EL VIDEO REAL
            if (isPlayingFreeze) return;

            if (!videoService.isPlaying()) return;

            if (totalOriginalDuration > 0) {
                currentRealVideoTime = (percent / 100.0) * totalOriginalDuration;

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
            // Incrementar el contador de frames
            int currentFrame = frameCounter.incrementAndGet();
            // Ejemplo: Solo procesar IA cada 3 frames para no saturar
            if (currentFrame % 2 != 0) return;

            if (!btnToggleAI.isSelected() || isProcessingAI.get()) return;

            isProcessingAI.set(true);
            aiExecutor.submit(() -> {
                try {
                    var results = aiService.detectPlayers(bufferedImage);

                    Platform.runLater(() -> {
                        this.currentDetections = results;
                        VideoSegment activeSeg = getCurrentSegment();

                        // 1. RE-CONEXIÓN AUTOMÁTICA
                        // Si no hay tracking activo pero estamos en "Play", buscamos si este clip ya tiene un tracking guardado
                        if (trackingShape == null && activeSeg != null && videoService.isPlaying()) {
                            for (DrawingShape s : shapes) {
                                if ("tracking".equals(s.getType()) && activeSeg.getId().equals(s.getClipId())) {
                                    trackingShape = s; // ✅ Re-enganchamos el seguimiento
                                    break;
                                }
                            }
                        }

                        // 2. ACTUALIZACIÓN DE POSICIÓN
                        if (trackingShape != null && videoService.isPlaying()) {
                            // ✅ SEGURIDAD: Si el cabezal sale del clip del tracking, dejamos de seguir
                            if (activeSeg == null || !activeSeg.getId().equals(trackingShape.getClipId())) {
                                trackingShape = null;
                                return;
                            }

                            Image img = videoView.getImage();
                            if (img == null) return;

                            // Cálculo de escala y márgenes para precisión milimétrica
                            double sc = Math.min(drawCanvas.getWidth() / img.getWidth(), drawCanvas.getHeight() / img.getHeight());
                            double vDW = img.getWidth() * sc; double vDH = img.getHeight() * sc;
                            double oX = (drawCanvas.getWidth() - vDW) / 2.0; double oY = (drawCanvas.getHeight() - vDH) / 2.0;

                            DetectedObjects.DetectedObject closest = null;
                            double dMin = 120.0;

                            for (var obj : results) {
                                if (obj.getClassName().equals("person")) {
                                    var r = obj.getBoundingBox().getBounds();
                                    // Coordenadas reales mapeadas al video
                                    double nx = ((r.getX() + r.getWidth()/2.0) / 640.0) * vDW + oX;
                                    double ny = ((r.getY() + r.getHeight()) / 640.0) * vDH + oY;

                                    double d = Math.sqrt(Math.pow(trackingShape.getEndX() - nx, 2) + Math.pow(trackingShape.getEndY() - ny, 2));
                                    if (d < dMin) { dMin = d; closest = obj; }
                                }
                            }

                            if (closest != null) {
                                var r = closest.getBoundingBox().getBounds();
                                double cX = ((r.getX() + r.getWidth()/2.0) / 640.0) * vDW + oX;
                                // StartY = Cabeza (Triángulo), EndY = Pies (Base)
                                trackingShape.setStartX(cX);
                                trackingShape.setStartY((r.getY() / 640.0) * vDH + oY);
                                trackingShape.setEndX(cX);
                                trackingShape.setEndY(((r.getY() + r.getHeight()) / 640.0) * vDH + oY);
                            }
                        }
                        redrawVideoCanvas();
                        isProcessingAI.set(false);
                    });

                } catch (Exception e) { isProcessingAI.set(false); }
            });
        });
    }

    private void jumpToNextSegment(VideoSegment currentSeg) {
        resetTracking();
        autoSaveActiveSegmentDrawings();

        int nextIndex = (currentSeg != null) ? segments.indexOf(currentSeg) + 1 : getNextSegmentIndexByTime();

        if (nextIndex < segments.size()) {
            VideoSegment nextSeg = segments.get(nextIndex);
            currentTimelineTime = nextSeg.getStartTime();

            double seekPercent = (nextSeg.getSourceStartTime() / totalOriginalDuration) * 100.0;
            performSafeSeek(seekPercent);

            // ✅ IMPORTANTE: Verificar inmediatamente si el nuevo clip es un freeze
            checkPlaybackJump();
        } else {
            Platform.runLater(() -> {
                if (videoService.isPlaying()) onPlayPause();
                currentTimelineTime = totalTimelineDuration;
                redrawTimeline();
            });
        }
    }

    private void checkAutoScroll() {
        if (totalTimelineDuration <= 0) return;
        double w = timelineCanvas.getWidth();
        double playheadPos = (currentTimelineTime * pixelsPerSecond) - timelineScroll.getValue();
        if (playheadPos > w) {
            timelineScroll.setValue(timelineScroll.getValue() + playheadPos - w + 50);
        } else if (playheadPos < 0) {
            timelineScroll.setValue(timelineScroll.getValue() + playheadPos - 50);
        }
    }

    private void checkPlaybackJump() {
        VideoSegment currentSeg = getCurrentSegment();

        if (currentSeg != null && currentSeg.isFreezeFrame()) {
            if (!isPlayingFreeze) {
                videoService.pause();
                isPlayingFreeze = true;
                lastTimerTick = 0;
                freezeTimer.start();
            }
        } else {
            // --- SALIR DE MODO CONGELADO ---
            if (isPlayingFreeze) {
                freezeTimer.stop();
                isPlayingFreeze = false;
            }

            // ✅ CLAVE: Si caemos en un hueco (null) y estamos en Play, saltamos al siguiente
            if ("||".equals(btnPlayPause.getText())) {
                if (currentSeg == null) {
                    jumpToNextSegment(null); // Salto automático de huecos
                } else {
                    videoService.play();
                }
            }
        }
    }

    @FXML public void onCaptureFrame() {
        if (segments.isEmpty()) return;
        if (videoService.isPlaying()) onPlayPause();
        TextInputDialog dialog = new TextInputDialog("3");
        dialog.setTitle("Capturar Frame");
        dialog.setHeaderText("Congelar imagen");
        dialog.setContentText("Duración en segundos:");
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            try {
                double duration = Double.parseDouble(result.get());
                if (duration <= 0) return;
                insertFreezeFrame(duration);
            } catch (NumberFormatException e) {}
        }
    }

    private void insertFreezeFrame(double duration) {
        saveState(); // ✅ Guardamos el estado para el Undo paso a paso
        double gap = 0.05; // La separación que quieres igualar al corte

        VideoSegment activeSeg = null;
        for (VideoSegment seg : segments) {
            if (currentTimelineTime >= seg.getStartTime() && currentTimelineTime < seg.getEndTime()) {
                activeSeg = seg;
                break;
            }
        }

        if (activeSeg != null) {
            double splitTimeTimeline = currentTimelineTime;
            double splitTimeSource = activeSeg.getSourceStartTime() + (currentTimelineTime - activeSeg.getStartTime());
            double originalEndTimeTimeline = activeSeg.getEndTime();
            double originalEndTimeSource = activeSeg.getSourceEndTime();

            // 1. El primer clip termina justo en el punto de clic
            activeSeg.setEndTime(splitTimeTimeline);
            activeSeg.setSourceEndTime(splitTimeSource);

            // 2. El Freeze empieza después del PRIMER GAP
            VideoSegment freezeSeg = new VideoSegment(
                    splitTimeTimeline + gap,
                    splitTimeTimeline + gap + duration,
                    splitTimeSource, splitTimeSource, "#00bcd4", true
            );
            // Asignamos la imagen actual del video al frame congelado
            if (videoView.getImage() != null) freezeSeg.setThumbnail(videoView.getImage());

            // 3. El siguiente clip de video empieza después del SEGUNDO GAP
            String colorRight = activeSeg.getColor().equals("#3b82f6") ? "#10b981" : "#3b82f6";
            VideoSegment rightSeg = new VideoSegment(
                    freezeSeg.getEndTime() + gap,
                    originalEndTimeTimeline + duration + (2 * gap),
                    splitTimeSource, originalEndTimeSource, colorRight, false
            );

            int idx = segments.indexOf(activeSeg);
            segments.add(idx + 1, freezeSeg);
            segments.add(idx + 2, rightSeg);

            // 4. Empujamos el resto de segmentos con el desplazamiento total (duración + 2 gaps)
            double totalShift = duration + (2 * gap);
            for (int i = idx + 3; i < segments.size(); i++) {
                VideoSegment s = segments.get(i);
                s.setStartTime(s.getStartTime() + totalShift);
                s.setEndTime(s.getEndTime() + totalShift);
            }

            totalTimelineDuration += totalShift;
            updateScrollbarAndRedraw();
        }
    }

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
            double gap = 0.05; // 1 segundo de separación visual
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

            if (isDraggingTimeline && seg == segmentBeingDragged) continue;

            double startX = (seg.getStartTime() * pixelsPerSecond) - scrollOffset;
            double width = seg.getDuration() * pixelsPerSecond;

            if (startX + width > 0 && startX < w) {
                drawStyledClip(gcTimeline, seg, startX, topMargin, width, barHeight, 1.0);
            }
        }

        // 3. DIBUJAR EL "FANTASMA" (GHOST)
        if (isDraggingTimeline && segmentBeingDragged != null) {
            double ghostW = segmentBeingDragged.getDuration() * pixelsPerSecond;
            double ghostX = currentDragX - (ghostW / 2.0);
            drawStyledClip(gcTimeline, segmentBeingDragged, ghostX, topMargin - 5, ghostW, barHeight + 10, 0.6);
        }

        // 4. ✅ NUEVA BARRA DE INSERCIÓN (iMovie Style)
        if (isDraggingTimeline && segmentBeingDragged != null && dropIndicatorX != -1) {

            gcTimeline.setStroke(Color.YELLOW);
            gcTimeline.setLineWidth(1.0);
            gcTimeline.strokeLine(dropIndicatorX, 0, dropIndicatorX, h); // De arriba a abajo

            // Flecha pequeña amarilla arriba
            gcTimeline.setFill(Color.YELLOW);
            double arrowSize = 6.0;
            double[] xPtsY = { dropIndicatorX - arrowSize, dropIndicatorX + arrowSize, dropIndicatorX };
            double[] yPtsY = { 0, 0, arrowSize + 2 };
            gcTimeline.fillPolygon(xPtsY, yPtsY, 3);
        }

        // 5. DIBUJAR REGLA Y CABEZAL
        drawRulerAndPlayhead(w, h, scrollOffset);

        if (hoverTime >= 0 && hoverTime <= totalTimelineDuration) {
            double hX = (hoverTime * pixelsPerSecond) - scrollOffset;

            // 1. Dibujar la línea vertical tenue
            gcTimeline.setStroke(Color.web("#ffffff", 0.4));
            gcTimeline.setLineWidth(1.0);
            gcTimeline.strokeLine(hX, 0, hX, h);

            // 2. Dibujar el Tooltip (Recuadro flotante)
            String timeText = formatPreciseTime(hoverTime);

            // Configuración del texto para medirlo
            gcTimeline.setFont(Font.font("Arial", FontWeight.BOLD, 10));
            javafx.scene.text.Text temp = new javafx.scene.text.Text(timeText);
            temp.setFont(gcTimeline.getFont());
            double txtW = temp.getLayoutBounds().getWidth();

            double rectW = txtW + 10;
            double rectH = 16;
            double rectX = hX - (rectW / 2);
            double rectY = 12; // Justo debajo de la regla de números

            // Fondo del recuadro (Negro redondeado)
            gcTimeline.setFill(Color.web("#1a1a1a", 0.9));
            gcTimeline.fillRoundRect(rectX, rectY, rectW, rectH, 5, 5);

            // Borde fino para que resalte
            gcTimeline.setStroke(Color.web("#ffffff", 0.2));
            gcTimeline.strokeRoundRect(rectX, rectY, rectW, rectH, 5, 5);

            // Texto del tiempo
            gcTimeline.setFill(Color.WHITE);
            gcTimeline.fillText(timeText, rectX + 5, rectY + 12);
        }
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

    // EditorController.java -> Sustituye tu método por esta versión refinada
    private void drawRulerAndPlayhead(double w, double h, double scrollOffset) {
        int stepTime = (pixelsPerSecond > 80) ? 1 : (pixelsPerSecond > 40) ? 5 : (pixelsPerSecond > 20) ? 10 : 30;
        int startSec = ((int) (scrollOffset / pixelsPerSecond) / stepTime) * stepTime;
        int endSec = (int) ((scrollOffset + w) / pixelsPerSecond) + 1;

        gcTimeline.setStroke(Color.GRAY);
        gcTimeline.setLineWidth(0.7); // Línea más fina

        for (int i = startSec; i <= endSec; i += stepTime) {
            double x = (i * pixelsPerSecond) - scrollOffset;

            // ✅ Rayita grande: Reducida de 15 a 10
            gcTimeline.strokeLine(x, 0, x, 10);

            gcTimeline.setFill(Color.GRAY);
            // ✅ Fuente: Reducida de 10 a 8
            gcTimeline.setFont(Font.font("Arial", 8));
            // ✅ Posición texto: Ajustada de 12 a 9 para acompañar la marca
            gcTimeline.fillText(formatShortTime(i), x + 2, 9);

            // Rayitas pequeñas
            if (stepTime >= 5) {
                for (int j = 1; j < stepTime; j++) {
                    double sx = x + j * pixelsPerSecond;
                    if (sx - scrollOffset < w) {
                        // ✅ Rayita pequeña: Reducida de 5 a 3
                        gcTimeline.strokeLine(sx, 0, sx, 3);
                    }
                }
            }
        }

        // --- DIBUJO DEL HOVER TOOLTIP (Debajo de la regla) ---
        if (hoverTime >= 0 && hoverTime <= totalTimelineDuration) {
            double hX = (hoverTime * pixelsPerSecond) - scrollOffset;

            // Línea vertical blanca tenue
            gcTimeline.setStroke(Color.web("#ffffff", 0.3));
            gcTimeline.strokeLine(hX, 0, hX, h);

            // Recuadro con tiempo (mm:ss)
            String timeStr = formatPreciseTime(hoverTime);
            gcTimeline.setFont(Font.font("Arial", FontWeight.BOLD, 9));

            double rectW = 34; double rectH = 14;
            double rectX = hX - (rectW / 2);
            double rectY = 12; // Posicionado justo bajo los números pequeños

            gcTimeline.setFill(Color.web("#1a1a1a", 0.9));
            gcTimeline.fillRoundRect(rectX, rectY, rectW, rectH, 4, 4);
            gcTimeline.setFill(Color.WHITE);
            gcTimeline.fillText(timeStr, rectX + 4, rectY + 10);
        }

        // Cabezal de reproducción rojo (Tu código original se mantiene igual)
        double phX = (currentTimelineTime * pixelsPerSecond) - scrollOffset;
        if (phX >= 0 && phX <= w) {
            gcTimeline.setStroke(Color.RED);
            gcTimeline.setLineWidth(1.0);
            gcTimeline.strokeLine(phX, 0, phX, h);
            gcTimeline.setFill(Color.RED);
            double arrowSize = 6.0;
            double[] xPts = { phX - arrowSize, phX + arrowSize, phX };
            double[] yPts = { 0, 0, arrowSize + 2 };
            gcTimeline.fillPolygon(xPts, yPts, 3);
            gcTimeline.fillOval(phX - 1.5, arrowSize, 3, 3);
        }
    }

    private void updateScrollbarAndRedraw() {
        if (totalTimelineDuration <= 0) {
            timelineScroll.setVisible(false); // Ocultar si no hay video
            timelineScroll.setManaged(false);
            return;
        }

        double canvasW = timelineCanvas.getWidth();
        double totalW = totalTimelineDuration * pixelsPerSecond;

        // ✅ LÓGICA DE AUTO-HIDE
        // Solo es visible si el ancho total es mayor que el ancho del canvas
        boolean needsScroll = totalW > canvasW;
        timelineScroll.setVisible(needsScroll);
        timelineScroll.setManaged(needsScroll); // Para que no ocupe espacio si no se ve

        if (needsScroll) {
            timelineScroll.setMax(Math.max(0, totalW - canvasW));
            timelineScroll.setVisibleAmount((canvasW / totalW) * timelineScroll.getMax());
        }

        redrawTimeline();
    }

    private void updateTimeLabel() {
        int m = (int) currentTimelineTime / 60; int s = (int) currentTimelineTime % 60;
        lblTime.setText(String.format("%02d:%02d", m, s) + " / " + String.format("%02d:%02d", (int)totalTimelineDuration/60, (int)totalTimelineDuration%60));
    }

    private String formatShortTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return (m > 0) ? String.format("%d:%02d", m, s) : s + "s";
    }

    public void onImportVideo() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Video", "*.mp4", "*.mov"));
        File f = fc.showOpenDialog(container.getScene().getWindow());

        if (f != null) {
            this.localVideoPath = f.getAbsolutePath();

            System.out.println("--- DIAGNÓSTICO DE IMPORTACIÓN ---");
            if (this.localVideoPath != null) {
                System.out.println("✅ USANDO RUTA LOCAL: " + this.localVideoPath);
            } else {
                System.err.println("⚠️ PELIGRO: La variable localVideoPath es NULL. La exportación fallará.");
            }
            System.out.println("-----------------------------------");

            System.out.println("🎥 MODO LOCAL: Video cargado desde " + this.localVideoPath);

            // 1. MOSTRAR SPINNER
            if (loadingSpinner != null) loadingSpinner.setVisible(true);

            // Limpiar canvas visualmente mientras carga
            gcTimeline.clearRect(0,0, timelineCanvas.getWidth(), timelineCanvas.getHeight());

            try {
                videoService.loadVideo(f.getAbsolutePath());
                /*new Thread(() -> {
                    try {
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
                }).start();*/
            } catch (Exception e) {
                e.printStackTrace();
            }

            Task<TreeMap<Double, Image>> task = new Task<>() {
                @Override
                protected TreeMap<Double, Image> call() throws Exception {
                    // Intervalo de 5 segundos, altura de miniatura 100px (para aprovechar la nueva altura)
                    return new FilmstripService().generateFilmstrip(f, 5.0, 80);
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

                Platform.runLater(() -> {
                    updateTimeLabel();        // Actualiza "00:00 / XX:XX"
                    updateScrollbarAndRedraw(); // Dibuja el timeline
                    redrawVideoCanvas();      // Dibuja el primer frame
                    if (btnPlayPause != null) btnPlayPause.setText("▶");
                });

                if (timelineScroll != null) {
                    timelineScroll.setValue(0);
                    timelineScroll.setMax(totalTimelineDuration * pixelsPerSecond);
                }
                updateScrollbarAndRedraw();
                videoService.pause();
                videoService.seek(0); // Asegura que se vea el primer frame
            });

            task.setOnFailed(e -> {
                if (loadingSpinner != null) loadingSpinner.setVisible(false);
                System.err.println("Error generando miniaturas");
            });

            new Thread(task).start();
        }
    }

    // 1. Reducimos el tiempo de bloqueo de 1000ms a 150ms para que sea instantáneo
    private void performSafeSeek(double percent) {
        ignoreUpdatesUntil = System.currentTimeMillis() + 150; // ✅ Evita el "salto" de 1 segundo después del click
        videoService.seek(percent);
    }

    // 2. Buscamos el punto de vídeo más cercano si haces clic en un hueco
    private void seekTimeline(double mouseX) {
        resetTracking();

        autoSaveActiveSegmentDrawings();

        double scrollOffset = timelineScroll.getValue();
        double time = (mouseX + scrollOffset) / pixelsPerSecond;

        if (time < 0) time = 0;
        if (time > totalTimelineDuration) time = totalTimelineDuration;

        currentTimelineTime = time;

        // Buscamos el segmento donde hemos caído O el más cercano
        VideoSegment targetSeg = null;
        for (VideoSegment s : segments) {
            if (time >= s.getStartTime() && time <= s.getEndTime()) {
                targetSeg = s;
                break;
            }
        }

        if (targetSeg != null) {
            double offset = time - targetSeg.getStartTime();
            double seekTarget = targetSeg.getSourceStartTime() + offset;
            performSafeSeek((seekTarget / totalOriginalDuration) * 100.0);
        } else {
            // ✅ SI CAES EN UN HUECO: Buscamos el siguiente clip para que el video no se pierda
            for (VideoSegment s : segments) {
                if (s.getStartTime() > time) {
                    performSafeSeek((s.getSourceStartTime() / totalOriginalDuration) * 100.0);
                    break;
                }
            }
        }

        // ✅ REFRESCAR TODO AL INSTANTE
        Platform.runLater(() -> {
            checkPlaybackJump();
            redrawTimeline();
            redrawVideoCanvas();
            updateTimeLabel();
        });
    }

    @FXML
    public void onPlayPause() {
        if (videoService.isPlaying() || isPlayingFreeze) {
            videoService.pause();
            if (isPlayingFreeze) {
                freezeTimer.stop();
                isPlayingFreeze = false;
            }
            btnPlayPause.setText("▶");
            isProcessingAI.set(false);
        } else {
            checkPlaybackJump();
            if (!isPlayingFreeze) {
                videoService.play();
            }
            btnPlayPause.setText("||");
        }
    }

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
        if (segments.isEmpty()) return;

        double currentPos = 0.0;
        double editGap = 0.05; // ✅ El espacio de transición que quieres mantener entre cortes

        for (int i = 0; i < segments.size(); i++) {
            VideoSegment seg = segments.get(i);
            double duration = seg.getDuration();

            seg.setStartTime(currentPos);
            seg.setEndTime(currentPos + duration);

            // El siguiente clip empezará después de la duración + el hueco de seguridad
            currentPos = seg.getEndTime() + editGap;
        }

        totalTimelineDuration = currentPos - editGap;
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

    private void saveState() {
        if (segments == null) return;

        // 1. Copia real de Segmentos
        List<VideoSegment> segmentsSnapshot = new ArrayList<>();
        for (VideoSegment s : segments) {
            VideoSegment copySeg = new VideoSegment(
                    s.getStartTime(), s.getEndTime(),
                    s.getSourceStartTime(), s.getSourceEndTime(),
                    s.getColor(), s.isFreezeFrame()
            );
            copySeg.setId(s.getId());
            copySeg.setThumbnail(s.getThumbnail());
            segmentsSnapshot.add(copySeg);
        }

        // 2. Copia real de Dibujos (paso a paso)
        List<DrawingShape> shapesSnapshot = new ArrayList<>();
        for (DrawingShape sh : shapes) {
            shapesSnapshot.add(sh.copy()); // Usamos el método copy() que creamos arriba
        }

        // 3. Guardar en la pila
        undoStack.push(new EditorState(segmentsSnapshot, shapesSnapshot, totalTimelineDuration));
        redoStack.clear(); // Al hacer una acción nueva, el futuro se borra
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

    @FXML
    public void onRedo() {
        if (redoStack.isEmpty()) return;

        // Guardamos el estado actual en Undo antes de saltar al futuro
        undoStack.push(new EditorState(new ArrayList<>(segments), new ArrayList<>(shapes),
                totalTimelineDuration));

        restoreState(redoStack.pop());
        updateUndoRedoButtons();
    }

    private void restoreState(EditorState state) {
        this.segments = new ArrayList<>(state.segmentsSnapshot);
        this.shapes = new ArrayList<>(state.shapesSnapshot);
        this.totalTimelineDuration = state.durationSnapshot;

        // ✅ REFRESCAR TODO EL SISTEMA VISUAL
        updateScrollbarAndRedraw();
        redrawVideoCanvas();
        redrawTimeline();
        updateTimeLabel();
    }

    // =========================================================================
    //                           EXPORTACIÓN (NUEVO)
    // =========================================================================

    @FXML
    public void onExportVideo() {
        if (localVideoPath == null) {
            mostrarAlerta("Error", "No hay video cargado.");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setInitialFileName("analisis_final.mp4");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("MP4 Video", "*.mp4"));
        File destFile = fc.showSaveDialog(container.getScene().getWindow());
        if (destFile == null) return;

        if (loadingSpinner != null) loadingSpinner.setVisible(true);
        if (lblStatus != null) lblStatus.setText("Preparando exportación local...");

        // Usamos un hilo para no congelar la UI
        new Thread(() -> {
            try {
                // 1. Preparar la lista de trabajos para el Servicio
                List<LocalExportService.ExportJobSegment> jobSegments = new ArrayList<>();

                for (VideoSegment seg : segments) {
                    if (seg.isFreezeFrame()) {
                        // Generar imagen en el Hilo de JavaFX (obligatorio para Canvas)
                        FutureTask<String> task = new FutureTask<>(() -> saveSnapshotToTempFile(seg));
                        Platform.runLater(task);
                        String imgPath = task.get(); // Esperamos a que se genere

                        if (imgPath != null) {
                            jobSegments.add(LocalExportService.ExportJobSegment.freeze(imgPath, seg.getDuration()));
                        }
                    } else {
                        jobSegments.add(LocalExportService.ExportJobSegment.video(seg.getSourceStartTime(), seg.getSourceEndTime()));
                    }
                }

                // 2. LLAMAR AL SERVICIO DE FFMPEG
                Platform.runLater(() -> lblStatus.setText("Renderizando video (FFmpeg)..."));

                LocalExportService exportService = new LocalExportService();
                exportService.renderProject(localVideoPath, jobSegments, destFile);

                // 3. FINALIZAR
                Platform.runLater(() -> {
                    if (loadingSpinner != null) loadingSpinner.setVisible(false);
                    lblStatus.setText("✅ Exportación completada");
                    mostrarAlerta("Éxito", "Video guardado en: " + destFile.getAbsolutePath());
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    if (loadingSpinner != null) loadingSpinner.setVisible(false);
                    mostrarAlerta("Error", "Fallo al exportar: " + e.getMessage());
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

        addTooltip(btnTracking, "Tracking - Seguimiento automático de jugadores");

        addTooltip(btnDeleteShape, "Borrar edición");
        addTooltip(btnClearCanvas, "Limpiar dibujo");
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
            double exportW = 1280; double exportH = 720;
            Canvas tempCanvas = new Canvas(exportW, exportH);
            GraphicsContext gc = tempCanvas.getGraphicsContext2D();

            Image bg = seg.getThumbnail();
            if (bg == null) return null;

            gc.drawImage(bg, 0, 0, exportW, exportH);

            // Parámetros de escala originales
            double canvasW = drawCanvas.getWidth();
            double canvasH = drawCanvas.getHeight();
            double scale = Math.min(canvasW / bg.getWidth(), canvasH / bg.getHeight());
            double vidDispW = bg.getWidth() * scale;
            double vidDispH = bg.getHeight() * scale;
            double offX = (canvasW - vidDispW) / 2.0;
            double offY = (canvasH - vidDispH) / 2.0;

            // ✅ PASO CLAVE: Combinar dibujos guardados y dibujos "vivos"
            List<DrawingShape> allToDraw = new ArrayList<>();
            // 1. Añadir lo que ya estaba en la caché (guardado anteriormente)
            List<DrawingShape> cached = annotationsCache.get(seg.getId());
            if (cached != null) allToDraw.addAll(cached);

            // 2. Añadir dibujos actuales que pertenezcan a este clip pero no estén en caché
            for (DrawingShape s : shapes) {
                if (seg.getId().equals(s.getClipId())) {
                    if (cached == null || !cached.contains(s)) {
                        allToDraw.add(s);
                    }
                }
            }

            // Dibujar todo el conjunto combinado
            for (DrawingShape s : allToDraw) {
                drawShapeScaledToVideo(gc, s, offX, offY, vidDispW, vidDispH, exportW, exportH, bg);
            }

            WritableImage snap = tempCanvas.snapshot(null, null);
            BufferedImage bImage = SwingFXUtils.fromFXImage(snap, null);
            ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            ImageIO.write(bImage, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    private String saveSnapshotToTempFile(VideoSegment seg) {
        try {
            double exportW = 1280; double exportH = 720;
            Canvas tempCanvas = new Canvas(exportW, exportH);
            GraphicsContext gc = tempCanvas.getGraphicsContext2D();

            Image bg = seg.getThumbnail();
            if (bg == null) return null;

            gc.drawImage(bg, 0, 0, exportW, exportH);

            // Parámetros de escala originales
            double canvasW = drawCanvas.getWidth();
            double canvasH = drawCanvas.getHeight();
            double scale = Math.min(canvasW / bg.getWidth(), canvasH / bg.getHeight());
            double vidDispW = bg.getWidth() * scale;
            double vidDispH = bg.getHeight() * scale;
            double offX = (canvasW - vidDispW) / 2.0;
            double offY = (canvasH - vidDispH) / 2.0;

            // ✅ PASO CLAVE: Combinar dibujos guardados y dibujos "vivos"
            List<DrawingShape> allToDraw = new ArrayList<>();
            // 1. Añadir lo que ya estaba en la caché (guardado anteriormente)
            List<DrawingShape> cached = annotationsCache.get(seg.getId());
            if (cached != null) allToDraw.addAll(cached);

            // 2. Añadir dibujos actuales que pertenezcan a este clip pero no estén en caché
            for (DrawingShape s : shapes) {
                if (seg.getId().equals(s.getClipId())) {
                    if (cached == null || !cached.contains(s)) {
                        allToDraw.add(s);
                    }
                }
            }

            // Dibujar todo el conjunto combinado
            for (DrawingShape s : allToDraw) {
                drawShapeScaledToVideo(gc, s, offX, offY, vidDispW, vidDispH, exportW, exportH, bg);
            }

            // AL FINAL, EN LUGAR DE BASE64, GUARDAMOS A DISCO:
            WritableImage snap = tempCanvas.snapshot(null, null);
            BufferedImage bImage = SwingFXUtils.fromFXImage(snap, null);

            // Crear archivo temporal
            File tempFile = File.createTempFile("freeze_", ".png");
            ImageIO.write(bImage, "png", tempFile);

            return tempFile.getAbsolutePath(); // ✅ Retornamos la ruta
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
        gc.setStroke(color);
        gc.setFill(color);
        gc.setLineWidth(size / 3.0);

        // 1. Calcular el ángulo final de la curva
        double angle = Math.atan2(y2 - cy, x2 - cx);
        double headLen = size * 1.5;

        // 2. ✅ SOLUCIÓN: Retroceder el final de la línea curva (Offset de la punta)
        double lineEndX = x2 - (headLen * 0.5) * Math.cos(angle);
        double lineEndY = y2 - (headLen * 0.5) * Math.sin(angle);

        // 3. Dibujar la curva hasta el punto de retroceso
        gc.beginPath();
        gc.moveTo(x1, y1);
        gc.quadraticCurveTo(cx, cy, lineEndX, lineEndY);
        gc.stroke();

        // 4. Dibujar la punta de flecha en la coordenada real final (x2, y2)
        drawArrowHeadOnGC(gc, x2, y2, angle, size, color);
    }

    private void drawSimpleWallOnGC(GraphicsContext gc, double x1, double y1, double x2, double y2, Color c, double wallHeight) {
        gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.25));
        gc.fillPolygon(new double[]{x1, x2, x2, x1},
                new double[]{y1, y2, y2 - wallHeight, y1 - wallHeight}, 4);
        gc.setStroke(c);
        gc.strokeLine(x1, y1, x2, y2);
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

    private void onTimelineDragged(MouseEvent e) {
        // 1. Declaramos las constantes y variables de margen
        double topMargin = 22;
        double canvasW = timelineCanvas.getWidth();
        double edgeThreshold = 70.0; // Distancia al borde para activar el scroll

        // ✅ DEFINIMOS scrollOffset AQUÍ (Esto corrige tu error)
        double scrollOffset = timelineScroll.getValue();

        if (segmentBeingDragged != null && e.getY() >= topMargin) {
            isDraggingTimeline = true;
            currentDragX = e.getX();

            // 2. LÓGICA DE AUTO-SCROLL
            if (e.getX() > canvasW - edgeThreshold) {
                timelineScroll.setValue(scrollOffset + 15);
            } else if (e.getX() < edgeThreshold && scrollOffset > 0) {
                timelineScroll.setValue(scrollOffset - 15);
            }

            // 4. LÓGICA DE INSERCIÓN iMOVIE (Usando scrollOffset de forma segura)
            double mousePosTime = (e.getX() + timelineScroll.getValue()) / pixelsPerSecond;

            List<VideoSegment> others = new ArrayList<>(segments);
            others.remove(segmentBeingDragged);

            int newDropIndex = 0;
            double indicatorTime = 0;

            if (!others.isEmpty()) {
                for (int i = 0; i < others.size(); i++) {
                    VideoSegment s = others.get(i);
                    double midPoint = s.getStartTime() + (s.getDuration() / 2.0);

                    if (mousePosTime < midPoint) {
                        newDropIndex = i;
                        indicatorTime = s.getStartTime();
                        break;
                    } else {
                        newDropIndex = i + 1;
                        indicatorTime = s.getEndTime();
                    }
                }
            }

            // 5. ACTUALIZAR VARIABLES PARA EL DIBUJO
            this.dropIndex = newDropIndex;
            // Usamos el valor de scroll actualizado para que la línea amarilla no "tiemble"
            this.dropIndicatorX = (indicatorTime * pixelsPerSecond) - timelineScroll.getValue();

            redrawTimeline();

        } else {
            // --- ✅ LÓGICA DE SCRUBBING PROFESIONAL ---
            long now = System.currentTimeMillis();
            // Solo pedimos un nuevo frame al vídeo si han pasado al menos 30ms
            // Esto evita que la aplicación se bloquee por exceso de peticiones
            if (now - lastScrubTime > 30) {
                seekTimeline(e.getX());
                lastScrubTime = now;
            }
        }
    }

    private void initTimelineContextMenu() {
        timelineContextMenu = new ContextMenu();

        MenuItem modifyTimeItem = new MenuItem("Modificar Duración");
        modifyTimeItem.setOnAction(e -> onModifyFreezeDuration());

        MenuItem captureItem = new MenuItem("Capturar Frame");
        captureItem.setOnAction(e -> onCaptureFrame());

        MenuItem cutItem = new MenuItem("Cortar");
        cutItem.setOnAction(e -> onCutVideo());

        MenuItem deleteItem = new MenuItem("Borrar");
        deleteItem.setStyle("-fx-text-fill: #ff4444;");
        deleteItem.setOnAction(e -> onDeleteSegment());

        // Añadimos la nueva opción al principio
        timelineContextMenu.getItems().addAll(
                modifyTimeItem,
                new SeparatorMenuItem(),
                captureItem,
                cutItem,
                new SeparatorMenuItem(),
                deleteItem);
    }

    @FXML
    public void onModifyFreezeDuration() {
        if (selectedSegment == null || !selectedSegment.isFreezeFrame()) {
            mostrarAlerta("Aviso", "Selecciona un clip azul primero.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(String.valueOf(selectedSegment.getDuration()));
        dialog.setTitle("Duración del Freeze");
        dialog.setHeaderText("Modificar tiempo de congelado");
        dialog.setContentText("Nueva duración (segundos):");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            try {
                double newDuration = Double.parseDouble(result.get());
                if (newDuration <= 0) return;

                // ✅ REGISTRAR EN EL HISTORIAL ANTES DEL CAMBIO
                saveState();

                // Actualizamos el final del segmento (startTime + duración)
                selectedSegment.setEndTime(selectedSegment.getStartTime() + newDuration);

                // Reajustamos toda la línea de tiempo magnética
                recalculateSegmentTimes();
                redrawTimeline();
            } catch (NumberFormatException e) {
                mostrarAlerta("Error", "Introduce un número válido.");
            }
        }
    }

    private void autoSaveActiveSegmentDrawings() {
        // Si no hay dibujos o no hay video cargado, no hacemos nada
        if (shapes.isEmpty()) return;

        VideoSegment activeSeg = getCurrentSegment();
        if (activeSeg == null || !activeSeg.isFreezeFrame()) return;

        String segmentId = activeSeg.getId();

        // 1. Mover dibujos de la lista "viva" a la caché local inmediatamente
        // Esto evita que desaparezcan de la pantalla al cambiar de tiempo
        List<DrawingShape> drawingsToPersist = new ArrayList<>(shapes);
        annotationsCache.computeIfAbsent(segmentId, k -> new ArrayList<>()).addAll(drawingsToPersist);

        for(DrawingShape s : drawingsToPersist) {
            s.setClipId(segmentId);
        }

        // Limpiamos shapes para que el sistema sepa que ya están "procesados"
        shapes.clear();

        System.out.println("✅ Dibujos guardados en memoria local para exportación.");
    }

    private void runAIDetectionManual() {
        if (btnToggleAI == null || !btnToggleAI.isSelected() || isProcessingAI.get()) return;

        Image fxImage = videoView.getImage();
        if (fxImage == null) return;

        isProcessingAI.set(true);

        Platform.runLater(() -> {
            if (lblStatus != null) lblStatus.setText("🔍 Analizando frame...");
        });

        aiExecutor.submit(() -> {
            try {
                BufferedImage bufferedImage = SwingFXUtils.fromFXImage(fxImage, null);
                var results = aiService.detectPlayers(bufferedImage);

                Platform.runLater(() -> {
                    // Si el usuario apagó el botón mientras procesábamos, abortamos
                    if (!btnToggleAI.isSelected()) {
                        this.currentDetections.clear();
                        redrawVideoCanvas();
                        isProcessingAI.set(false);
                        return;
                    }

                    this.currentDetections = results;
                    redrawVideoCanvas(); // ✅ FUNDAMENTAL: Para que aparezcan las cajas

                    if (lblStatus != null) lblStatus.setText("✅ Análisis listo");
                    isProcessingAI.set(false); // ✅ LIBERAR para la siguiente detección
                });
            } catch (Exception e) {
                Platform.runLater(() -> isProcessingAI.set(false));
            }
        });
    }

    private void resetTracking() {
        this.trackedObject = null;
        this.trackingShape = null; // ✅ Soltamos el seguimiento activo, pero el objeto queda en 'shapes'
        this.currentDetections.clear();

        if (btnToggleAI != null) btnToggleAI.setSelected(false);
        this.currentTool = ToolType.CURSOR;
        if (btnCursor != null) btnCursor.setSelected(true);

        redrawVideoCanvas();
    }

    @FXML
    public void onCloseVideo() {
        // 1. Detener reproducción y hilos
        if (videoService != null) videoService.pause();
        if (isPlayingFreeze) {
            freezeTimer.stop();
            isPlayingFreeze = false;
        }

        // 2. Limpiar DATOS (Listas y Mapas)
        segments.clear();
        shapes.clear();
        annotationsCache.clear();
        filmstripMap.clear();

        // 3. Resetear VARIABLES de estado
        currentTimelineTime = 0.0;
        totalTimelineDuration = 0.0;
        serverVideoId = null;
        selectedSegment = null;
        selectedShapeToMove = null;

        // 4. LIMPIEZA VISUAL FORZADA (Evita que la imagen se quede "debajo" del icono)
        videoView.setImage(null); // Esto activa el icono de la cámara

        // Limpiar el Canvas de dibujo (donde se ven las marcas y el último frame)
        gcDraw.clearRect(0, 0, drawCanvas.getWidth(), drawCanvas.getHeight());

        // Limpiar el Canvas del Timeline (donde se ven los clips)
        gcTimeline.clearRect(0, 0, timelineCanvas.getWidth(), timelineCanvas.getHeight());

        // 5. Resetear el Scrollbar para que vuelva al inicio
        timelineScroll.setValue(0);
        timelineScroll.setMax(0);
        timelineScroll.setVisible(false);

        // 6. Refrescar la UI
        updateTimeLabel();
        if (lblStatus != null) {
            lblStatus.setText("Proyecto cerrado");
        }

        System.out.println("✅ Limpieza total completada.");
    }

    @FXML
    public void onDeleteSelectedShape() {
        if (selectedShapeToMove != null) {
            saveState(); // ✅ Permite deshacer el borrado

            // 1. Eliminar de la lista de dibujos activos
            shapes.remove(selectedShapeToMove);

            // 2. Eliminar de la caché del segmento actual
            VideoSegment activeSeg = getCurrentSegment();
            if (activeSeg != null && annotationsCache.containsKey(activeSeg.getId())) {
                annotationsCache.get(activeSeg.getId()).remove(selectedShapeToMove);
            }

            selectedShapeToMove = null;
            redrawVideoCanvas();
        }

        // Al ser una acción instantánea, devolvemos el foco al cursor
        setToolCursor();
    }

    @FXML
    public void onClearAllShapes() {
        VideoSegment activeSeg = getCurrentSegment();
        // Si no hay nada que borrar, no hacemos nada
        if (shapes.isEmpty() && (activeSeg == null || !annotationsCache.containsKey(activeSeg.getId()))) {
            setToolCursor();
            return;
        }

        saveState(); // ✅ Registra el estado antes de limpiar todo

        // 1. Limpiar dibujos en vivo
        shapes.clear();

        // 2. Limpiar dibujos guardados en este clip
        if (activeSeg != null) {
            annotationsCache.remove(activeSeg.getId());
        }

        selectedShapeToMove = null;
        redrawVideoCanvas();

        // Devolvemos el foco al cursor
        setToolCursor();
    }

    private String formatPreciseTime(double seconds) {
        int m = (int) seconds / 60;
        int s = (int) seconds % 60;
        // Formato mm:ss
        return String.format("%02d:%02d", m, s);
    }

}

