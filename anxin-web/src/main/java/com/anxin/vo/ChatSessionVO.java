package com.anxin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatSessionVO {
    private String id;
    private String documentId;
    private String title;
    /**
     * OPEN/CLOSE
     */
    private String status;
    private LocalDateTime createdTime;
}
