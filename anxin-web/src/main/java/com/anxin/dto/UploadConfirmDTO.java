package com.anxin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UploadConfirmDTO {

    /**
     * 签发凭证时返回的对象名，必须原样回传
     */
    @NotBlank(message = "objectKey 不能为空")
    @Size(max = 512, message = "objectKey 过长")
    private String objectKey;

    /**
     * 展示用文件名，与签发时一致
     */
    @Size(max = 255, message = "文件名最长255字符")
    private String fileName;
}
