package com.ssltrainingplatform.service;

import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.transform.Resize;
import ai.djl.modality.cv.transform.ToTensor;
// CAMBIO IMPORTANTE: Usamos YoloV8Translator
import ai.djl.modality.cv.translator.YoloV8Translator;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Pipeline;
import ai.djl.translate.Translator;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AIService {

    private ZooModel<Image, DetectedObjects> model;
    private Predictor<Image, DetectedObjects> predictor;

    public void loadModel(String relativePath) throws IOException, ModelException {
        // 1. Verificar archivo
        Path modelPath = Paths.get(relativePath).toAbsolutePath();
        File modelFile = modelPath.toFile();
        if (!modelFile.exists()) {
            throw new IOException("❌ Modelo no encontrado: " + modelPath.toString());
        }
        System.out.println("📂 Cargando modelo YOLOv11 desde: " + modelPath);

        // 2. Pipeline (Igual que antes)
        Pipeline pipeline = new Pipeline();
        pipeline.add(new Resize(640, 640));
        pipeline.add(new ToTensor());

        // 3. DEFINIR CLASES (Synset)
        List<String> classes = Arrays.asList(
                "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
                "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
                "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
                "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
                "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
                "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
                "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
                "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
                "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator",
                "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
        );

        // 4. TRADUCTOR (¡AQUÍ ESTABA EL ERROR!)
        Translator<Image, DetectedObjects> translator = YoloV8Translator.builder()
                .setPipeline(pipeline)
                .optSynset(classes) // Le pasamos la lista de nombres
                .optThreshold(0.1f) // Umbral de confianza (40%)
                .build();

        // 5. Criterios de carga
        Criteria<Image, DetectedObjects> criteria = Criteria.builder()
                .setTypes(Image.class, DetectedObjects.class)
                .optModelPath(modelPath)
                .optModelName("yolov11")
                .optTranslator(translator)
                .optEngine("PyTorch")
                .build();

        // 6. Cargar
        this.model = criteria.loadModel();
        this.predictor = model.newPredictor();

        System.out.println("✅ Modelo YOLOv11 cargado correctamente en memoria.");
    }

    public List<DetectedObjects.DetectedObject> detectPlayers(BufferedImage bufferedImage) {
        if (predictor == null) return Collections.emptyList();
        try {
            Image djlImage = ImageFactory.getInstance().fromImage(bufferedImage);
            DetectedObjects detection = predictor.predict(djlImage);
            return detection.items();
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public void close() {
        if (model != null) model.close();
        if (predictor != null) predictor.close();
    }
}