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
public class DocumentDetailVO {
    private String id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String status;
    private String summary;
    private Integer highCount;
    private Integer mediumCount;
    private Integer lowCount;
    private String latestTaskId;
    private String taskStatus;
    private String errorMessage;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
