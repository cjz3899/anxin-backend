package com.anxin.parser;

import com.anxin.parser.model.ParsedSection;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * 统一文档解析器：Tika 按内容自动探测 PDF/DOC/DOCX 并抽取全文，无需按文件类型分流实现
 */
@Component
public class TikaDocumentParser implements DocumentParser {

    @Override
    public List<ParsedSection> parse(InputStream inputStream, String fileType) {
        try {
            Parser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            parser.parse(inputStream, handler, metadata, parseContext);
            return SectionSplitter.splitByClause(handler.toString());
        } catch (Exception e) {
            throw new DocumentParseException("文档解析失败 fileType : " + fileType, e);
        }
    }
}
