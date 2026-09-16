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
@TableName("chat_session")
public class ChatSession implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户id，逻辑关联user.id
     */
    private Long userId;

    /**
     * 文件id，逻辑关联document.id
     */
    private Long documentId;

    private String title;

    /**
     * 0-关闭，1-正常
     */
    private Integer status;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
