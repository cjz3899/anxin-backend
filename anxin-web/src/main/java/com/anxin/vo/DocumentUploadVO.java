package com.anxin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传成功出参。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentUploadVO {

    /**
     * 文件 ID（String，防前端精度丢失）
     */
    private String documentId;

    /**
     * 分析任务 ID（String）
     */
    private String taskId;

    /**
     * 任务状态：PENDING
     */
    private String status;
}
