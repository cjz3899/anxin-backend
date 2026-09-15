package com.anxin.service.support.extract;

import com.anxin.parser.DocumentParser;
import com.anxin.parser.model.ParsedSection;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;

/**
 * 文档策略：Tika 解析 PDF/Word 全文并按条款切分（DocumentParser 内部按内容自动探测格式）
 */
@Slf4j
@Component
public class DocumentSectionExtractor implements SectionExtractor {

    private static final Set<String> SUPPORTED_TYPES = Set.of("PDF", "DOC", "DOCX");

    @Resource
    private DocumentParser documentParser;

    @Override
    public Set<String> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public List<ParsedSection> extract(Long documentId, String fileType, byte[] bytes) {
        List<ParsedSection> sections = documentParser.parse(new ByteArrayInputStream(bytes), fileType);
        log.info("文档解析完成 documentId : {}, fileType : {}, 拆节数 : {}", documentId, fileType, sections.size());
        return sections;
    }
}
