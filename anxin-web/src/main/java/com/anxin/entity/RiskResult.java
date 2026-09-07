package com.anxin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
@TableName("risk_result")
public class RiskResult implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 分析任务id，逻辑关联analysis_task.id
     */
    private Long taskId;

    /**
     * 文档id，逻辑关联document.id
     */
    private Long documentId;

    /**
     * 整体风险摘要
     */
    private String riskSummary;

    private Integer highCount;

    private Integer mediumCount;

    private Integer lowCount;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
