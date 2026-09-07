package com.anxin.document.parser;

import com.anxin.document.parser.model.ParsedSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SectionSplitter {

    private static final Pattern CN_CLAUSE = Pattern.compile("^(第[一二三四五六七八九十百千零壹贰叁肆伍陆柒捌玖拾\\d]+[条款章节款条])(.*)$", Pattern.MULTILINE);
    private static final Pattern NUM_CLAUSE = Pattern.compile("^(\\d+(?:\\.\\d+)*)[.、](.*)$", Pattern.MULTILINE);
    private static final Pattern EN_CLAUSE = Pattern.compile("^(Article|Section|Clause)\\s+(\\d+)\\s*(.*)$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private record Match(int start, String sectionNo, String title) {
    }

    private SectionSplitter() {
    }

    public static List<ParsedSection> splitByClause(String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return List.of();
        }
        String normalized = fullText.replace("\r\n", "\n");
        Map<Integer, Match> matchMap = new TreeMap<>();
        collectCn(normalized, matchMap);
        collectEn(normalized, matchMap);
        collectNum(normalized, matchMap);
        if (matchMap.isEmpty()) {
            return splitByParagraph(normalized);
        }
        List<Match> matches = new ArrayList<>(matchMap.values());
        List<ParsedSection> sections = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);
            int end = (i + 1 < matches.size()) ? matches.get(i + 1).start() : normalized.length();
            String chunk = normalized.substring(match.start(), end).trim();
            if (!chunk.isEmpty()) {
                sections.add(ParsedSection.builder()
                        .sectionNo(match.sectionNo())
                        .title(match.title())
                        .content(chunk)
                        .sortOrder(i)
                        .build());
            }
        }
        return sections;
    }

    private static void collectCn(String text, Map<Integer, Match> matchMap) {
        Matcher matcher = CN_CLAUSE.matcher(text);
        while (matcher.find()) {
            matchMap.putIfAbsent(matcher.start(), new Match(matcher.start(), matcher.group(1).trim(), firstLine(matcher.group(2))));
        }
    }

    private static void collectEn(String text, Map<Integer, Match> matchMap) {
        Matcher matcher = EN_CLAUSE.matcher(text);
        while (matcher.find()) {
            matchMap.putIfAbsent(matcher.start(), new Match(matcher.start(), (matcher.group(1) + " " + matcher.group(2)).trim(), firstLine(matcher.group(3))));
        }
    }

    private static void collectNum(String text, Map<Integer, Match> matchMap) {
        Matcher matcher = NUM_CLAUSE.matcher(text);
        while (matcher.find()) {
            matchMap.putIfAbsent(matcher.start(), new Match(matcher.start(), matcher.group(1).trim(), firstLine(matcher.group(2))));
        }
    }

    private static List<ParsedSection> splitByParagraph(String text) {
        String[] paragraphs = text.split("\\n\\s*\\n");
        List<ParsedSection> sections = new ArrayList<>();
        for (int i = 0; i < paragraphs.length; i++) {
            String chunk = paragraphs[i].trim();
            if (!chunk.isEmpty()) {
                sections.add(ParsedSection.builder()
                        .sectionNo(String.valueOf(i + 1))
                        .content(chunk)
                        .sortOrder(i)
                        .build());
            }
        }
        return sections;
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] lines = text.split("\\n");
        String first = lines[0].trim();
        return first.isEmpty() ? null : first;
    }
}