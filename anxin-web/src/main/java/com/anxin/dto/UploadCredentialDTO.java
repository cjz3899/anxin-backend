package com.anxin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UploadCredentialDTO {

    /**
     * 客户端声明的原始文件名，仅用于取扩展名和落库展示，不参与对象路径拼接
     */
    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名最长255字符")
    private String fileName;
}
