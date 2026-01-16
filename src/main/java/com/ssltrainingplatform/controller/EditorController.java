package com.ssltrainingplatform.controller;

import ai.djl.modality.cv.output.DetectedObjects;
import com.ssltrainingplatform.SslTpApp;
import com.ssltrainingplatform.manager.TimelineManager;
import com.ssltrainingplatform.model.DrawingShape;
import com.ssltrainingplatform.model.EditorState;
import com.ssltrainingplatform.model.VideoSegment;
import com.ssltrainingplatform.service.*;
import com.ssltrainingplatform.ui.renderer.CanvasRenderer;
import com.ssltrainingplatform.util.AppIcons;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Affine;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    private CanvasRenderer canvasRenderer;

    private TimelineManager timelineManager = new TimelineManager();

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
        canvasRenderer = new CanvasRenderer(gcDraw);

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
    @FXML
    public void setToolPen() {
        changeTool(ToolType.PEN);
    }

    @FXML
    public void setToolText() {
        changeTool(ToolType.TEXT);
    }

    @FXML
    public void setToolArrow() {
        changeTool(ToolType.ARROW);
    }
    @FXML
    public void setToolArrowDashed() {
        changeTool(ToolType.ARROW_DASHED);
    }

    @FXML
    public void setToolArrow3D() {
        changeTool(ToolType.ARROW_3D);
    }

    @FXML
    public void setToolSpotlight() {
        changeTool(ToolType.SPOTLIGHT);
    }

    @FXML
    public void setToolBase() {
        changeTool(ToolType.BASE);
    }

    @FXML
    public void setToolWall() {
        changeTool(ToolType.WALL);
    }

    @FXML
    public void setToolPolygon() {
        changeTool(ToolType.POLYGON);
    }

    @FXML
    public void setToolRectShaded() {
        changeTool(ToolType.RECT_SHADED);
    }

    @FXML
    public void setToolZoomCircle() {
        changeTool(ToolType.ZOOM_CIRCLE);
    }

    @FXML
    public void setToolZoomRect() {
        changeTool(ToolType.ZOOM_RECT);
    }

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

    private void finishPolyShape() {
        if (currentShape != null &&
                ("wall".equals(currentShape.getType()) || "polygon".equals(currentShape.getType()))) {
            List<Double> pts = currentShape.getPoints();

            if (pts.size() % 2 != 0) pts.remove(pts.size() - 1);

            if (pts.size() >= 4) {
                pts.remove(pts.size() - 1);
                pts.remove(pts.size() - 1);
            }

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

        currentTool = ToolType.CURSOR;
        if (btnCursor != null) btnCursor.setSelected(true);

        selectedShapeToMove = null;

        if (colorPicker != null) colorPicker.setValue(Color.web(shape.getColor()));
        if (sizeSlider != null) sizeSlider.setValue(shape.getStrokeWidth());

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

            VideoSegment currentSeg = getCurrentSegment();

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
                    if (s.getClipId() != null && currentSeg != null &&
                            !s.getClipId().equals(currentSeg.getId())) {
                        continue;
                    }

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
                if (currentSegTrack == null) return;

                if (currentDetections.isEmpty()) {
                    runAIDetectionManual();
                    return;
                }

                saveState();
                double minDist = 100.0;
                trackedObject = null;

                Image vidImg = videoView.getImage();
                double sc = Math.min(drawCanvas.getWidth() / vidImg.getWidth(),
                        drawCanvas.getHeight() / vidImg.getHeight());
                double vDW = vidImg.getWidth() * sc; double vDH = vidImg.getHeight() * sc;
                double oX = (drawCanvas.getWidth() - vDW) / 2.0;
                double oY = (drawCanvas.getHeight() - vDH) / 2.0;

                for (var obj : currentDetections) {
                    if (obj.getClassName().equals("person")) {
                        var r = obj.getBoundingBox().getBounds();
                        double cx = ((r.getX() + r.getWidth()/2.0) / 640.0) * vDW + oX;
                        double cy = ((r.getY() + r.getHeight()) / 640.0) * vDH + oY;

                        double dist = Math.sqrt(Math.pow(e.getX() - cx, 2) + Math.pow(e.getY() - cy, 2));

                        if (dist < minDist) {
                            trackedObject = obj;
                            minDist = dist;
                        }
                    }
                }

                if (trackedObject != null) {
                    var r = trackedObject.getBoundingBox().getBounds();
                    double cX = ((r.getX() + r.getWidth()/2.0) / 640.0) * vDW + oX;
                    double headY = (r.getY() / 640.0) * vDH + oY;
                    double footY = ((r.getY() + r.getHeight()) / 640.0) * vDH + oY;

                    trackingShape = new DrawingShape("track_" + System.currentTimeMillis(),
                            "tracking", cX, headY, toHex(colorPicker.getValue()));
                    trackingShape.setEndX(cX);
                    trackingShape.setEndY(footY);
                    trackingShape.setClipId(currentSegTrack.getId());

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
            }

            // ---------------------------------------------------------
            // 2. MODO CREACIÓN
            // ---------------------------------------------------------
            selectedShapeToMove = null;

            if (currentTool == ToolType.TEXT) {
                showFloatingInput(e.getX(), e.getY());
                return;
            }

            Color c = colorPicker != null ? colorPicker.getValue() : Color.RED;
            double s = currentStrokeWidth;

            if (currentTool == ToolType.PEN) {
                saveState();
                currentShape = new DrawingShape("pen"+System.currentTimeMillis(),
                        "pen", e.getX(), e.getY(), toHex(c));

                if (currentSeg != null) currentShape.setClipId(currentSeg.getId());

                currentShape.setStrokeWidth(s);
                currentShape.addPoint(e.getX(), e.getY());
                shapes.add(currentShape);
                redrawVideoCanvas();
                return;
            }

            if (currentTool == ToolType.POLYGON) {

                if (e.getClickCount() == 2 || e.getButton() == MouseButton.SECONDARY) {
                    finishPolyShape();
                    return;
                }

                if (currentShape == null || !"polygon".equals(currentShape.getType())) {
                    saveState();
                    currentShape = new DrawingShape("p"+System.currentTimeMillis(), "polygon", e.getX(), e.getY(), toHex(c));

                    if (currentSeg != null) currentShape.setClipId(currentSeg.getId());

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

            // ARRASTRE
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

            if ("arrow-3d".equals(type)) {
                currentShape.addPoint(e.getX(), e.getY() - 50);
            }
            shapes.add(currentShape);
        });

        drawCanvas.setOnMouseDragged(e -> {
            double dx = e.getX() - lastMouseX;
            double dy = e.getY() - lastMouseY;

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
                } else if (dragMode == 6 && "arrow-3d".equals(selectedShapeToMove.getType())) {
                    List<Double> pts = selectedShapeToMove.getPoints();
                    if (!pts.isEmpty()) {
                        pts.set(0, pts.get(0) + dx);
                        pts.set(1, pts.get(1) + dy);
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
        double dist = 20.0;

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

        if ("text".equals(s.getType())) {
            Text temp = new Text(s.getTextValue());
            temp.setFont(Font.font("Arial", FontWeight.BOLD, s.getStrokeWidth() * 2.5));
            double w = temp.getLayoutBounds().getWidth();
            double h = temp.getLayoutBounds().getHeight();
            double resizeX = s.getStartX() + w;
            double resizeY = s.getStartY() + h;

            if (Math.abs(mx - resizeX) < dist && Math.abs(my - resizeY) < dist) {
                dragMode = 5;
                return true;
            }
            return false;
        }

        if ("arrow-3d".equals(s.getType()) && !s.getPoints().isEmpty()) {
            double cx = s.getPoints().get(0);
            double cy = s.getPoints().get(1);
            if (Math.abs(mx - cx) < dist && Math.abs(my - cy) < dist) {
                dragMode = 6;
                return true;
            }
        }

        // C. FORMAS ESTÁNDAR (Flechas, Muro, Zoom, etc...)
        if (Math.abs(mx - s.getStartX()) < dist && Math.abs(my - s.getStartY()) < dist) {
            dragMode = 2;
            return true;
        }

        if (Math.abs(mx - s.getEndX()) < dist && Math.abs(my - s.getEndY()) < dist) {
            dragMode = 3;
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

        Image vidImg = (activeSeg != null && activeSeg.isFreezeFrame() && activeSeg.getThumbnail() != null)
                ? activeSeg.getThumbnail()
                : videoView.getImage();

        if (vidImg == null) return;

        double canvasW = drawCanvas.getWidth();
        double canvasH = drawCanvas.getHeight();
        double scale = Math.min(canvasW / vidImg.getWidth(), canvasH / vidImg.getHeight());
        double vidDispW = vidImg.getWidth() * scale;
        double vidDispH = vidImg.getHeight() * scale;
        double offX = (canvasW - vidDispW) / 2.0;
        double offY = (canvasH - vidDispH) / 2.0;

        gcDraw.drawImage(vidImg, offX, offY, vidDispW, vidDispH);

        // IA: Solo dibujamos los cuadros de detección si la herramienta de Tracking está activa
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
            if (s.getClipId() != null) {
                if (activeSegId == null || !activeSegId.equals(s.getClipId())) {
                    continue;
                }
            }
            Color c = Color.web(s.getColor());
            double size = s.getStrokeWidth();
            double x1 = s.getStartX(); 
            double y1 = s.getStartY();
            double x2 = s.getEndX(); 
            double y2 = s.getEndY();

            if (s == selectedShapeToMove) {
                canvasRenderer.drawSelectionOverlay(s, size, x1, y1, x2, y2);
            }
            canvasRenderer.drawShape(vidImg, vidDispW, vidDispH, offX, offY, s, c, size, x1, y1, x2, y2);
        }

        gcDraw.setEffect(null);
        gcDraw.setLineDashes(null);
    }

    private String toHex(Color c) {
        return String.format("#%02X%02X%02X", (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
    }

    private void showFloatingInput(double x, double y) {
        if (floatingInput == null) return;

        floatingInput.setTranslateX(x - (drawCanvas.getWidth() / 2) + 100);
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

                if (lastTimerTick == 0) {
                    lastTimerTick = now;
                    return;
                }

                if (btnPlayPause.getText().equals("||")) {
                    double delta = (now - lastTimerTick) / 1_000_000_000.0;
                    currentTimelineTime += delta;
                }

                lastTimerTick = now;

                Platform.runLater(() -> {
                    updateTimeLabel();
                    redrawTimeline();
                    redrawVideoCanvas();

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

            if (isPlayingFreeze) return;

            if (!videoService.isPlaying()) return;

            if (totalOriginalDuration > 0) {
                currentRealVideoTime = (percent / 100.0) * totalOriginalDuration;

                VideoSegment expectedSeg = getCurrentSegment();

                if (expectedSeg != null && expectedSeg.isFreezeFrame()) {
                    checkPlaybackJump();
                    return;
                }

                if (expectedSeg != null && !expectedSeg.isFreezeFrame()) {

                    double offsetInSegment = currentTimelineTime - expectedSeg.getStartTime();
                    double expectedRealTime = expectedSeg.getSourceStartTime() + offsetInSegment;

                    double drift = Math.abs(currentRealVideoTime - expectedRealTime);

                    if (drift > 0.25) {
                        System.out.println("Corrección de desfase: " + drift + "s. Sincronizando...");
                        performSafeSeek((expectedRealTime / totalOriginalDuration) * 100.0);
                    } else {
                        currentTimelineTime = expectedSeg.getStartTime() +
                                (currentRealVideoTime - expectedSeg.getSourceStartTime());
                    }

                    if (currentTimelineTime >= expectedSeg.getEndTime() - 0.05) {
                        jumpToNextSegment(expectedSeg);
                    }

                } else if (expectedSeg == null) {
                    jumpToNextSegment(null);
                }

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
                        if (trackingShape == null && activeSeg != null && videoService.isPlaying()) {
                            for (DrawingShape s : shapes) {
                                if ("tracking".equals(s.getType()) && activeSeg.getId().equals(s.getClipId())) {
                                    trackingShape = s;
                                    break;
                                }
                            }
                        }

                        // 2. ACTUALIZACIÓN DE POSICIÓN
                        if (trackingShape != null && videoService.isPlaying()) {
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
            if (isPlayingFreeze) {
                freezeTimer.stop();
                isPlayingFreeze = false;
            }

            if ("||".equals(btnPlayPause.getText())) {
                if (currentSeg == null) {
                    jumpToNextSegment(null);
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
        saveState();
        double gap = 0.05;

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

            activeSeg.setEndTime(splitTimeTimeline);
            activeSeg.setSourceEndTime(splitTimeSource);

            VideoSegment freezeSeg = new VideoSegment(
                    splitTimeTimeline + gap,
                    splitTimeTimeline + gap + duration,
                    splitTimeSource, splitTimeSource, "#00bcd4", true
            );

            if (videoView.getImage() != null) freezeSeg.setThumbnail(videoView.getImage());

            String colorRight = activeSeg.getColor().equals("#3b82f6") ? "#10b981" : "#3b82f6";
            VideoSegment rightSeg = new VideoSegment(
                    freezeSeg.getEndTime() + gap,
                    originalEndTimeTimeline + duration + (2 * gap),
                    splitTimeSource, originalEndTimeSource, colorRight, false
            );

            int idx = segments.indexOf(activeSeg);
            segments.add(idx + 1, freezeSeg);
            segments.add(idx + 2, rightSeg);

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

    @FXML
    public void onCutVideo() {
        saveState();
        if (segments.isEmpty()) return;
        VideoSegment activeSegment = null;

        for (VideoSegment seg : segments) {
            if (currentTimelineTime >= seg.getStartTime() && currentTimelineTime < seg.getEndTime()) {
                activeSegment = seg; break;
            }
        }

        if (activeSegment != null) {
            if (activeSegment.isFreezeFrame()) return;

            double offset = currentTimelineTime - activeSegment.getStartTime();

            // Evitar cortes muy cerca de los bordes
            if (offset < 0.5 || (activeSegment.getEndTime() - currentTimelineTime) < 0.5) return;

            double oldSourceEnd = activeSegment.getSourceEndTime();
            double oldTimelineEnd = activeSegment.getEndTime();
            double cutPointSource = activeSegment.getSourceStartTime() + offset;

            activeSegment.setEndTime(currentTimelineTime);
            activeSegment.setSourceEndTime(cutPointSource);

            double gap = 0.05;
            double newStartTime = currentTimelineTime + gap;
            double newDuration = oldTimelineEnd - currentTimelineTime;

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

            int idx = segments.indexOf(activeSegment);
            segments.add(idx + 1, newSegment);

            for(int i = idx + 2; i < segments.size(); i++) {
                VideoSegment s = segments.get(i);
                s.setStartTime(s.getStartTime() + gap);
                s.setEndTime(s.getEndTime() + gap);
            }

            totalTimelineDuration += gap;

            selectedSegment = newSegment;
            updateScrollbarAndRedraw();
        }
    }

    @FXML
    public void onDeleteSegment() {
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

    private void selectSegmentUnderPlayhead() {
        for (VideoSegment seg : segments) {
            if (currentTimelineTime >= seg.getStartTime() && currentTimelineTime < seg.getEndTime()) {
                selectedSegment = seg;
                break;
            }
        }
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
        for (VideoSegment seg : segments) {
            if (isDraggingTimeline && seg == segmentBeingDragged) continue;

            double startX = (seg.getStartTime() * pixelsPerSecond) - scrollOffset;
            double width = seg.getDuration() * pixelsPerSecond;

            if (startX + width > 0 && startX < w) {
                drawStyledClip(gcTimeline, seg, startX, topMargin, width, barHeight, 1.0);
            }
        }

        if (isDraggingTimeline && segmentBeingDragged != null) {
            double ghostW = segmentBeingDragged.getDuration() * pixelsPerSecond;
            double ghostX = currentDragX - (ghostW / 2.0);
            drawStyledClip(gcTimeline, segmentBeingDragged, ghostX, topMargin - 5, ghostW, barHeight + 10, 0.6);
        }

        if (isDraggingTimeline && segmentBeingDragged != null && dropIndicatorX != -1) {

            gcTimeline.setStroke(Color.YELLOW);
            gcTimeline.setLineWidth(1.0);
            gcTimeline.strokeLine(dropIndicatorX, 0, dropIndicatorX, h); // De arriba a abajo

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

            gcTimeline.setStroke(Color.web("#ffffff", 0.4));
            gcTimeline.setLineWidth(1.0);
            gcTimeline.strokeLine(hX, 0, hX, h);

            String timeText = formatPreciseTime(hoverTime);

            gcTimeline.setFont(Font.font("Arial", FontWeight.BOLD, 10));
            Text temp = new Text(timeText);
            temp.setFont(gcTimeline.getFont());
            double txtW = temp.getLayoutBounds().getWidth();

            double rectW = txtW + 10;
            double rectH = 16;
            double rectX = hX - (rectW / 2);
            double rectY = 12;

            gcTimeline.setFill(Color.web("#1a1a1a", 0.9));
            gcTimeline.fillRoundRect(rectX, rectY, rectW, rectH, 5, 5);

            gcTimeline.setStroke(Color.web("#ffffff", 0.2));
            gcTimeline.strokeRoundRect(rectX, rectY, rectW, rectH, 5, 5);

            gcTimeline.setFill(Color.WHITE);
            gcTimeline.fillText(timeText, rectX + 5, rectY + 12);
        }
    }

    private void drawStyledClip(GraphicsContext gc, VideoSegment seg, double x, double y, double w, double h,
                                double alpha) {
        gc.save();

        Color c = Color.web(seg.getColor());
        if (alpha < 1.0) {
            c = new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
        }

        double arcSize = 10;

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

        gc.save();
        gc.clip();

        boolean hasImages = !seg.isFreezeFrame() && !filmstripMap.isEmpty();

        if (hasImages) {
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
                    double imgX = x + pixelOffset;
                    double imgW = step * pixelsPerSecond;

                    gc.drawImage(img, imgX, y, imgW, h);
                }
            }
            gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.35 * alpha));
            gc.fillRect(x, y, w, h);
        } else {
            gc.setFill(c);
            gc.fillRect(x, y, w, h);
        }

        gc.restore();

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

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        if (w > 40) {
            String label = String.format("%.1fs", seg.getDuration());
            if (seg.isFreezeFrame()) label = "❄ " + label;

            Text tempText = new Text(label);
            tempText.setFont(gc.getFont());
            double textWidth = tempText.getLayoutBounds().getWidth();

            gc.fillText(label, x + (w - textWidth)/2, y + h/2 + 4);
        }

        gc.restore();
    }

    private void drawRulerAndPlayhead(double w, double h, double scrollOffset) {
        int stepTime = (pixelsPerSecond > 80) ? 1 : (pixelsPerSecond > 40) ? 5 : (pixelsPerSecond > 20) ? 10 : 30;
        int startSec = ((int) (scrollOffset / pixelsPerSecond) / stepTime) * stepTime;
        int endSec = (int) ((scrollOffset + w) / pixelsPerSecond) + 1;

        gcTimeline.setStroke(Color.GRAY);
        gcTimeline.setLineWidth(0.7);

        for (int i = startSec; i <= endSec; i += stepTime) {
            double x = (i * pixelsPerSecond) - scrollOffset;

            gcTimeline.strokeLine(x, 0, x, 10);

            gcTimeline.setFill(Color.GRAY);
            gcTimeline.setFont(Font.font("Arial", 8));
            gcTimeline.fillText(formatShortTime(i), x + 2, 9);

            if (stepTime >= 5) {
                for (int j = 1; j < stepTime; j++) {
                    double sx = x + j * pixelsPerSecond;
                    if (sx - scrollOffset < w) {
                        gcTimeline.strokeLine(sx, 0, sx, 3);
                    }
                }
            }
        }

        if (hoverTime >= 0 && hoverTime <= totalTimelineDuration) {
            double hX = (hoverTime * pixelsPerSecond) - scrollOffset;

            gcTimeline.setStroke(Color.web("#ffffff", 0.3));
            gcTimeline.strokeLine(hX, 0, hX, h);

            String timeStr = formatPreciseTime(hoverTime);
            gcTimeline.setFont(Font.font("Arial", FontWeight.BOLD, 9));

            double rectW = 34; double rectH = 14;
            double rectX = hX - (rectW / 2);
            double rectY = 12;

            gcTimeline.setFill(Color.web("#1a1a1a", 0.9));
            gcTimeline.fillRoundRect(rectX, rectY, rectW, rectH, 4, 4);
            gcTimeline.setFill(Color.WHITE);
            gcTimeline.fillText(timeStr, rectX + 4, rectY + 10);
        }

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
            timelineScroll.setVisible(false);
            timelineScroll.setManaged(false);
            return;
        }

        double canvasW = timelineCanvas.getWidth();
        double totalW = totalTimelineDuration * pixelsPerSecond;

        boolean needsScroll = totalW > canvasW;
        timelineScroll.setVisible(needsScroll);
        timelineScroll.setManaged(needsScroll);

        if (needsScroll) {
            timelineScroll.setMax(Math.max(0, totalW - canvasW));
            timelineScroll.setVisibleAmount((canvasW / totalW) * timelineScroll.getMax());
        }

        redrawTimeline();
    }

    private void updateTimeLabel() {
        int m = (int) currentTimelineTime / 60; int s = (int) currentTimelineTime % 60;
        lblTime.setText(String.format("%02d:%02d", m, s) + " / " + String.format("%02d:%02d",
                (int)totalTimelineDuration/60, (int)totalTimelineDuration%60));
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

            System.out.println("🎥 MODO LOCAL: Video cargado desde " + this.localVideoPath);

            if (loadingSpinner != null) loadingSpinner.setVisible(true);

            gcTimeline.clearRect(0,0, timelineCanvas.getWidth(), timelineCanvas.getHeight());

            try {
                videoService.loadVideo(f.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
            }

            Task<TreeMap<Double, Image>> task = new Task<>() {
                @Override
                protected TreeMap<Double, Image> call() {
                    // Intervalo de 5 segundos, altura de miniatura 80px (para aprovechar la nueva altura)
                    return new FilmstripService().generateFilmstrip(f, 5.0, 80);
                }
            };

            task.setOnSucceeded(event -> {
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
                    updateTimeLabel();
                    updateScrollbarAndRedraw();
                    redrawVideoCanvas();
                    if (btnPlayPause != null) btnPlayPause.setText("▶");
                });

                if (timelineScroll != null) {
                    timelineScroll.setValue(0);
                    timelineScroll.setMax(totalTimelineDuration * pixelsPerSecond);
                }

                updateScrollbarAndRedraw();
                videoService.pause();
                videoService.seek(0);
            });

            task.setOnFailed(e -> {
                if (loadingSpinner != null) loadingSpinner.setVisible(false);
                System.err.println("Error generando miniaturas");
            });

            new Thread(task).start();
        }
    }

    private void performSafeSeek(double percent) {
        ignoreUpdatesUntil = System.currentTimeMillis() + 150;
        videoService.seek(percent);
    }

    private void seekTimeline(double mouseX) {
        resetTracking();

        autoSaveActiveSegmentDrawings();

        double scrollOffset = timelineScroll.getValue();
        double time = (mouseX + scrollOffset) / pixelsPerSecond;

        if (time < 0) time = 0;
        if (time > totalTimelineDuration) time = totalTimelineDuration;

        currentTimelineTime = time;

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
            for (VideoSegment s : segments) {
                if (s.getStartTime() > time) {
                    performSafeSeek((s.getSourceStartTime() / totalOriginalDuration) * 100.0);
                    break;
                }
            }
        }

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
                if (videoService.isPlaying()) onPlayPause();
                videoService.seek(100);
            }
            redrawTimeline();
            updateTimeLabel();
        }
    }

    private VideoSegment getCurrentSegment() {
        double epsilon = 0.05; // 50 milisegundos de margen de error
        for (VideoSegment seg : segments) {
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
        double editGap = 0.05;

        for (int i = 0; i < segments.size(); i++) {
            VideoSegment seg = segments.get(i);
            double duration = seg.getDuration();

            seg.setStartTime(currentPos);
            seg.setEndTime(currentPos + duration);

            currentPos = seg.getEndTime() + editGap;
        }

        totalTimelineDuration = currentPos - editGap;
        updateScrollbarAndRedraw();
    }

    private int getNextSegmentIndexByTime() {
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).getStartTime() > currentTimelineTime) {
                return i;
            }
        }
        return segments.size();
    }

    private void saveState() {
        if (segments == null) return;

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

        List<DrawingShape> shapesSnapshot = new ArrayList<>();
        for (DrawingShape sh : shapes) {
            shapesSnapshot.add(sh.copy());
        }

        undoStack.push(new EditorState(segmentsSnapshot, shapesSnapshot, totalTimelineDuration));
        redoStack.clear();
        updateUndoRedoButtons();
    }

    private void updateUndoRedoButtons() {
        if (btnUndo != null) btnUndo.setDisable(undoStack.isEmpty());
        if (btnRedo != null) btnRedo.setDisable(redoStack.isEmpty());
    }

    @FXML
    public void onUndo() {
        if (undoStack.isEmpty()) return;

        redoStack.push(new EditorState(segments, shapes, totalTimelineDuration));

        restoreState(undoStack.pop());
        updateUndoRedoButtons();
    }

    @FXML
    public void onRedo() {
        if (redoStack.isEmpty()) return;

        undoStack.push(new EditorState(new ArrayList<>(segments), new ArrayList<>(shapes), totalTimelineDuration));

        restoreState(redoStack.pop());
        updateUndoRedoButtons();
    }

    private void restoreState(EditorState state) {
        this.segments = new ArrayList<>(state.segmentsSnapshot);
        this.shapes = new ArrayList<>(state.shapesSnapshot);
        this.totalTimelineDuration = state.durationSnapshot;

        updateScrollbarAndRedraw();
        redrawVideoCanvas();
        redrawTimeline();
        updateTimeLabel();
    }

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

        new Thread(() -> {
            try {
                List<LocalExportService.ExportJobSegment> jobSegments = new ArrayList<>();

                for (VideoSegment seg : segments) {
                    if (seg.isFreezeFrame()) {
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

                Platform.runLater(() -> lblStatus.setText("Renderizando video (FFmpeg)..."));

                LocalExportService exportService = new LocalExportService();
                exportService.renderProject(localVideoPath, jobSegments, destFile);

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

    private void mostrarAlerta(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    private void setupTooltips() {

        addTooltip(btnUndo, "Deshacer");
        addTooltip(btnRedo, "Rehacer");

        addTooltip(btnCursor, "Cursor - Seleccionar y mover ediciones");
        addTooltip(btnPen, "Lápiz - Dibujo libre a mano alzada");
        addTooltip(btnText, "Texto - Añadir etiquetas de texto (Clic + Escribir + Enter)");

        addTooltip(btnArrow, "Flecha Simple");
        addTooltip(btnArrowDashed, "Flecha Discontinua (Movimiento de balón/jugador)");
        addTooltip(btnArrow3D, "Flecha Curva 3D");

        addTooltip(btnSpotlight, "Foco (Spotlight) - Resaltar zona");
        addTooltip(btnBase, "Base - Círculo de jugador");
        addTooltip(btnWall, "Muro - Pared defensiva 3D");

        addTooltip(btnRectangle, "Rectángulo (Hueco)");
        addTooltip(btnRectShaded, "Zona (Rectángulo relleno)");
        addTooltip(btnPolygon, "Polígono - Área libre (Clic para puntos, Doble clic para cerrar)");
        addTooltip(btnCurve, "Curva - Línea curva con punto de control");

        addTooltip(btnZoomCircle, "Lupa Circular - Ampliar detalle");
        addTooltip(btnZoomRect, "Lupa Rectangular - Ampliar zona");

        addTooltip(btnSkipStart, "Ir al inicio");
        addTooltip(btnSkipEnd, "Ir al final");

        addTooltip(btnTracking, "Tracking - Seguimiento automático de jugadores");

        addTooltip(btnDeleteShape, "Borrar edición");
        addTooltip(btnClearCanvas, "Limpiar dibujo");
    }

    private void addTooltip(Control node, String text) {
        if (node != null) {
            Tooltip t = new Tooltip(text);
            t.setShowDelay(javafx.util.Duration.millis(200));
            t.setStyle("-fx-font-size: 12px; -fx-background-color: #333; -fx-text-fill: white;");
            node.setTooltip(t);
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

            List<DrawingShape> allToDraw = new ArrayList<>();
            List<DrawingShape> cached = annotationsCache.get(seg.getId());
            if (cached != null) allToDraw.addAll(cached);

            for (DrawingShape s : shapes) {
                if (seg.getId().equals(s.getClipId())) {
                    if (cached == null || !cached.contains(s)) {
                        allToDraw.add(s);
                    }
                }
            }

            for (DrawingShape s : allToDraw) {
                drawShapeScaledToVideo(gc, s, offX, offY, vidDispW, vidDispH, exportW, exportH, bg);
            }

            WritableImage snap = tempCanvas.snapshot(null, null);
            BufferedImage bImage = SwingFXUtils.fromFXImage(snap, null);

            File tempFile = File.createTempFile("freeze_", ".png");
            ImageIO.write(bImage, "png", tempFile);

            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void drawShapeScaledToVideo(GraphicsContext gc, DrawingShape s,
                                        double offX, double offY,
                                        double vidDispW, double vidDispH,
                                        double exportW, double exportH, Image bg) {

        double sx = exportW / vidDispW;
        double sy = exportH / vidDispH;

        double x1 = (s.getStartX() - offX) * sx;
        double y1 = (s.getStartY() - offY) * sy;
        double x2 = (s.getEndX() - offX) * sx;
        double y2 = (s.getEndY() - offY) * sy;

        double size = s.getStrokeWidth() * sx;

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

        gc.setLineWidth(size / 3.0);

        if (dashed) gc.setLineDashes(10 * size/20.0, 10 * size/20.0);
        else gc.setLineDashes(null);

        double angle = Math.atan2(y2 - y1, x2 - x1);
        double headLen = size * 1.5;

        gc.strokeLine(x1, y1, x2 - (headLen * 0.8 * Math.cos(angle)), y2 - (headLen * 0.8 * Math.sin(angle)));

        gc.setLineDashes(null);
        drawArrowHeadOnGC(gc, x2, y2, angle, size, color);
    }

    private void drawArrowHeadOnGC(GraphicsContext gc, double x, double y, double angle, double size, Color color) {
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

    private void drawCurvedArrowOnGC(GraphicsContext gc, double x1, double y1, double cx, double cy, double x2,
                                     double y2, Color color, double size) {
        gc.setStroke(color);
        gc.setFill(color);
        gc.setLineWidth(size / 3.0);

        double angle = Math.atan2(y2 - cy, x2 - cx);
        double headLen = size * 1.5;

        double lineEndX = x2 - (headLen * 0.5) * Math.cos(angle);
        double lineEndY = y2 - (headLen * 0.5) * Math.sin(angle);

        gc.beginPath();
        gc.moveTo(x1, y1);
        gc.quadraticCurveTo(cx, cy, lineEndX, lineEndY);
        gc.stroke();

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
        double l = Math.min(x1, x2);
        double t = Math.min(y1, y2);
        double w = Math.abs(x2-x1);
        double h = Math.abs(y2-y1);
        gc.setFill(new Color(c.getRed(), c.getGreen(), c.getBlue(), 0.3));
        gc.fillRect(l, t, w, h);
        gc.strokeRect(l, t, w, h);
    }

    private void drawProSpotlightOnGC(GraphicsContext gc, double x1, double y1, double x2, double y2, Color c, double size) {
        double rxTop = size * 1.5;
        double ryTop = rxTop * 0.3;
        double rxBot = size * 3.0 + Math.abs(x2-x1)*0.2;
        double ryBot = rxBot * 0.3;
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

        gc.drawImage(img, 0, 0, exportW, exportH);

        gc.restore();

        gc.setStroke(c);
        gc.setLineWidth(2.0 * sx);
        if (circle) gc.strokeOval(left, top, w, h);
        else gc.strokeRect(left, top, w, h);
    }

    private void onTimelineDragged(MouseEvent e) {

        double topMargin = 22;
        double canvasW = timelineCanvas.getWidth();
        double edgeThreshold = 70.0; // Distancia al borde para activar el scroll

        double scrollOffset = timelineScroll.getValue();

        if (segmentBeingDragged != null && e.getY() >= topMargin) {
            isDraggingTimeline = true;
            currentDragX = e.getX();

            if (e.getX() > canvasW - edgeThreshold) {
                timelineScroll.setValue(scrollOffset + 15);
            } else if (e.getX() < edgeThreshold && scrollOffset > 0) {
                timelineScroll.setValue(scrollOffset - 15);
            }

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

            this.dropIndex = newDropIndex;
            this.dropIndicatorX = (indicatorTime * pixelsPerSecond) - timelineScroll.getValue();

            redrawTimeline();

        } else {
            long now = System.currentTimeMillis();
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

                saveState();

                selectedSegment.setEndTime(selectedSegment.getStartTime() + newDuration);

                recalculateSegmentTimes();
                redrawTimeline();
            } catch (NumberFormatException e) {
                mostrarAlerta("Error", "Introduce un número válido.");
            }
        }
    }

    private void autoSaveActiveSegmentDrawings() {

        if (shapes.isEmpty()) return;

        VideoSegment activeSeg = getCurrentSegment();
        if (activeSeg == null || !activeSeg.isFreezeFrame()) return;

        String segmentId = activeSeg.getId();

        List<DrawingShape> drawingsToPersist = new ArrayList<>(shapes);
        annotationsCache.computeIfAbsent(segmentId, k -> new ArrayList<>()).addAll(drawingsToPersist);

        for(DrawingShape s : drawingsToPersist) {
            s.setClipId(segmentId);
        }

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
                    if (!btnToggleAI.isSelected()) {
                        this.currentDetections.clear();
                        redrawVideoCanvas();
                        isProcessingAI.set(false);
                        return;
                    }

                    this.currentDetections = results;
                    redrawVideoCanvas();

                    if (lblStatus != null) lblStatus.setText("✅ Análisis listo");
                    isProcessingAI.set(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> isProcessingAI.set(false));
            }
        });
    }

    private void resetTracking() {
        this.trackedObject = null;
        this.trackingShape = null;
        this.currentDetections.clear();

        if (btnToggleAI != null) btnToggleAI.setSelected(false);
        this.currentTool = ToolType.CURSOR;
        if (btnCursor != null) btnCursor.setSelected(true);

        redrawVideoCanvas();
    }

    @FXML
    public void onCloseVideo() {
        if (videoService != null) videoService.pause();

        if (isPlayingFreeze) {
            freezeTimer.stop();
            isPlayingFreeze = false;
        }

        segments.clear();
        shapes.clear();
        annotationsCache.clear();
        filmstripMap.clear();

        currentTimelineTime = 0.0;
        totalTimelineDuration = 0.0;
        serverVideoId = null;
        selectedSegment = null;
        selectedShapeToMove = null;

        videoView.setImage(null);

        gcDraw.clearRect(0, 0, drawCanvas.getWidth(), drawCanvas.getHeight());

        gcTimeline.clearRect(0, 0, timelineCanvas.getWidth(), timelineCanvas.getHeight());

        timelineScroll.setValue(0);
        timelineScroll.setMax(0);
        timelineScroll.setVisible(false);

        updateTimeLabel();

        if (lblStatus != null) {
            lblStatus.setText("Proyecto cerrado");
        }

        System.out.println("✅ Limpieza total completada.");
    }

    @FXML
    public void onDeleteSelectedShape() {
        if (selectedShapeToMove != null) {
            saveState();

            shapes.remove(selectedShapeToMove);

            VideoSegment activeSeg = getCurrentSegment();
            if (activeSeg != null && annotationsCache.containsKey(activeSeg.getId())) {
                annotationsCache.get(activeSeg.getId()).remove(selectedShapeToMove);
            }

            selectedShapeToMove = null;
            redrawVideoCanvas();
        }

        setToolCursor();
    }

    @FXML
    public void onClearAllShapes() {
        VideoSegment activeSeg = getCurrentSegment();

        if (shapes.isEmpty() && (activeSeg == null || !annotationsCache.containsKey(activeSeg.getId()))) {
            setToolCursor();
            return;
        }

        saveState();

        shapes.clear();

        if (activeSeg != null) {
            annotationsCache.remove(activeSeg.getId());
        }

        selectedShapeToMove = null;
        redrawVideoCanvas();

        setToolCursor();
    }

    private String formatPreciseTime(double seconds) {
        int m = (int) seconds / 60;
        int s = (int) seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

}

