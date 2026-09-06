package com.anxin.service.support;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class FileTypeService {
    /**
     * Tika：根据文件二进制内容判断MIME类型
     */
    private final Tika tika = new Tika();

    /**
     * 文档白名单
     */
    private static final Set<String> DOCUMENT_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
            "application/rtf"
    );

    /**
     * 图片白名单
     */
    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/bmp",
            "image/webp"
    );

    /**
     * 头像图片白名单（微信小程序规范：BMP/JPEG/JPG/GIF/PNG）
     */
    private static final Set<String> AVATAR_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/bmp"
    );

    /**
     * 文件上传白名单（题目要求：图片 jpg/png + PDF/Word）
     */
    private static final Set<String> UPLOAD_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    /**
     * MIME类型到扩展名的映射
     */
    private static final Map<String, String> MIME_TO_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/bmp", "bmp",
            "image/webp", "webp",
            "application/pdf", "pdf",
            "application/msword", "doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
            "dotx",
            "application/rtf", "rtf"
    );

    public String detectMime(byte[] data) {
        //Tika 对个别 OOXML 可能返回 application/x-tika-ooxml；
        // TODO 可选加强：读 zip 内 [Content_Types].xml 判定是否含 word/document.xml 细分出 docx，此处暂不实现
        return tika.detect(data);
    }

    public String realExtOf(String mime) {
        return MIME_TO_EXT.get(mime);
    }

    public boolean isImage(String mime) {
        return IMAGE_MIME_TYPES.contains(mime);
    }

    public boolean isAvatar(String mime) {
        return AVATAR_MIME_TYPES.contains(mime);
    }

    public boolean isUploadAllowed(String mime) {
        return UPLOAD_MIME_TYPES.contains(mime);
    }

    public boolean isUploadImage(String mime) {
        return "image/jpeg".equals(mime) || "image/png".equals(mime);
    }

    public boolean isDocument(String mime) {
        return DOCUMENT_MIME_TYPES.contains(mime);
    }


}
