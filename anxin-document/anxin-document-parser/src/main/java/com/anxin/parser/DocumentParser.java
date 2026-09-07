package com.anxin.parser;

import com.anxin.parser.model.ParsedSection;

import java.io.InputStream;
import java.util.List;

public interface DocumentParser {

    List<ParsedSection> parse(InputStream inputStream, String fileType) throws Exception;
}