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
@TableName("risk_detail")
public class RiskDetail implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long riskResultId;

    private Long sectionId;

    private String riskType;

    private String riskLevel;

    private String title;

    private String originalText;

    private String reason;

    private String impact;

    private String suggestion;

    private Integer startPosition;

    private Integer endPosition;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
