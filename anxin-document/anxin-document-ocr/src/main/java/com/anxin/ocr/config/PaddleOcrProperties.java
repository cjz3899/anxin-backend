package com.anxin.ocr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置类
 */
@ConfigurationProperties(prefix = "anxin.ocr")
public record PaddleOcrProperties(
        String detModelPath,
        String recModelPath,
        String charDictPath,
        float detThreshold,
        float detBoxUnclipRatio,
        int recImageHeight,
        int recImageMaxWidth
) {
    public PaddleOcrProperties {
        if (detModelPath == null || detModelPath.isBlank()) detModelPath = "classpath:models/det/inference.onnx";
        if (recModelPath == null || recModelPath.isBlank()) recModelPath = "classpath:models/rec/inference.onnx";
        if (charDictPath == null || charDictPath.isBlank()) charDictPath = "classpath:models/rec/ppocrv6_dict.txt";
        if (detThreshold <= 0) detThreshold = 0.3f;
        if (detBoxUnclipRatio <= 0) detBoxUnclipRatio = 1.5f;
        if (recImageHeight <= 0) recImageHeight = 48;
        if (recImageMaxWidth <= 0) recImageMaxWidth = 320;
    }
}