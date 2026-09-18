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
@TableName("document")
public class Document implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键由应用侧生成：与 analysis_task 同批落库，消息体需要在投递前带上 documentId
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long userId;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String fileUrl;

    private Integer status;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
