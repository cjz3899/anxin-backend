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
@TableName("document_section")
public class DocumentSection implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 文件id，逻辑关联document.id
     */
    private Long documentId;

    /**
     * 章节/条款编号
     */
    private String sectionNo;

    private String title;

    private String content;

    /**
     * 所在页码
     */
    private Integer pageNo;

    /**
     * 章节内部排序
     */
    private Integer sort;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
