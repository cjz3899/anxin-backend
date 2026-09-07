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

@Component
public class DocxDocumentParser implements DocumentParser {

    @Override
    public List<ParsedSection> parse(InputStream inputStream, String fileType) throws Exception {
        Parser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        parser.parse(inputStream, handler, metadata, parseContext);
        return SectionSplitter.splitByClause(handler.toString());
    }
}