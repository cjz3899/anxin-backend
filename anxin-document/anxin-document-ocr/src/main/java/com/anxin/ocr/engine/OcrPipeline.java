package com.anxin.ocr.engine;

import com.anxin.ocr.model.TextBox;
import com.anxin.ocr.util.ImageUtils;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * OCR 管线
 */
@Slf4j
public class OcrPipeline {

    private final DetEngine detEngine;
    private final RecEngine recEngine;

    public OcrPipeline(DetEngine detEngine, RecEngine recEngine) {
        this.detEngine = detEngine;
        this.recEngine = recEngine;
    }

    public String process(BufferedImage image) throws Exception {
        List<TextBox> boxes = detEngine.detect(image);

        if (boxes.isEmpty()) {
            log.warn("未检测到文本区域");
            return "";
        }

        log.info("检测到 {} 个文本区域", boxes.size());

        StringBuilder fullText = new StringBuilder();
        for (TextBox box : boxes) {
            BufferedImage cropped = ImageUtils.fourPointTransform(image, box.getPoints());
            String text = recEngine.recognize(cropped);
            if (text != null && !text.isBlank()) {
                fullText.append(text).append("\n");
            }
        }

        return fullText.toString().trim();
    }
}
