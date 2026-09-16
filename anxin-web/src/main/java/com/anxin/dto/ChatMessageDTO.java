package com.anxin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatMessageDTO {
    @NotBlank(message = "提示内容不能为空")
    @Size(max = 1000, message = "提问内容最长1000字符")
    private String content;
}
