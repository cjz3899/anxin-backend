package com.anxin.service.impl;

import com.anxin.constant.UploadConstant;
import com.anxin.entity.AnalysisTask;
import com.anxin.entity.Document;
import com.anxin.enums.ResultCode;
import com.anxin.enums.TaskStatus;
import com.anxin.exception.ServiceException;
import com.anxin.mapper.AnalysisTaskMapper;
import com.anxin.mapper.DocumentMapper;
import com.anxin.rocketmq.message.AnalysisTaskMessage;
import com.anxin.rocketmq.producer.TaskProducer;
import com.anxin.service.IDocumentService;
import com.anxin.service.support.FileTypeService;
import com.anxin.service.support.OssStorageService;
import com.anxin.service.support.WxSecurityService;
import com.anxin.threadlocal.BaseContext;
import com.anxin.vo.DocumentUploadVO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * 文档上传与分析任务创建
 */
@Slf4j
@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements IDocumentService {

    /**
     * 任务类型：风险分析
     */
    private static final String TASK_TYPE_RISK_ANALYSIS = "RISK_ANALYSIS";

    /**
     * 仅用于上传前的初步大小分档，最终类型以 Tika 检测为准
     */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");

    @Resource
    private FileTypeService fileTypeService;

    @Resource
    private OssStorageService ossStorageService;

    @Resource
    private WxSecurityService wxSecurityService;

    @Resource
    private AnalysisTaskMapper analysisTaskMapper;

    @Resource
    private TaskProducer taskProducer;

    @Override
    public DocumentUploadVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), "请选择要上传的文件");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        long size = file.getSize();
        //获取文件扩展名
        String ext = extensionOf(originalName);

        //初步大小闸门：按扩展名预判类别（图片5MB/文档10MB），超限立即拒绝
        boolean likelyImage = IMAGE_EXTENSIONS.contains(ext);
        long preLimit = likelyImage ? UploadConstant.IMAGE_MAX_BYTES : UploadConstant.DOC_MAX_BYTES;
        if (size > preLimit) {
            throw new ServiceException(ResultCode.FILE_SIZE_EXCEEDED.getCode(),
                    likelyImage ? "图片大小不能超过5MB" : "文档大小不能超过10MB");
        }

        //读流（此时大小已被闸门限制在 10MB 内）
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("读取上传文件失败 fileName : {}", originalName, e);
            throw new ServiceException(ResultCode.FILE_SAVE_FAILED);
        }

        //Tika 按文件头魔数检测真实类型，白名单校验（isImage/isDocument，防改名伪装的主闸门）
        String mime = fileTypeService.detectMime(bytes);
        if (!fileTypeService.isImage(mime) && !fileTypeService.isDocument(mime)) {
            log.warn("拒绝非白名单文件 fileName : {}, 真实类型 : {}", originalName, mime);
            throw new ServiceException(ResultCode.FILE_TYPE_NOT_SUPPORTED.getCode(),
                    "不支持的文件类型，仅支持 PDF/Word 与 jpg/png 图片");
        }

        //按真实类型复核大小上限（图片5MB，文档10MB）
        if (fileTypeService.isImage(mime)) {
            if (size > UploadConstant.IMAGE_MAX_BYTES) {
                throw new ServiceException(ResultCode.FILE_SIZE_EXCEEDED.getCode(), "图片大小不能超过5MB");
            }
            if (size <= UploadConstant.SYNC_CHECK_MAX_BYTES) {
                // 图片 ≤4MB：同步 imgSecCheck，违规（87014）在此抛 10008，文件不会进入 OSS
                wxSecurityService.checkImage(bytes);
            } else {
                // 图片 >4MB：走异步审核（当前为骨架 mock 放行，见 AnalysisTaskConsumer TODO）
                log.warn("图片超过4MB，异步审核暂为骨架，直接放行 size : {}", size);
            }
        } else {
            // PDF/Word：异步审核（骨架 mock 放行）
            log.warn("文档类文件异步审核暂为骨架，直接放行 mime : {}", mime);
        }

        //存储：UUID + Tika 真实后缀，路径不拼接用户文件名
        String key = ossStorageService.upload(bytes, mime, "documents", fileTypeService.realExtOf(mime));
        String fileUrl = ossStorageService.toUrl(key);

        //落库 document（PENDING）
        String fileType = fileTypeNameOf(mime);
        Document document = Document.builder()
                .userId(BaseContext.getCurrentId())
                .fileName(truncate(originalName.isBlank() ? "未命名文件" : originalName, 255))
                .fileType(fileType)
                .fileSize(size)
                .fileUrl(fileUrl)
                .status(TaskStatus.PENDING.getCode())
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .build();
        save(document);

        //落库 analysis_task（PENDING）并投递异步任务
        AnalysisTask task = AnalysisTask.builder()
                .documentId(document.getId())
                .taskType(TASK_TYPE_RISK_ANALYSIS)
                .status(TaskStatus.PENDING.getCode())
                .retryCount(0)
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .build();
        analysisTaskMapper.insert(task);

        taskProducer.dispatch(AnalysisTaskMessage.builder()
                .taskId(task.getId())
                .documentId(document.getId())
                .fileUrl(fileUrl)
                .fileType(fileType)
                .build());

        log.info("文件上传成功 documentId : {}, taskId : {}", document.getId(), task.getId());
        return DocumentUploadVO.builder()
                .documentId(String.valueOf(document.getId()))
                .taskId(String.valueOf(task.getId()))
                .status(TaskStatus.PENDING.name())
                .build();
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 真实 MIME → document.file_type 存储值
     */
    private String fileTypeNameOf(String mime) {
        if (fileTypeService.isImage(mime)) {
            return "IMAGE";
        }
        return switch (mime) {
            case "application/pdf" -> "PDF";
            case "application/msword" -> "DOC";
            default -> "DOCX";
        };
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
