package com.anxin.constant;

import java.time.Duration;

public class UploadConstant {
    private static final int KB = 1024;
    private static final int MB = KB * 1024;

    public static final long AVATAR_MAX_BYTES = 2 * MB;
    public static final long IMAGE_MAX_BYTES = 5 * MB;
    public static final long DOC_MAX_BYTES = 10 * MB;

    /**
     * 直传凭证有效期：覆盖一次弱网上传即可，不留可重放的长窗口
     */
    public static final Duration CREDENTIAL_TTL = Duration.ofMinutes(10);

    /**
     * 魔数检测只读对象头部这么多字节，不为识别类型拉回整份文件
     */
    public static final int DETECT_HEAD_BYTES = 64 * KB;
}
