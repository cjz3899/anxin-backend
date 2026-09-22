package com.anxin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageVO {
    private String messageId;
    /**
     * USER/ASSISTANT
     */
    private String role;
    private String content;

    /**
     * PENDING/PROCESSING/SUCCESS/FAILED，回答是异步生成的，前端按这个字段决定要不要继续轮询
     */
    private String status;

    /**
     * 回答生成失败时给用户的提示，成功为 null
     */
    private String errorMessage;

    private List<ChatReferenceVO> references;
    private LocalDateTime createdTime;
}
