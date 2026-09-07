package com.anxin.document.parser;

import com.anxin.document.parser.model.ParsedSection;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;

import java.io.InputStream;
import java.util.List;


/**
 * PDF 文档解析器。
 */
@Slf4j
@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public List<ParsedSection> parse(InputStream inputStream, String fileType) {
        try {
            log.info("开始解析PDF文档");
            AutoDetectParser parser = new AutoDetectParser();

            ContentHandler handler = new BodyContentHandler();
            parser.parse(inputStream, handler, new org.apache.tika.metadata.Metadata());
            return SectionSplitter.split(handler.toString());
        } catch (Exception e) {
            log.error("PDF解析失败", e);
            return List.of();
        }
    }
}
