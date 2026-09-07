package com.anxin.service.support;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileTypeService {
    /**
     * Tika：根据文件二进制内容判断MIME类型
     */
    private final Tika tika = new Tika();

    /**
     * Tika 无法细分 OOXML 家族时返回的兜底 MIME
     */
    private static final String TIKA_OOXML_MIME = "application/x-tika-ooxml";

    /**
     * docx 标准 MIME
     */
    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /**
     * 文档白名单（上传白名单口径，PDF/Word）
     */
    private static final Set<String> DOCUMENT_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            DOCX_MIME
    );

    /**
     * 图片白名单（上传白名单口径，jpg/png）
     */
    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png"
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
            DOCX_MIME, "docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
            "dotx",
            "application/rtf", "rtf"
    );

    public String detectMime(byte[] data) {
        String mime = tika.detect(data);
        //Tika 对个别 OOXML 可能返回 application/x-tika-ooxml，兜底按包内是否含 word/document.xml 细分出 docx
        if (TIKA_OOXML_MIME.equals(mime) && containsWordDocument(data)) {
            return DOCX_MIME;
        }
        return mime;
    }

    /**
     * docx 包内固定存在 word/document.xml 条目，据此兜底判定
     */
    private boolean containsWordDocument(byte[] data) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
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

    public boolean isDocument(String mime) {
        return DOCUMENT_MIME_TYPES.contains(mime);
    }


}
