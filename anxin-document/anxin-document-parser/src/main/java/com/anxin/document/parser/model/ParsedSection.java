package com.anxin.document.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档被解析后，每个章节用这个对象表示。它是模块间传递数据的载体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ParsedSection {

    private String sectionNo;

    private String title;

    private String content;

    private Integer pageNo;

    private Integer sortOrder;
}
