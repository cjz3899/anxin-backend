package com.anxin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSessionDTO {
    @NotBlank(message = "会话标题不能为空")
    @Size(max = 100, message = "会话标题不能超过100个字符")
    private String title;
}
