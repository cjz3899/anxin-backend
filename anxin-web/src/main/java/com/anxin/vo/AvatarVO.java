package com.anxin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 头像上传出参。
 */
@Data
@AllArgsConstructor
@Builder
public class AvatarVO {

    /**
     * 头像永久访问 URL（OSS 公共读）。
     * 注意：本接口只负责校验+存储+返回 URL，不落库；
     * 前端将 URL 与昵称一起提交 POST /api/user/profile 完成资料完善。
     */
    private String avatar;
}
