package com.anxin.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ParsedSection implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String sectionNo;

    private String title;

    private String content;

    private Integer pageNo;

    private Integer sortOrder;
}