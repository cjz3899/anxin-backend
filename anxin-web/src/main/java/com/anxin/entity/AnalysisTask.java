package com.anxin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 分析任务（对应 analysis_task 表）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("analysis_task")
public class AnalysisTask implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键由应用侧生成：事务消息的消息体要在投递前就带上 taskId，供 broker 回查时核对本地事务结果
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 文件ID，逻辑关联 document.id
     */
    private Long documentId;

    /**
     * 任务类型：RISK_ANALYSIS 等
     */
    private String taskType;

    /**
     * 任务状态：0-待处理，1-处理中，2-成功，3-失败
     */
    private Integer status;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 失败原因
     */
    private String errorMessage;

    /**
     * 任务开始时间
     */
    private LocalDateTime startedTime;

    /**
     * 任务完成时间
     */
    private LocalDateTime finishedTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
