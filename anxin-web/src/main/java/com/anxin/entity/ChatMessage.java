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
@TableName("chat_message")
public class ChatMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 会话id，逻辑关联chat_session.id
     */
    private Long sessionId;

    /**
     * 消息角色：USER/ASSISTANT
     * 用户问的和llm答的
     */
    private String role;

    private String content;

    /**
     * 引用的文档章节id，JSON数组字符串
     */
    private String referenceSections;

    /**
     * 本次消息Token消耗
     */
    private Integer tokenUsage;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
