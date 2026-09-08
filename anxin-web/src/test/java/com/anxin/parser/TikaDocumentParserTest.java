package com.anxin.parser;

import com.anxin.parser.model.ParsedSection;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TikaDocumentParser 解析与条款切分测试（内存构造最小 docx，不依赖 Spring 与外部文件）
 */
class TikaDocumentParserTest {

    private final TikaDocumentParser parser = new TikaDocumentParser();

    @Test
    void parseDocxAndSplitByClause() {
        List<ParsedSection> sections = parser.parse(
                new ByteArrayInputStream(minimalDocx()), "DOCX");

        assertEquals(2, sections.size());
        assertEquals("第一条", sections.get(0).getSectionNo());
        assertTrue(sections.get(0).getContent().contains("甲方"), "第一条内容不符 : " + sections.get(0).getContent());
        assertEquals("第二条", sections.get(1).getSectionNo());
        assertTrue(sections.get(1).getContent().contains("解除"), "第二条内容不符 : " + sections.get(1).getContent());
    }

    @Test
    void parseEmptyInputThrows() {
        assertThrows(DocumentParseException.class,
                () -> parser.parse(new ByteArrayInputStream(new byte[0]), "PDF"));
    }

    private byte[] minimalDocx() {
        String contentTypes = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """;
        String rels = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """;
        String document = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:r><w:t>第一条 甲方应按约定时间支付全部款项。</w:t></w:r></w:p>
                    <w:p><w:r><w:t>第二条 乙方有权单方面解除本协议且不承担赔偿责任。</w:t></w:r></w:p>
                    <w:p><w:r><w:t>本协议未尽事宜由双方友好协商解决。</w:t></w:r></w:p>
                  </w:body>
                </w:document>
                """;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(contentTypes.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("_rels/.rels"));
            zip.write(rels.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(document.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("构造测试 docx 失败", e);
        }
    }
}
