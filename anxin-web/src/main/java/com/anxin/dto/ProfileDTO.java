package com.anxin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProfileDTO {
    @NotBlank(message = "昵称不能为空")
    private String nickname;
    private String avatar;
}
