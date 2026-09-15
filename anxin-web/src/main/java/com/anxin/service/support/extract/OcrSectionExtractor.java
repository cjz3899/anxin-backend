package com.anxin.service.support.extract;

import com.anxin.exception.NonRetryableTaskException;
import com.anxin.ocr.OcrException;
import com.anxin.ocr.service.OcrService;
import com.anxin.parser.SectionSplitter;
import com.anxin.parser.model.ParsedSection;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 图片策略：OCR 识别全文后复用文档同一套条款切分逻辑，使图片与 PDF/Word 走完全一致的下游链路
 */
@Slf4j
@Component
public class OcrSectionExtractor implements SectionExtractor {

    private static final Set<String> SUPPORTED_TYPES = Set.of("IMAGE");

    @Resource
    private OcrService ocrService;

    @Override
    public Set<String> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public List<ParsedSection> extract(Long documentId, String fileType, byte[] bytes) {
        try {
            //mime 当前未参与 OCR 推理，仅作接口语义占位
            String fullText = ocrService.recognize(bytes, "image/jpeg");
            if (fullText == null || fullText.isBlank()) {
                throw new NonRetryableTaskException("未识别到有效条款内容，请上传清晰的合同图片");
            }
            log.info("图片 OCR 完成 documentId : {}, 文本长度 : {}", documentId, fullText.length());
            return SectionSplitter.splitByClause(fullText);
        } catch (OcrException e) {
            //图片模糊、无文字属业务性失败，重试无意义
            throw new NonRetryableTaskException("图片识别失败：" + e.getMessage());
        }
    }
}
