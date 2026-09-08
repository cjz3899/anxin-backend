package com.anxin.parser;

import com.anxin.parser.model.ParsedSection;

import java.io.InputStream;
import java.util.List;

/**
 * 文档解析接口：抽取文档全文并按条款切分
 */
public interface DocumentParser {

    /**
     * @param fileType 上传侧判定的文件类型（PDF/DOC/DOCX/IMAGE），当前 Tika 自动探测，仅作语义标注
     * @throws DocumentParseException 解析失败
     */
    List<ParsedSection> parse(InputStream inputStream, String fileType);
}
