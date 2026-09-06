package com.anxin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分析任务投递消息（异步处理链路）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnalysisTaskMessage {

    private Long taskId;

    private Long documentId;

    /**
     * 文件 OSS 公共读 URL，后续解析/异步审核从该地址取文件
     */
    private String fileUrl;

    /**
     * 文件类型：PDF / DOC / DOCX / IMAGE
     */
    private String fileType;
}
