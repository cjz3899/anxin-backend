package com.anxin.ocr.service.Impl;

import com.anxin.ocr.OcrException;
import com.anxin.ocr.config.PaddleOcrProperties;
import com.anxin.ocr.engine.DetEngine;
import com.anxin.ocr.engine.OcrPipeline;
import com.anxin.ocr.engine.RecEngine;
import com.anxin.ocr.service.OcrService;
import com.anxin.ocr.util.ImageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.awt.image.BufferedImage;

/**
 * PaddleOCR 服务实现
 */
@Slf4j
@Service
@EnableConfigurationProperties(PaddleOcrProperties.class)
public class PaddleOcrService implements OcrService {

    private final DetEngine detEngine;
    private final RecEngine recEngine;
    private final OcrPipeline pipeline;

    public PaddleOcrService(ResourceLoader resourceLoader, PaddleOcrProperties properties) {
        this.detEngine = new DetEngine(resourceLoader, properties);
        this.recEngine = new RecEngine(resourceLoader, properties);
        this.pipeline = new OcrPipeline(detEngine, recEngine);
    }

    @PostConstruct
    public void init() {
        try {
            detEngine.init();
            recEngine.init();
            log.info("PaddleOCR 引擎初始化完成");
        } catch (Exception e) {
            throw new OcrException("OCR 模型加载失败", e);
        }
    }

    /**
     * 关闭所有资源
     */
    @PreDestroy
    public void destroy() {
        detEngine.close();
        recEngine.close();
    }

    /**
     * 识别图片中的文字
     */
    @Override
    public String recognize(byte[] imageBytes, String mimeType) {
        try {
            BufferedImage image = ImageUtils.readImage(imageBytes);
            if (image == null) {
                throw new OcrException("无法读取图片，请确认文件格式正确");
            }

            String fullText = pipeline.process(image);

            if (fullText.isBlank()) {
                throw new OcrException("图片未识别到文字");
            }

            log.info("OCR 识别完成，文本长度: {}", fullText.length());
            return fullText;
        } catch (OcrException e) {
            throw e;
        } catch (Exception e) {
            throw new OcrException("OCR 推理失败: " + e.getMessage(), e);
        }
    }
}
