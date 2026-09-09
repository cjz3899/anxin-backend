package com.anxin.ocr.engine;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.anxin.ocr.config.PaddleOcrProperties;
import com.anxin.ocr.model.DetPreprocessResult;
import com.anxin.ocr.model.TextBox;
import com.anxin.ocr.postprocess.DetPostProcessor;
import com.anxin.ocr.preprocess.DetPreprocessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检测引擎
 */
@Slf4j
public class DetEngine {

    private final PaddleOcrProperties properties;
    private final ResourceLoader resourceLoader;
    private final DetPreprocessor preprocessor;
    private final DetPostProcessor postProcessor;

    private OrtEnvironment env;
    private OrtSession session;

    public DetEngine(ResourceLoader resourceLoader, PaddleOcrProperties properties) {
        this.resourceLoader = resourceLoader;
        this.properties = properties;
        this.preprocessor = new DetPreprocessor();
        this.postProcessor = new DetPostProcessor(
                properties.detThreshold(), properties.detBoxUnclipRatio());
    }

    public void init() throws Exception {
        env = OrtEnvironment.getEnvironment();
        byte[] modelBytes = resourceLoader.getResource(properties.detModelPath())
                .getInputStream().readAllBytes();
        session = env.createSession(modelBytes);
        log.info("检测模型加载完成: {}", properties.detModelPath());
    }

    public void close() {
        try {
            if (session != null) {
                session.close();
                session = null;
            }
        } catch (Exception e) {
            log.warn("关闭检测模型失败", e);
        }
    }

    public List<TextBox> detect(BufferedImage image) throws Exception {
        int origW = image.getWidth();
        int origH = image.getHeight();
        DetPreprocessResult preprocessResult = preprocessor.process(image);

        long[] shape = {1, 3, preprocessResult.height(), preprocessResult.width()};
        OnnxTensor inputTensor = OnnxTensor.createTensor(env,
                FloatBuffer.wrap(preprocessResult.data()), shape);

        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("x", inputTensor);
        OrtSession.Result result = session.run(inputs);

        float[][][][] output = (float[][][][]) result.get(0).getValue();
        int modelH = output[0][0].length;
        int modelW = output[0][0][0].length;
        float[] outputFlat = new float[modelH * modelW];
        for (int y = 0; y < modelH; y++) {
            for (int x = 0; x < modelW; x++) {
                outputFlat[y * modelW + x] = output[0][0][y][x];
            }
        }

        inputTensor.close();
        result.close();

        return postProcessor.process(outputFlat, modelH, modelW, origW, origH);
    }
}
