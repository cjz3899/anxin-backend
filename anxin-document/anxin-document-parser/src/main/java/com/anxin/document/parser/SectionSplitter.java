package com.anxin.document.parser;

import com.anxin.document.parser.model.ParsedSection;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档章节分割器。
 */
public class SectionSplitter {

    /**
     * 使用正则表达式匹配章节标题。
     */
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "^(第[一二三四五六七八九十百千零\\d]+[条款章节]|\\d+(?:\\.\\d+)*[.、\\s])",
            Pattern.MULTILINE
    );

    public static List<ParsedSection> split(String text) {
        // 解析出的章节列表 sections
        List<ParsedSection> sections = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return sections;
        }

        Matcher matcher = SECTION_PATTERN.matcher(text);
        List<int[]> positions = new ArrayList<>();
        while (matcher.find()) {
            positions.add(new int[]{matcher.start(), matcher.end()});
        }

        if (positions.isEmpty()) {
            sections.add(ParsedSection.builder()
                    .sectionNo("全文")
                    .content(text.trim())
                    .pageNo(1)
                    .sortOrder(0)
                    .build());
            return sections;
        }

        for (int i = 0; i < positions.size(); i++) {
            int start = positions.get(i)[0];
            int end = (i + 1 < positions.size()) ? positions.get(i + 1)[0] : text.length();

            String sectionText = text.substring(start, end).trim();
            String header = text.substring(positions.get(i)[0], positions.get(i)[1]).trim();
            String content = sectionText.substring(header.length()).trim();

            if (!content.isEmpty()) {
                sections.add(ParsedSection.builder()
                        .sectionNo(header)
                        .content(content)
                        .pageNo(1)
                        .sortOrder(i)
                        .build());
            }
        }

        return sections;
    }
}
