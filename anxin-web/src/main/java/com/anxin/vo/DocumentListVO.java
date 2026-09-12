package com.anxin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentListVO {
    private String id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String status;
    private String summary;
    /**
     * 整体风险等级：HIGH/MEDIUM/LOW（服务端由最新风险结果的各级数量推导）；
     * null 表示暂无分析结果（图7 卡片徽标显示「已完成」）
     */
    private String riskLevel;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
