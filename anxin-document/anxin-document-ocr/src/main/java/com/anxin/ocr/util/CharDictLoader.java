package com.anxin.ocr.util;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 加载字符字典
 */
public class CharDictLoader {

    private static final int BLANK_INDEX = 0;

    private final List<String> charList;

    public CharDictLoader(ResourceLoader resourceLoader, String charDictPath) throws Exception {
        Resource resource = resourceLoader.getResource(charDictPath);
        this.charList = new ArrayList<>();
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                this.charList.add(line);
            }
        }
    }

    public String decode(List<Integer> indices) {
        StringBuilder sb = new StringBuilder();
        Integer prev = null;
        for (int idx : indices) {
            if (idx == BLANK_INDEX) {
                prev = idx;
                continue;
            }
            if (prev != null && prev == idx) {
                continue;
            }
            prev = idx;
            int charIndex = idx - 1;
            if (charIndex >= 0 && charIndex < charList.size()) {
                sb.append(charList.get(charIndex));
            }
        }
        return sb.toString();
    }

    public int size() {
        return charList.size();
    }
}