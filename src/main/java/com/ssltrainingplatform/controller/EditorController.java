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
import com.ssltrainingplatform.util.SimpleKalmanTracker;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
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

    private VideoSegment selectedSegment = null;

    private double currentTimelineTime = 0.0;
    private double currentRealVideoTime = 0.0;
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
        POLYGON, RECT_SHADED, ZOOM_CIRCLE, ZOOM_RECT, TRACKING, LINE_DEFENSE
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

    @FXML private ProgressIndicator spinnerVideo;
    @FXML private ProgressIndicator spinnerTimeline;

    // --- VARIABLES DE BLOQUEO ---
    @FXML private StackPane overlayVideo;
    @FXML private StackPane overlayTimeline;
    @FXML private GridPane controlsGrid;

    private DrawingShape textShapeBeingEdited = null;

    private SimpleKalmanTracker kalmanFilter = null;

    private int framesLost = 0;

    private int frameSkipCounter = 0;

    private int framesTracked = 0;

    private double lastProcessedTime = -1;

    @FXML private ToggleButton btnLineDefense;

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
            aiService.loadModel("models/yolo11n.torchscript");
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
            colorPicker.setValue(Color.web("#f85d5d"));
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

        // -----------------------------------------------------------------
        // ✅ SOLUCIÓN MAESTRA: Redibujar canvas automáticamente cuando cambie el frame
        // Esto garantiza que en cuanto el seek termine y cargue la foto, la veamos.
        videoView.imageProperty().addListener((obs, oldImg, newImg) -> {
            if (newImg != null) {
                redrawVideoCanvas();
            }
        });

        timelineCanvas.setOnMouseMoved(e -> {
            // 1. Calcular tiempo bajo el ratón (Línea Gris)
            double scrollOffset = timelineScroll.getValue();
            hoverTime = (e.getX() + scrollOffset) / pixelsPerSecond;

            long now = System.currentTimeMillis();

            // 2. SCRUBBING: Actualizar video solo cada 100ms
            if (now - lastScrubTime > 100) {
                // ¡AQUÍ EL CAMBIO! Usamos el método que SOLO previsualiza
                previewVideoOnly(hoverTime);
                lastScrubTime = now;
            }

            redrawTimeline(); // Dibuja la línea gris en hoverTime y la roja en su sitio
        });

        timelineCanvas.setOnMouseExited(e -> {
            hoverTime = -1;

            // 3. AL SALIR: El video vuelve a la posición real de la aguja roja
            previewVideoOnly(currentTimelineTime);

            redrawTimeline();
        });

        // --- CONFIGURACIÓN DE LOS SPINNERS Y BLOQUEOS ---

        // Spinner Video
        if (spinnerVideo != null) {
            spinnerVideo.setMouseTransparent(true); // El spinner en sí no bloquea clic
        }
        // Overlay Video (Este SÍ bloquea clics al estar visible)
        if (overlayVideo != null) {
            overlayVideo.setVisible(false);
        }

        // Spinner Timeline
        if (spinnerTimeline != null) {
            spinnerTimeline.setMouseTransparent(true);
        }
        // Overlay Timeline
        if (overlayTimeline != null) {
            overlayTimeline.setVisible(false);
        }

        // Empezamos ocultos
        setLoading(false);

        timelineCanvas.setCursor(Cursor.DEFAULT);

    }

    private void processMultiTrackingLogic(List<DetectedObjects.DetectedObject> results, boolean hasAIData) {
        VideoSegment activeSeg = timelineManager.getSegmentAt(currentTimelineTime);

        // --- FIX PERSISTENCIA: Si es tracking, permitimos que siga vivo aunque cambie el segmento ---
        // (Esto evita que desaparezca al salir del FreezeFrame y entrar en el video)

        Image img = videoView.getImage();
        if (img == null) return;

        // Cálculos de escala
        double sc = Math.min(drawCanvas.getWidth() / img.getWidth(), drawCanvas.getHeight() / img.getHeight());
        double vDW = img.getWidth() * sc; double vDH = img.getHeight() * sc;
        double oX = (drawCanvas.getWidth() - vDW) / 2.0; double oY = (drawCanvas.getHeight() - vDH) / 2.0;

        for (DrawingShape shape : this.shapes) {

            boolean isTrackingType = "tracking".equals(shape.getType()) || "line_defense".equals(shape.getType());

            // A. FILTRO DE SEGURIDAD MODIFICADO
            // Si NO es tracking, aplicamos la restricción estricta de ID.
            // Si ES tracking, permitimos que se dibuje para mantener la continuidad.
            if (!isTrackingType) {
                if (activeSeg == null || shape.getClipId() == null || !shape.getClipId().equals(activeSeg.getId())) {
                    continue;
                }
            }

            // --- TIPO 1: TRACKING INDIVIDUAL ---
            if ("tracking".equals(shape.getType())) {
                if (kalmanFilter == null) kalmanFilter = new SimpleKalmanTracker(shape.getEndX(), shape.getEndY());
                kalmanFilter.predict();

                if (hasAIData && results != null) {
                    DetectedObjects.DetectedObject best = findClosestHuman(results, kalmanFilter.getX(), kalmanFilter.getY(), vDW, vDH, oX, oY);
                    if (best != null) {
                        var pt = convertToScreenCoords(best, vDW, vDH, oX, oY);
                        kalmanFilter.update(pt.x, pt.y);

                        // Actualizar posición Pies
                        shape.setEndX(pt.x); shape.setEndY(pt.y);

                        // Calcular altura Cabeza (Bounding Box) y guardar
                        var r = best.getBoundingBox().getBounds();
                        double headY = (r.getY() / 640.0) * vDH + oY;
                        shape.setStartX(pt.x); shape.setStartY(headY); // Start es la cabeza
                        shape.setUserData(pt.y - headY); // Guardar altura
                    }
                } else {
                    // Inercia
                    double currentH = (shape.getUserData() instanceof Double) ? (Double) shape.getUserData() : 60.0;
                    shape.setEndX(kalmanFilter.getX()); shape.setEndY(kalmanFilter.getY());
                    shape.setStartX(kalmanFilter.getX()); shape.setStartY(kalmanFilter.getY() - currentH);
                }
            }

            // --- TIPO 2: DEFENSA EN LÍNEA (MULTI JUGADOR) ---
            else if ("line_defense".equals(shape.getType())) {
                List<SimpleKalmanTracker> trackers = shape.getInternalTrackers();
                List<java.awt.Point.Double> visualPoints = shape.getKeyPoints(); // Puntos actuales en pantalla

                // 1. SINCRONIZACIÓN DE SEGURIDAD (CRUCIAL)
                // Si acabas de dibujar, tienes puntos visuales pero NO tienes trackers (cerebros).
                // O si ha cambiado el número de puntos, reiniciamos todo para que coincida.
                if (trackers.isEmpty() || trackers.size() != visualPoints.size()) {
                    trackers.clear();
                    for (java.awt.Point.Double p : visualPoints) {
                        // Creamos un tracker nuevo exactamente donde está el punto dibujado
                        trackers.add(new SimpleKalmanTracker(p.x, p.y));
                    }
                }

                // Map para alturas (para el triángulo visual)
                Map<Integer, Double> playerHeights;
                if (shape.getUserData() instanceof Map) {
                    playerHeights = (Map<Integer, Double>) shape.getUserData();
                } else {
                    playerHeights = new HashMap<>();
                    shape.setUserData(playerHeights);
                }

                // 2. BUCLE DE TRACKING INDIVIDUAL PARA CADA JUGADOR
                for (int i = 0; i < trackers.size(); i++) {
                    SimpleKalmanTracker tracker = trackers.get(i);

                    // A. Predecir movimiento (Inercia)
                    tracker.predict();

                    if (hasAIData && results != null) {
                        // B. Buscar el humano más cercano a la predicción de ESTE punto específico
                        DetectedObjects.DetectedObject best = findClosestHuman(results, tracker.getX(), tracker.getY(), vDW, vDH, oX, oY);

                        if (best != null) {
                            var pt = convertToScreenCoords(best, vDW, vDH, oX, oY);

                            // C. Corregir el tracker con el dato real de la IA
                            tracker.update(pt.x, pt.y);

                            // D. Actualizar el dibujo visual
                            shape.updateKeyPointPosition(i, pt.x, pt.y);

                            // E. Calcular y guardar altura para el triángulo
                            var r = best.getBoundingBox().getBounds();
                            double headY = (r.getY() / 640.0) * vDH + oY;
                            double height = pt.y - headY;
                            playerHeights.put(i, height);
                        }
                    } else {
                        // Si no hay IA en este frame (frame intermedio), usamos la predicción del tracker
                        // para mover el dibujo suavemente.
                        shape.updateKeyPointPosition(i, tracker.getX(), tracker.getY());
                    }
                }
            }
        }
        redrawVideoCanvas();
    }

    private void updateTrackingShapePosition() {
        if (kalmanFilter == null || trackingShape == null) return;

        // Recuperar altura guardada o calcular una por defecto
        double currentHeight = 60.0; // Valor default
        if (trackingShape.getUserData() instanceof Double) {
            currentHeight = (Double) trackingShape.getUserData();
        } else {
            // Fallback: altura actual del dibujo
            currentHeight = trackingShape.getEndY() - trackingShape.getStartY();
        }

        trackingShape.setEndX(kalmanFilter.getX());
        trackingShape.setEndY(kalmanFilter.getY());
        trackingShape.setStartX(kalmanFilter.getX());
        trackingShape.setStartY(kalmanFilter.getY() - currentHeight);
    }

    private void setLoading(boolean isLoading) {
        // 1. SPINNER Y OVERLAY VIDEO
        if (spinnerVideo != null) spinnerVideo.setVisible(isLoading);
        if (overlayVideo != null) overlayVideo.setVisible(isLoading);

        // 2. SPINNER Y OVERLAY TIMELINE
        if (spinnerTimeline != null) spinnerTimeline.setVisible(isLoading);
        if (overlayTimeline != null) overlayTimeline.setVisible(isLoading);

        // 3. BLOQUEAR LOS BOTONES (Play, Exportar, etc.)
        if (controlsGrid != null) {
            controlsGrid.setDisable(isLoading);
        }
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

        // 1. MOVIMIENTO INMEDIATO DEL PLAYHEAD Y VIDEO
        // Esto asegura que "lo lleve allí" siempre, antes de calcular selecciones
        seekTimeline(e.getX(), true);

        double scrollOffset = timelineScroll.getValue();
        double clickTime = (e.getX() + scrollOffset) / pixelsPerSecond;

        if (timelineContextMenu != null) timelineContextMenu.hide();

        VideoSegment clickedSeg = null;
        for (VideoSegment seg : timelineManager.getSegments()) {
            if (clickTime >= seg.getStartTime() && clickTime <= seg.getEndTime()) {
                clickedSeg = seg;
                break;
            }
        }

        // Clic Derecho
        if (e.getButton() == MouseButton.SECONDARY) {
            selectedSegment = clickedSeg;
            if (selectedSegment != null) {
                timelineContextMenu.show(timelineCanvas, e.getScreenX(), e.getScreenY());
            }
            redrawTimeline();
            return;
        }

        // Clic Izquierdo (Selección)
        selectedSegment = clickedSeg;
        segmentBeingDragged = null;
        isDraggingTimeline = false;

        if (clickedSeg != null) {
            saveState();
            segmentBeingDragged = clickedSeg;
            currentDragX = e.getX();
        }

        redrawTimeline();
    }

    private void onTimelineReleased(MouseEvent e) {
        if (isDraggingTimeline && segmentBeingDragged != null) {

            timelineManager.moveSegment(segmentBeingDragged, dropIndex);

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
        if(btnLineDefense != null) btnLineDefense.setGraphic(AppIcons.getIcon("line-defense", 28));
    }

    // --- SELECTORES HERRAMIENTAS ---
    @FXML
    public void setToolLineDefense() {
        //changeTool(ToolType.LINE_DEFENSE);
        //// Activamos IA automáticamente porque esta herramienta LA NECESITA
        //if (btnToggleAI != null && !btnToggleAI.isSelected()) {
          //  btnToggleAI.setSelected(true);
        //}
        //runAIDetectionManual(); // Analizar frame actual para facilitar clics
    }

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
        //changeTool(ToolType.TRACKING);
        //// Forzamos la activación del botón de IA si no está puesto
        //if (btnToggleAI != null && !btnToggleAI.isSelected()) {
          //  btnToggleAI.setSelected(true);
           // System.out.println("DEBUG: IA activada automáticamente para Tracking.");
        //}

        //runAIDetectionManual();
    }

    private void changeTool(ToolType type) {
        finishPolyShape();
        currentTool = type;
        selectedShapeToMove = null;
        redrawVideoCanvas();
    }

    private void finishPolyShape() {
        if (currentShape != null &&
                ("wall".equals(currentShape.getType()) ||
                        "polygon".equals(currentShape.getType()) ||
                        "line_defense".equals(currentShape.getType()))) {

            // Lógica específica para línea defensiva
            if ("line_defense".equals(currentShape.getType())) {
                if (currentShape.getKeyPoints().size() < 2) {
                    shapes.remove(currentShape); // Borrar si tiene menos de 2 jugadores
                } else {
                    switchToCursorAndSelect(currentShape);
                }
                currentShape = null;
                redrawVideoCanvas();
                return;
            }

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
        drawCanvas.setOnMousePressed(this::onCanvasPressed);
        drawCanvas.setOnMouseDragged(this::onCanvasDragged);
        drawCanvas.setOnMouseReleased(this::onCanvasReleased);
    }

    private void onCanvasPressed(MouseEvent e) {
        lastMouseX = e.getX();
        lastMouseY = e.getY();
        dragMode = 0;

        VideoSegment currentSeg = timelineManager.getSegmentAt(currentTimelineTime);

        if (currentTool == ToolType.CURSOR) {

            // --- NUEVO: DOBLE CLIC PARA EDITAR TEXTO ---
            if (e.getClickCount() == 2) {
                for (int i = shapes.size() - 1; i >= 0; i--) {
                    DrawingShape s = shapes.get(i);
                    // Chequeo de clip ID...
                    if (s.getClipId() != null && currentSeg != null && !s.getClipId().equals(currentSeg.getId())) continue;

                    if (s.isHit(e.getX(), e.getY()) && "text".equals(s.getType())) {
                        startEditingText(s, e.getX(), e.getY()); // <--- Método nuevo
                        return;
                    }
                }
            }
            // -------------------------------------------

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
            handleTrackingClick(e, currentSeg);
            return;
        }

        if (currentSeg != null && !currentSeg.isFreezeFrame()) {
            if (videoService.isPlaying()) {
                videoService.pause();
                btnPlayPause.setText("▶");
            }
            saveState();
            timelineManager.insertFreezeFrame(currentTimelineTime, 3.0, videoView.getImage());
            currentSeg = timelineManager.getSegmentAt(currentTimelineTime);
        }

        // ---------------------------------------------------------
        // 2. MODO CREACIÓN
        // ---------------------------------------------------------
        selectedShapeToMove = null;

        if (currentTool == ToolType.TEXT) {
            showFloatingInput(e.getX(), e.getY());
            return;
        }

        createNewShape(e, currentSeg);
    }

    private void createNewShape(MouseEvent e, VideoSegment currentSeg) {
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
                currentShape = new DrawingShape("p" + System.currentTimeMillis(),
                        "polygon", e.getX(), e.getY(), toHex(c));

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

        // ---------------------------------------------------------
        //  NUEVA LÓGICA: DEFENSA EN LÍNEA (MULTI-TRACKING)
        // ---------------------------------------------------------
        if (currentTool == ToolType.LINE_DEFENSE) {

            // 1. FINALIZAR: Doble clic o clic derecho cierra la línea
            if (e.getClickCount() == 2 || e.getButton() == MouseButton.SECONDARY) {
                finishPolyShape();
                return;
            }

            // 2. CREAR O CONTINUAR
            if (currentShape == null || !"line_defense".equals(currentShape.getType())) {
                // --- INICIO: PRIMER CLIC (JUGADOR 1) ---
                saveState();

                // Creamos la forma vacía
                currentShape = new DrawingShape("ld_" + System.currentTimeMillis(),
                        "line_defense", e.getX(), e.getY(), toHex(colorPicker.getValue()));

                if (currentSeg != null) currentShape.setClipId(currentSeg.getId());
                currentShape.setStrokeWidth(currentStrokeWidth);

                // AÑADIMOS EL PRIMER PUNTO (Donde has hecho clic)
                // Ya no usamos +50, +100... Usamos la posición real del ratón.
                currentShape.addKeyPoint(e.getX(), e.getY());

                shapes.add(currentShape);

            } else {
                // --- CONTINUACIÓN: SIGUIENTES CLICS (JUGADOR 2, 3, 4...) ---
                // Añadimos el siguiente punto a la línea existente
                currentShape.addKeyPoint(e.getX(), e.getY());
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

        currentShape = new DrawingShape("s" + System.currentTimeMillis(), type, e.getX(), e.getY(), toHex(c));

        if (currentSeg != null) currentShape.setClipId(currentSeg.getId());
        currentShape.setStrokeWidth(s);

        if ("arrow-3d".equals(type)) {
            currentShape.addPoint(e.getX(), e.getY() - 50);
        }
        shapes.add(currentShape);
    }

    private void handleTrackingClick (MouseEvent e, VideoSegment currentSegTrack) {

        if (currentSegTrack == null) return;

        if (currentDetections.isEmpty()) {
            runAIDetectionManual();
            return;
        }

        saveState();
        double minDist = 100.0;
        trackedObject = null;

        Image vidImg = videoView.getImage();
        if (vidImg == null) return;

        double sc = Math.min(drawCanvas.getWidth() / vidImg.getWidth(),
                drawCanvas.getHeight() / vidImg.getHeight());
        double vDW = vidImg.getWidth() * sc;
        double vDH = vidImg.getHeight() * sc;
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
            kalmanFilter = null;
            redrawVideoCanvas();
        }
    }

    private void onCanvasDragged(MouseEvent e) {
        double dx = e.getX() - lastMouseX;
        double dy = e.getY() - lastMouseY;

        if (currentTool == ToolType.CURSOR && selectedShapeToMove != null) {

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
            // --- NUEVO: REDIMENSIONAR TEXTO (ESTIRAR) ---
            else if (dragMode == 5 && "text".equals(selectedShapeToMove.getType())) {
                // Calculamos vector desde el origen del texto (Arriba-Izq) al Ratón actual
                double dxText = e.getX() - selectedShapeToMove.getStartX();
                double dyText = e.getY() - selectedShapeToMove.getStartY();

                // Evitamos valores negativos o cero
                if (dxText < 10) dxText = 10;
                if (dyText < 10) dyText = 10;

                // Usamos la altura para definir el tamaño (es más intuitivo para texto)
                // Dividimos por 2.5 porque en el renderer multiplicamos por 2.5
                // Esto hace que el ratón siga perfectamente a la esquina del texto
                double newSize = dyText / 2.5;

                // Un mínimo de seguridad para que no desaparezca
                selectedShapeToMove.setStrokeWidth(Math.max(5, newSize));
            }
            // --------------------------------------------

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
    }

    private void startEditingText(DrawingShape s, double mouseX, double mouseY) {
        this.textShapeBeingEdited = s; // Guardamos la referencia

        // Preparamos el input flotante
        showFloatingInput(mouseX, mouseY);

        // Rellenamos con el texto actual
        floatingInput.setText(s.getTextValue());
        floatingInput.selectAll(); // Seleccionar todo para sobreescribir rápido
    }

    private void onCanvasReleased(MouseEvent e) {
        // CORRECCIÓN: Añadimos LINE_DEFENSE a las excepciones.
        // Si es Polígono o Defensa en Línea, NO cambiamos al cursor todavía,
        // porque queremos seguir añadiendo puntos con más clics.
        boolean isMultiPointTool = (currentTool == ToolType.POLYGON || currentTool == ToolType.LINE_DEFENSE);

        if (!isMultiPointTool && currentShape != null) {
            switchToCursorAndSelect(currentShape);
            currentShape = null;
        }

        dragMode = 0;
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
            // 1. Recalculamos dónde está la esquina inferior derecha
            Text temp = new Text(s.getTextValue());
            // IMPORTANTE: Usar el mismo factor que en el renderer (2.5)
            temp.setFont(Font.font("Arial", FontWeight.BOLD, s.getStrokeWidth() * 2.5));

            double w = temp.getLayoutBounds().getWidth();
            double h = temp.getLayoutBounds().getHeight();

            // La posición del "handle" es Inicio + Ancho/Alto
            double handleX = s.getStartX() + w;
            double handleY = s.getStartY() + h;

            // 2. Comprobamos si el ratón está cerca de esa esquina
            if (Math.abs(mx - handleX) < dist && Math.abs(my - handleY) < dist) {
                dragMode = 5; // Modo "Estirar Texto"
                return true;
            }

            // Si no pinchamos en la esquina, devolvemos false (así el drag normal moverá el texto)
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
        VideoSegment activeSeg = timelineManager.getSegmentAt(currentTimelineTime);

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

        if (activeSeg != null && activeSeg.isFreezeFrame()) {
            gcDraw.drawImage(vidImg, offX, offY, vidDispW, vidDispH);
        }

        // gcDraw.drawImage(vidImg, offX, offY, vidDispW, vidDispH);

        // Permitimos dibujar las cajas MIENTRAS se reproduce para depurar
        if (btnToggleAI.isSelected() && currentTool == ToolType.TRACKING && currentDetections != null) {
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

        List<DrawingShape> cachedShapes = (activeSegId != null) ? annotationsCache.get(activeSegId) : null;
        List<DrawingShape> allToDraw = new ArrayList<>(shapes);
        if (cachedShapes != null) {
            allToDraw.addAll(cachedShapes);
        }

        for (DrawingShape s : allToDraw) {

            // --- AQUÍ ESTÁ EL CAMBIO (FIX PARA QUE NO DESAPAREZCAN) ---
            // Verificamos si es un dibujo de tracking (simple o línea defensa)
            boolean isTracking = "tracking".equals(s.getType()) || "line_defense".equals(s.getType());

            // Si NO es tracking, aplicamos la regla estricta: debe coincidir el ID del clip.
            // Si SÍ es tracking, nos saltamos esta comprobación para que se siga viendo al reproducir.
            if (!isTracking) {
                if (s.getClipId() != null) {
                    if (activeSegId == null || !activeSegId.equals(s.getClipId())) {
                        continue;
                    }
                }
            }
            // -----------------------------------------------------------

            Color c = Color.web(s.getColor());
            double size = s.getStrokeWidth();
            double x1 = s.getStartX();
            double y1 = s.getStartY();
            double x2 = s.getEndX();
            double y2 = s.getEndY();

            if (s == selectedShapeToMove) {
                canvasRenderer.drawSelectionOverlay(s, size, x1, y1, x2, y2);
            }

            // El CanvasRenderer se encarga del estilo (Círculos y Triángulos)
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

        // Si llamamos a esto directamente (desde la herramienta Texto), limpiamos la edición
        if (currentTool == ToolType.TEXT) {
            this.textShapeBeingEdited = null;
            floatingInput.setText("");
        }

        floatingInput.setTranslateX(x - (drawCanvas.getWidth() / 2) + 100);
        floatingInput.setTranslateY(y - (drawCanvas.getHeight() / 2));
        floatingInput.setVisible(true);
        floatingInput.requestFocus();

        // Guardamos posición por si es nuevo
        floatingInput.setUserData(new double[]{x, y});
    }

    private void confirmFloatingText() {
        if (!floatingInput.isVisible()) return;

        String text = floatingInput.getText();

        if (!text.isEmpty()) {
            saveState(); // Guardamos historia para Undo

            if (textShapeBeingEdited != null) {
                // CASO 1: EDITAR EXISTENTE
                textShapeBeingEdited.setTextValue(text);
                // Opcional: Actualizar color también si quieres
                // textShapeBeingEdited.setColor(toHex(colorPicker.getValue()));
                textShapeBeingEdited = null; // Reset
            } else {
                // CASO 2: CREAR NUEVO (Tu lógica anterior)
                double[] pos = (double[]) floatingInput.getUserData();
                Color c = colorPicker.getValue();
                DrawingShape txtShape = new DrawingShape("t" + System.currentTimeMillis(), "text", pos[0], pos[1], toHex(c));

                VideoSegment activeSeg = timelineManager.getSegmentAt(currentTimelineTime);
                if (activeSeg != null) {
                    txtShape.setClipId(activeSeg.getId());
                }

                txtShape.setTextValue(text);
                // Tamaño por defecto o el del slider
                txtShape.setStrokeWidth(currentStrokeWidth);
                shapes.add(txtShape);
                switchToCursorAndSelect(txtShape);
            }
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

                    VideoSegment current = timelineManager.getSegmentAt(currentTimelineTime);
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

            double totalOrgDur = timelineManager.getTotalOriginalDuration();

            if (totalOrgDur > 0) {
                currentRealVideoTime = (percent / 100.0) * totalOrgDur;

                VideoSegment expectedSeg = timelineManager.getSegmentAt(currentTimelineTime);

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
                        performSafeSeek((expectedRealTime / totalOrgDur) * 100.0);
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
                    // redrawVideoCanvas();
                });
            }
        });

        // --- CORRECCIÓN PARA setupVideoEvents ---
        videoService.setOnFrameCaptured(bufferedImage -> {
            // 1. Chequeo rápido
            if (!btnToggleAI.isSelected() || trackingShape == null) return;

            frameSkipCounter++;

            // 2. Lógica de calentamiento y salto de frames
            boolean isWarmup = (framesTracked < 10);
            boolean runHeavyAI = isWarmup || (frameSkipCounter % 3 == 0);

            if (runHeavyAI && !isProcessingAI.get()) {
                isProcessingAI.set(true);
                aiExecutor.submit(() -> {
                    try {
                        // --- CORRECCIÓN: La imagen ya viene como BufferedImage ---
                        // No usamos SwingFXUtils. La usamos directamente como 'fullImage'.
                        BufferedImage fullImage = bufferedImage;

                        // 3. REDIMENSIONAR (Optimización de velocidad)
                        double aspect = (double) fullImage.getHeight() / fullImage.getWidth();
                        int newW = 640;
                        int newH = (int) (newW * aspect);

                        BufferedImage resizedImage = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_BGR);
                        var g = resizedImage.createGraphics();
                        g.drawImage(fullImage, 0, 0, newW, newH, null);
                        g.dispose();
                        // --------------------------------------------------

                        // 4. Ejecutar IA con la imagen pequeña
                        var results = aiService.detectPlayers(resizedImage);

                        Platform.runLater(() -> {
                            this.currentDetections = results;
                            // CAMBIO: Llamamos a la nueva lógica multi-tracking
                            processMultiTrackingLogic(results, true);
                            isProcessingAI.set(false);
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        isProcessingAI.set(false);
                    }
                });
            } else {
                // Frames intermedios: Solo física
                Platform.runLater(() -> {
                    processMultiTrackingLogic(null, false);
                });
            }
        });
    }

    private void forceStopTracking() {
        Platform.runLater(() -> {
            // Detenemos la matemática
            kalmanFilter = null;
            framesLost = 0;
            framesTracked = 0;

            // --- CAMBIO CRUCIAL: NO BORRAMOS LA FORMA ---
            // Simplemente ponemos trackingShape a null para que la IA deje de moverlo.
            // El círculo se quedará quieto donde se perdió, pero NO desaparecerá.
            trackingShape = null;

            // (Opcional) Si quieres avisar por consola
            System.out.println("Tracking detenido (pausa o pérdida), pero dibujo mantenido.");

            redrawVideoCanvas();
        });
    }

    private void jumpToNextSegment(VideoSegment currentSeg) {
        resetTracking();
        autoSaveActiveSegmentDrawings();

        // 1. Delegamos la búsqueda al Manager
        VideoSegment nextSeg = timelineManager.getNextSegment(currentSeg, currentTimelineTime);

        if (nextSeg != null) {
            // --- CASO: HAY SIGUIENTE CLIP ---
            currentTimelineTime = nextSeg.getStartTime();

            // Usamos la duración original desde el Manager
            double totalOrg = timelineManager.getTotalOriginalDuration();

            double seekPercent = (nextSeg.getSourceStartTime() / totalOrg) * 100.0;
            performSafeSeek(seekPercent);

            checkPlaybackJump();
        } else {
            // --- CASO: FIN DEL TIMELINE ---
            Platform.runLater(() -> {
                if (videoService.isPlaying()) onPlayPause();
                // Usamos la duración total desde el Manager
                currentTimelineTime = timelineManager.getTotalDuration();
                redrawTimeline();
            });
        }
    }

    private void checkAutoScroll() {
        if (timelineManager.getTotalDuration() <= 0) return;
        double w = timelineCanvas.getWidth();
        double playheadPos = (currentTimelineTime * pixelsPerSecond) - timelineScroll.getValue();
        if (playheadPos > w) {
            timelineScroll.setValue(timelineScroll.getValue() + playheadPos - w + 50);
        } else if (playheadPos < 0) {
            timelineScroll.setValue(timelineScroll.getValue() + playheadPos - 50);
        }
    }

    private void checkPlaybackJump() {
        VideoSegment currentSeg = timelineManager.getSegmentAt(currentTimelineTime);

        if (currentSeg == null ||
                (trackingShape != null && !currentSeg.getId().equals(trackingShape.getClipId()))) {
            kalmanFilter = null;
        }

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

    @FXML
    public void onCaptureFrame() {
        // 1. Consultamos al manager si la lista está vacía
        if (timelineManager.getSegments().isEmpty()) return;

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

                saveState(); // Guardamos estado para Undo

                timelineManager.insertFreezeFrame(currentTimelineTime, duration, videoView.getImage());

                // 3. Actualizamos la UI porque los datos han cambiado
                updateScrollbarAndRedraw();
                redrawVideoCanvas();

            } catch (NumberFormatException e) {}
        }
    }

    @FXML
    public void onCutVideo() {
        // 1. Guardar estado para poder deshacer (Undo)
        saveState();

        // 2. Delegar toda la matemática al Manager
        // Le pasamos: Tiempo actual y la Imagen actual (para la miniatura del nuevo trozo)
        boolean cutPerformed = timelineManager.cutVideo(currentTimelineTime, videoView.getImage());

        // 3. Solo si hubo un corte real, actualizamos la interfaz
        if (cutPerformed) {
            // Deseleccionamos para evitar errores visuales (o podrías buscar el nuevo segmento si quisieras)
            selectedSegment = null;

            // Redibujamos el timeline
            updateScrollbarAndRedraw();
        }
    }

    @FXML public void onDeleteSegment() {
        saveState();

        // 1. Lógica de selección inteligente (UI)
        // Si no hay nada seleccionado, intentamos borrar lo que está bajo el cabezal
        if (selectedSegment == null) {
            // Usamos el método del manager en lugar de selectSegmentUnderPlayhead()
            selectedSegment = timelineManager.getSegmentAt(currentTimelineTime);
        }

        // 2. Delegamos el borrado al Manager
        boolean wasDeleted = timelineManager.deleteSegment(selectedSegment);

        if (wasDeleted) {
            // 3. Actualizamos UI solo si hubo borrado real
            selectedSegment = null;
            updateScrollbarAndRedraw();

            // 4. Corregir cabezal si se ha quedado fuera del video (por estar al final)
            double totalDur = timelineManager.getTotalDuration();
            if (currentTimelineTime > totalDur) {
                currentTimelineTime = Math.max(0, totalDur - 0.1);
            }

            redrawTimeline(); // Refrescar visualmente
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
        for (VideoSegment seg : timelineManager.getSegments()) {
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

        if (hoverTime >= 0 && hoverTime <= timelineManager.getTotalDuration()) {
            double hX = (hoverTime * pixelsPerSecond) - scrollOffset;

            gcTimeline.setStroke(Color.web("#ffffff", 0.4));
            gcTimeline.setLineWidth(1.0);
            gcTimeline.strokeLine(hX, 0, hX, h);

            /*String timeText = formatPreciseTime(hoverTime);

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
            gcTimeline.fillText(timeText, rectX + 5, rectY + 12);*/
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

        if (hoverTime >= 0 && hoverTime <= timelineManager.getTotalDuration()) {
            double hX = (hoverTime * pixelsPerSecond) - scrollOffset;

            gcTimeline.setStroke(Color.web("#ffffff", 0.3));
            gcTimeline.strokeLine(hX, 0, hX, h);

            /*String timeStr = formatPreciseTime(hoverTime);
            gcTimeline.setFont(Font.font("Arial", FontWeight.BOLD, 9));

            double rectW = 34; double rectH = 14;
            double rectX = hX - (rectW / 2);
            double rectY = 12;

            gcTimeline.setFill(Color.web("#1a1a1a", 0.9));
            gcTimeline.fillRoundRect(rectX, rectY, rectW, rectH, 4, 4);
            gcTimeline.setFill(Color.WHITE);
            gcTimeline.fillText(timeStr, rectX + 4, rectY + 10);*/
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

        double totalDur = timelineManager.getTotalDuration();

        if (totalDur <= 0) {
            timelineScroll.setVisible(false);
            timelineScroll.setManaged(false);
            return;
        }

        double canvasW = timelineCanvas.getWidth();
        double totalW = totalDur * pixelsPerSecond;

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
        double totalDur = timelineManager.getTotalDuration();
        // Tiempo actual (UI)
        int m = (int) currentTimelineTime / 60;
        int s = (int) currentTimelineTime % 60;

        // Tiempo total (Manager)
        int totalM = (int) totalDur / 60;
        int totalS = (int) totalDur % 60;

        lblTime.setText(String.format("%02d:%02d", m, s) + " / " +
                String.format("%02d:%02d", totalM, totalS));
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

            setLoading(true);

            gcTimeline.clearRect(0,0, timelineCanvas.getWidth(), timelineCanvas.getHeight());

            try {
                videoService.loadVideo(f.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
            }

            Task<TreeMap<Double, Image>> task = new Task<>() {
                @Override
                protected TreeMap<Double, Image> call() {
                    return new FilmstripService().generateFilmstrip(f, 5.0, 80);
                }
            };

            task.setOnSucceeded(event -> {
                setLoading(false);

                // --- LIMPIEZA OBLIGATORIA AL CARGAR NUEVO VIDEO ---
                shapes.clear();             // Borrar dibujos anteriores
                annotationsCache.clear();   // Borrar caché de segmentos
                resetTracking();            // Resetear variables de IA

                this.filmstripMap = task.getValue();

                timelineManager.reset(videoService.getTotalDuration());

                currentTimelineTime = 0;

                Platform.runLater(() -> {
                    updateTimeLabel();
                    updateScrollbarAndRedraw();
                    redrawVideoCanvas();
                    if (btnPlayPause != null) btnPlayPause.setText("▶");
                });

                if (timelineScroll != null) {
                    timelineScroll.setValue(0);
                    // Usamos la duración del Manager para configurar el scroll
                    timelineScroll.setMax(timelineManager.getTotalDuration() * pixelsPerSecond);
                }

                updateScrollbarAndRedraw();
                videoService.pause();
                videoService.seek(0);
            });

            task.setOnFailed(e -> {
                setLoading(false);
                System.err.println("Error generando miniaturas");
            });

            new Thread(task).start();
        }
    }

    private void performSafeSeek(double percent) {
        ignoreUpdatesUntil = System.currentTimeMillis() + 150;
        videoService.seek(percent);
    }

    private void seekTimeline(double mouseX, boolean updateVideo) {
        resetTracking();

        double scrollOffset = timelineScroll.getValue();
        double time = (mouseX + scrollOffset) / pixelsPerSecond;

        // 1. Validaciones
        if (time < 0) time = 0;
        double totalDur = timelineManager.getTotalDuration();
        if (time > totalDur) time = totalDur;

        // 2. Actualizamos variable de tiempo siempre
        currentTimelineTime = time;

        // 3. ACTUALIZAR VIDEO
        if (updateVideo) {
            // ¡IMPORTANTE! Desbloqueamos las actualizaciones visuales inmediatamente
            ignoreUpdatesUntil = 0;

            VideoSegment targetSeg = timelineManager.getSegmentAt(time);
            double totalOrgDur = timelineManager.getTotalOriginalDuration();
            double seekTarget = 0;

            if (targetSeg != null) {
                // Sobre un clip
                double offset = time - targetSeg.getStartTime();
                seekTarget = targetSeg.getSourceStartTime() + offset;
            } else {
                // En un hueco
                VideoSegment nextSeg = timelineManager.getNextSegment(null, time);
                if (nextSeg != null) {
                    seekTarget = nextSeg.getSourceStartTime();
                }
            }

            // Usamos seek directo (sin safeSeek) para respuesta inmediata
            if (totalOrgDur > 0) {
                videoService.seek((seekTarget / totalOrgDur) * 100.0);
            }

            Platform.runLater(() -> {
                checkPlaybackJump();
                // redrawVideoCanvas();
                updateTimeLabel();
            });
        }

        redrawTimeline();

        if (!updateVideo) {
            updateTimeLabel();
        }
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
        double totalDur = timelineManager.getTotalDuration();
        if (totalDur > 0) {
            currentTimelineTime = totalDur;
            if (timelineScroll != null) timelineScroll.setValue(timelineScroll.getMax());
            if (videoService != null) {
                if (videoService.isPlaying()) onPlayPause();
                videoService.seek(100);
            }
            redrawTimeline();
            updateTimeLabel();
        }
    }

    private void saveState() {
        if (timelineManager.getSegments() == null) return;

        List<VideoSegment> segmentsSnapshot = new ArrayList<>();

        for (VideoSegment s : timelineManager.getSegments()) {
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

        undoStack.push(new EditorState(segmentsSnapshot, shapesSnapshot, timelineManager.getTotalDuration()));
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

        redoStack.push(new EditorState(
                new ArrayList<>(timelineManager.getSegments()),
                new ArrayList<>(shapes),
                timelineManager.getTotalDuration()
        ));

        restoreState(undoStack.pop());
        updateUndoRedoButtons();
    }

    @FXML
    public void onRedo() {
        if (redoStack.isEmpty()) return;

        undoStack.push(new EditorState(
                new ArrayList<>(timelineManager.getSegments()),
                new ArrayList<>(shapes),
                timelineManager.getTotalDuration()
        ));

        restoreState(redoStack.pop());
        updateUndoRedoButtons();
    }

    private void restoreState(EditorState state) {
        // 1. Restaurar SEGMENTOS y DURACIÓN (Delegado al Manager)
        timelineManager.restoreState(state.segmentsSnapshot, state.durationSnapshot); // <-- CAMBIO

        // 2. Restaurar DIBUJOS (Se queda en el Controller)
        this.shapes = new ArrayList<>(state.shapesSnapshot);

        // 3. Actualizar UI
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

        setLoading(true);
        if (lblStatus != null) lblStatus.setText("Preparando exportación local...");

        // --- IMPORTANTE ---
        // Hacemos una COPIA de la lista de segmentos AQUÍ (en el hilo principal)
        // para que el hilo secundario trabaje seguro.
        List<VideoSegment> segmentsToExport = new ArrayList<>(timelineManager.getSegments());

        new Thread(() -> {
            try {
                List<LocalExportService.ExportJobSegment> jobSegments = new ArrayList<>();

                // Usamos la copia 'segmentsToExport' en lugar de 'segments'
                for (VideoSegment seg : segmentsToExport) {
                    if (seg.isFreezeFrame()) {
                        FutureTask<String> task = new FutureTask<>(() -> saveSnapshotToTempFile(seg));
                        Platform.runLater(task);
                        String imgPath = task.get();

                        if (imgPath != null) {
                            jobSegments.add(LocalExportService.ExportJobSegment.freeze(imgPath, seg.getDuration()));
                        }
                    } else {
                        jobSegments.add(LocalExportService.ExportJobSegment.video(seg.getSourceStartTime(), seg.getSourceEndTime()));
                    }
                }

                Platform.runLater(() -> {
                    lblStatus.setFont(Font.font("System", FontWeight.BOLD, 22));
                    lblStatus.setTextFill(Color.web("#ffffff"));
                    lblStatus.setText("Exportando video...");
                });

                LocalExportService exportService = new LocalExportService();
                exportService.renderProject(localVideoPath, jobSegments, destFile);

                Platform.runLater(() -> {
                    setLoading(false);
                    lblStatus.setFont(Font.font("System", FontWeight.BOLD, 22));
                    lblStatus.setTextFill(Color.web("#ffffff"));
                    lblStatus.setText("✅ Exportación completada");
                    mostrarAlerta("Éxito", "Video guardado en: " + destFile.getAbsolutePath());
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    setLoading(false);
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

        addTooltip(btnTracking, "Tracking - Seguimiento automático de jugador");

        addTooltip(btnDeleteShape, "Borrar edición");
        addTooltip(btnClearCanvas, "Limpiar dibujo");

        addTooltip(btnLineDefense, "Multi-Tracking - Seguimiento automático de jugadores");
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
            double exportW = 1280;
            double exportH = 720;

            Canvas tempCanvas = new Canvas(exportW, exportH);
            GraphicsContext gc = tempCanvas.getGraphicsContext2D();

            // 1. Fondo negro (para evitar transparencia rara)
            gc.setFill(Color.BLACK);
            gc.fillRect(0, 0, exportW, exportH);

            Image bg = seg.getThumbnail();
            if (bg == null) return null;

            // 2. Calcular ESCALA REAL (Sin deformar la imagen)
            double scaleW = exportW / bg.getWidth();
            double scaleH = exportH / bg.getHeight();
            double scale = Math.min(scaleW, scaleH); // Usamos el menor para que quepa entera

            double finalW = bg.getWidth() * scale;
            double finalH = bg.getHeight() * scale;
            double destX = (exportW - finalW) / 2.0;
            double destY = (exportH - finalH) / 2.0;

            // Pintamos la imagen centrada
            gc.drawImage(bg, destX, destY, finalW, finalH);

            // 3. Calcular factores de conversión (Editor -> Export)
            double canvasW = drawCanvas.getWidth();
            double canvasH = drawCanvas.getHeight();

            // Replicamos la escala que tenía en el editor
            double editorScale = Math.min(canvasW / bg.getWidth(), canvasH / bg.getHeight());
            double editorVidW = bg.getWidth() * editorScale;
            double editorVidH = bg.getHeight() * editorScale;

            // Estos son los márgenes negros que tenías en el editor
            double editorOffX = (canvasW - editorVidW) / 2.0;
            double editorOffY = (canvasH - editorVidH) / 2.0;

            // Factor de multiplicación
            double sx = finalW / editorVidW;
            double sy = finalH / editorVidH;

            // PREPARAMOS EL CONTEXTO
            gc.save();
            gc.translate(destX, destY); // Movemos el lápiz al inicio de la imagen real

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
                // CORRECCIÓN CRUCIAL: Calculamos las coordenadas reales
                double x1 = (s.getStartX() - editorOffX) * sx;
                double y1 = (s.getStartY() - editorOffY) * sy;
                double x2 = (s.getEndX() - editorOffX) * sx;
                double y2 = (s.getEndY() - editorOffY) * sy;
                double size = s.getStrokeWidth();

                // Pasamos las coordenadas calculadas y los offsets del editor para las curvas internas
                canvasRenderer.drawShapeOnGC(gc, s, bg,
                        x1, y1, x2, y2,
                        size,
                        sx, sy,
                        editorOffX, editorOffY,
                        exportW, exportH);
            }

            gc.restore();

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

        canvasRenderer.drawShapeOnGC(gc, s, bg, x1, y1, x2, y2, size, sx, sy, offX, offY, exportW, exportH);
    }

    private void onTimelineDragged(MouseEvent e) {
        double canvasW = timelineCanvas.getWidth();
        double edgeThreshold = 70.0;
        double scrollOffset = timelineScroll.getValue();

        // CASO A: MOVER UN CLIP (Si estamos arrastrando un segmento)
        if (segmentBeingDragged != null) {
            isDraggingTimeline = true;
            currentDragX = e.getX();

            // Auto-scroll al llegar a los bordes
            if (e.getX() > canvasW - edgeThreshold) {
                timelineScroll.setValue(scrollOffset + 15);
            } else if (e.getX() < edgeThreshold && scrollOffset > 0) {
                timelineScroll.setValue(scrollOffset - 15);
            }

            // Cálculo de dónde va a caer el clip (Drop Indicator)
            double mousePosTime = (e.getX() + timelineScroll.getValue()) / pixelsPerSecond;
            List<VideoSegment> others = new ArrayList<>(timelineManager.getSegments());
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

        }
        // CASO B: SCRUBBING (Solo movemos la línea de tiempo)
        else {
            long now = System.currentTimeMillis();

            // 1. UI Inmediata
            seekTimeline(e.getX(), false);

            // 2. Video Controlado (Subimos a 100ms para dar tiempo a cargar el frame)
            if (now - lastScrubTime > 100) { // <--- CAMBIO AQUÍ (75 -> 100)
                seekTimeline(e.getX(), true);
                lastScrubTime = now;
            }
        }
    }

    private void initTimelineContextMenu() {
        timelineContextMenu = new ContextMenu();

        // 1. Modificar Duración -> Icono: Reloj ("clock")
        MenuItem modifyTimeItem = new MenuItem("Modificar Duración");
        modifyTimeItem.setGraphic(AppIcons.getIcon("clock", 16));
        modifyTimeItem.setOnAction(e -> onModifyFreezeDuration());

        // 2. Añadir fotograma -> Icono: Cámara ("camera")
        MenuItem captureItem = new MenuItem("Añadir fotograma congelado");
        captureItem.setGraphic(AppIcons.getIcon("camera", 16));
        captureItem.setOnAction(e -> onCaptureFrame());

        // 3. Dividir clip -> Icono: Tijeras ("cut" o "scissors")
        MenuItem cutItem = new MenuItem("Dividir clip");
        cutItem.setGraphic(AppIcons.getIcon("cut", 16));
        cutItem.setOnAction(e -> onCutVideo());

        // 4. Eliminar -> Icono: Basura ("trash") - Ya usas "trash" en btnDeleteShape
        MenuItem deleteItem = new MenuItem("Eliminar");
        deleteItem.setGraphic(AppIcons.getIcon("trash", 16));
        deleteItem.setStyle("-fx-text-fill: #ff4444;"); // Mantenemos el texto rojo de alerta
        deleteItem.setOnAction(e -> onDeleteSegment());

        // Añadimos todo al menú con separadores
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
        // Validación de UI
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

                timelineManager.modifySegmentDuration(selectedSegment, newDuration);

                updateScrollbarAndRedraw();

            } catch (NumberFormatException e) {
                mostrarAlerta("Error", "Introduce un número válido.");
            }
        }
    }

    private void autoSaveActiveSegmentDrawings() {

        if (shapes.isEmpty()) return;

        VideoSegment activeSeg = timelineManager.getSegmentAt(currentTimelineTime);

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
        // Validaciones básicas
        if (btnToggleAI == null || !btnToggleAI.isSelected() || isProcessingAI.get()) return;

        Image fxImage = videoView.getImage();
        if (fxImage == null) return;

        // Bloqueamos para que no se solapen peticiones
        isProcessingAI.set(true);

        Platform.runLater(() -> {
            if (lblStatus != null) lblStatus.setText("🔍 Analizando frame manualmente...");
        });

        aiExecutor.submit(() -> {
            try {
                BufferedImage bufferedImage = SwingFXUtils.fromFXImage(fxImage, null);
                var results = aiService.detectPlayers(bufferedImage);

                Platform.runLater(() -> {
                    // 1. Actualizamos las detecciones para que se pinten los recuadros (si activas debug)
                    // o para que el clic del ratón sepa dónde hay gente.
                    this.currentDetections = results;

                    // 2. Redibujamos para ver los resultados (si tienes activado el pintado de cajas)
                    redrawVideoCanvas();

                    if (lblStatus != null) lblStatus.setText("✅ Análisis manual listo");
                    isProcessingAI.set(false);
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    if (lblStatus != null) lblStatus.setText("❌ Error en análisis");
                    isProcessingAI.set(false);
                });
            }
        });
    }

    private void resetTracking() {
        kalmanFilter = null;
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

        timelineManager.reset(0);
        shapes.clear();
        annotationsCache.clear();
        filmstripMap.clear();

        currentTimelineTime = 0.0;

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

            VideoSegment activeSeg = timelineManager.getSegmentAt(currentTimelineTime);

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
        VideoSegment activeSeg = timelineManager.getSegmentAt(currentTimelineTime);

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

    // --- MÉTODOS AUXILIARES PARA MULTI-TRACKING ---

    // Busca el objeto detectado (humano) más cercano a un punto (x, y)
    private DetectedObjects.DetectedObject findClosestHuman(List<DetectedObjects.DetectedObject> results,
                                                            double targetX, double targetY,
                                                            double vDW, double vDH, double oX, double oY) {
        DetectedObjects.DetectedObject bestMatch = null;
        double minDst = 100.0; // Radio de búsqueda individual
        double AI_REF = 640.0;

        for (var obj : results) {
            if (!obj.getClassName().equals("person")) continue;

            var r = obj.getBoundingBox().getBounds();
            // Convertir coordenadas de IA a Pantalla
            double screenX = ((r.getX() + r.getWidth()/2.0) / AI_REF) * vDW + oX;
            double screenY = ((r.getY() + r.getHeight()) / AI_REF) * vDH + oY;

            double dist = Math.sqrt(Math.pow(targetX - screenX, 2) + Math.pow(targetY - screenY, 2));

            if (dist < minDst) {
                minDst = dist;
                bestMatch = obj;
            }
        }
        return bestMatch;
    }

    // Convierte un objeto detectado (IA) a coordenadas de pantalla (Pies)
    private java.awt.Point.Double convertToScreenCoords(DetectedObjects.DetectedObject obj,
                                                        double vDW, double vDH, double oX, double oY) {
        double AI_REF = 640.0;
        var r = obj.getBoundingBox().getBounds();

        double boxX = (r.getX() / AI_REF) * vDW + oX;
        double boxY = (r.getY() / AI_REF) * vDH + oY;
        double boxW = (r.getWidth() / AI_REF) * vDW;
        double boxH = (r.getHeight() / AI_REF) * vDH;

        double feetX = boxX + (boxW / 2.0);
        double feetY = boxY + boxH;

        return new java.awt.Point.Double(feetX, feetY);
    }

    // --- NUEVO MÉTODO: Solo mueve el video visualmente (Previsualización) ---
    // NO actualiza currentTimelineTime, por lo que la aguja roja no se mueve.
    private void previewVideoOnly(double time) {
        // 1. Validar límites
        double totalDur = timelineManager.getTotalDuration();
        if (time < 0) time = 0;
        if (time > totalDur) time = totalDur;

        // 2. Calcular qué frame mostrar (Lógica idéntica a seekTimeline)
        VideoSegment targetSeg = timelineManager.getSegmentAt(time);
        double totalOrgDur = timelineManager.getTotalOriginalDuration();
        double seekTarget = 0;

        if (targetSeg != null) {
            double offset = time - targetSeg.getStartTime();
            seekTarget = targetSeg.getSourceStartTime() + offset;
        } else {
            VideoSegment nextSeg = timelineManager.getNextSegment(null, time);
            if (nextSeg != null) {
                seekTarget = nextSeg.getSourceStartTime();
            }
        }

        // 3. Mover el video (IMPORTANTE: Desbloquear actualizaciones)
        if (totalOrgDur > 0) {
            ignoreUpdatesUntil = 0;
            videoService.seek((seekTarget / totalOrgDur) * 100.0);
        }
    }

}

