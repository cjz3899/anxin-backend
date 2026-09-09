package com.anxin.ocr.engine;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.anxin.ocr.config.PaddleOcrProperties;
import com.anxin.ocr.postprocess.CtcDecoder;
import com.anxin.ocr.preprocess.RecPreprocessor;
import com.anxin.ocr.util.CharDictLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 识别引擎
 */
@Slf4j
public class RecEngine {

    private final PaddleOcrProperties properties;
    private final ResourceLoader resourceLoader;
    private final RecPreprocessor preprocessor;
    private final CtcDecoder ctcDecoder;

    private OrtEnvironment env;
    private OrtSession session;
    private CharDictLoader charDict;

    public RecEngine(ResourceLoader resourceLoader, PaddleOcrProperties properties) {
        this.resourceLoader = resourceLoader;
        this.properties = properties;
        this.preprocessor = new RecPreprocessor();
        this.ctcDecoder = new CtcDecoder();
    }

    public void init() throws Exception {
        env = OrtEnvironment.getEnvironment();
        byte[] modelBytes = resourceLoader.getResource(properties.recModelPath())
                .getInputStream().readAllBytes();
        session = env.createSession(modelBytes);

        charDict = new CharDictLoader(resourceLoader, properties.charDictPath());
        log.info("识别模型加载完成: {}", properties.recModelPath());
    }

    public void close() {
        try {
            if (session != null) {
                session.close();
                session = null;
            }
        } catch (Exception e) {
            log.warn("关闭识别模型失败", e);
        }
    }

    public String recognize(BufferedImage image) throws Exception {
        float[] inputData = preprocessor.process(image);
        int inputW = preprocessor.getInputWidth();
        int inputH = preprocessor.getInputHeight();

        long[] shape = {1, 3, inputH, inputW};
        OnnxTensor inputTensor = OnnxTensor.createTensor(env,
                FloatBuffer.wrap(inputData), shape);

        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("x", inputTensor);
        OrtSession.Result result = session.run(inputs);

        float[][][] output = (float[][][]) result.get(0).getValue();
        int seqLen = output[0].length;
        int vocabSize = output[0][0].length;

        float[] outputFlat = new float[seqLen * vocabSize];
        for (int t = 0; t < seqLen; t++) {
            System.arraycopy(output[0][t], 0, outputFlat, t * vocabSize, vocabSize);
        }

        inputTensor.close();
        result.close();

        List<Integer> indices = ctcDecoder.decode(outputFlat, seqLen, vocabSize);
        return charDict.decode(indices);
    }
}
