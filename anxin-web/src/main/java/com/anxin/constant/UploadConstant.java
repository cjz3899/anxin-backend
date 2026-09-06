package com.anxin.constant;

public class UploadConstant {
    private static final int KB = 1024;
    private static final int MB = KB * 1024;

    public static final long AVATAR_MAX_BYTES = 2 * MB;
    public static final long IMAGE_MAX_BYTES = 5 * MB;
    public static final long DOC_MAX_BYTES = 10 * MB;
    /**
     * 图片同步审核阈值：超过 4MB 走异步审核
     */
    public static final long SYNC_CHECK_MAX_BYTES = 4 * MB;
}
