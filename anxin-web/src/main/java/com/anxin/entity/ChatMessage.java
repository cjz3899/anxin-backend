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

    /**
     * 回答是异步生成的，ID 必须先生成好才能立刻返回给前端轮询
     */
    @TableId(value = "id", type = IdType.INPUT)
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
     * 状态，取值同 TaskStatus：0-待生成，1-生成中，2-成功，3-失败。用户消息落库即成功
     */
    private Integer status;

    /**
     * 生成失败原因
     */
    private String errorMessage;

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
