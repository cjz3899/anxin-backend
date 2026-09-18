package com.anxin.service.impl;

import com.anxin.constant.UploadConstant;
import com.anxin.entity.*;
import com.anxin.enums.ResultCode;
import com.anxin.enums.TaskStatus;
import com.anxin.exception.ServiceException;
import com.anxin.mapper.*;
import com.anxin.result.PageResult;
import com.anxin.rocketmq.message.AnalysisTaskMessage;
import com.anxin.rocketmq.producer.TaskProducer;
import com.anxin.service.IDocumentService;
import com.anxin.service.support.FileTypeService;
import com.anxin.service.support.OssStorageService;
import com.anxin.service.support.RiskLevelCalculator;
import com.anxin.threadlocal.BaseContext;
import com.anxin.util.SnowUtil;
import com.anxin.vo.DocumentDetailVO;
import com.anxin.vo.DocumentListVO;
import com.anxin.vo.DocumentUploadVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

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
    private AnalysisTaskMapper analysisTaskMapper;

    @Resource
    private TaskProducer taskProducer;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private RiskResultMapper riskResultMapper;

    @Resource
    private RiskDetailMapper riskDetailMapper;

    @Resource
    private DocumentSectionMapper documentSectionMapper;

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
        if (fileTypeService.isImage(mime) && size > UploadConstant.IMAGE_MAX_BYTES) {
            throw new ServiceException(ResultCode.FILE_SIZE_EXCEEDED.getCode(), "图片大小不能超过5MB");
        }

        //存储：UUID + Tika 真实后缀，路径不拼接用户文件名
        String key = ossStorageService.upload(bytes, mime, "documents", fileTypeService.realExtOf(mime));
        String fileUrl = ossStorageService.toUrl(key);

        //主键由应用侧生成：消息体在投递前就要带上两个 ID，紧贴投递生成以缩短「生成 ID → 落库可见」的窗口
        long documentId = SnowUtil.nextId();
        long taskId = SnowUtil.nextId();
        String fileType = fileTypeNameOf(mime);
        LocalDateTime now = LocalDateTime.now();

        Document document = Document.builder()
                .id(documentId)
                .userId(BaseContext.getCurrentId())
                .fileName(truncate(originalName.isBlank() ? "未命名文件" : originalName, 255))
                .fileType(fileType)
                .fileSize(size)
                .fileUrl(fileUrl)
                .status(TaskStatus.PENDING.getCode())
                .createdTime(now)
                .updatedTime(now)
                .build();
        AnalysisTask task = AnalysisTask.builder()
                .id(taskId)
                .documentId(documentId)
                .taskType(TASK_TYPE_RISK_ANALYSIS)
                .status(TaskStatus.PENDING.getCode())
                .retryCount(0)
                .createdTime(now)
                .updatedTime(now)
                .build();

        //半消息先落盘，这两条落库成功才提交消息：投递失败则两条都不写，不会留下永远「分析中」的空记录
        taskProducer.dispatchInTransaction(AnalysisTaskMessage.builder()
                .taskId(taskId)
                .documentId(documentId)
                .fileUrl(fileUrl)
                .fileType(fileType)
                .build(), () -> {
            documentMapper.insert(document);
            analysisTaskMapper.insert(task);
        });

        log.info("文件上传成功 documentId : {}, taskId : {}", documentId, taskId);
        return DocumentUploadVO.builder()
                .documentId(String.valueOf(documentId))
                .taskId(String.valueOf(taskId))
                .status(TaskStatus.PENDING.name())
                .build();
    }

    @Override
    public PageResult<DocumentListVO> getListDocuments(Integer pageSize, String statusGroup, String cursor) {
        int size = (pageSize == null || pageSize < 1) ? 5 : Math.min(pageSize, 10);
        String group = normalizeStatusGroup(statusGroup);
        Long cursorId = parserCursor(cursor);
        Long userId = BaseContext.getCurrentId();
        long total = documentMapper.countByUser(userId, group);
        //多取一条用于判断是否还有下一页
        List<Document> documents = documentMapper.selectPageByUser(userId, group, cursorId, size + 1);

        //多取了一条：超过 size 说明还有下一页，截掉用于探测的最后一条
        boolean hasMore = documents.size() > size;
        documents = hasMore ? documents.subList(0, size) : documents;

        Map<Long, RiskResult> latestResultByDocId = loadLatestResults(documents);
        List<DocumentListVO> records = documents.stream().map(d -> {
            RiskResult latest = latestResultByDocId.get(d.getId());
            return DocumentListVO.builder()
                    .id(String.valueOf(d.getId()))
                    .fileName(d.getFileName())
                    .fileType(d.getFileType())
                    .fileSize(d.getFileSize())
                    .status(TaskStatus.fromCode(d.getStatus()).name())
                    .summary(latest == null ? null : latest.getRiskSummary())
                    //整体等级由各级数量在服务端推导，数量本身不返回前端
                    .riskLevel(latest == null ? null : RiskLevelCalculator.derive(
                            latest.getHighCount(), latest.getMediumCount(), latest.getLowCount()))
                    .createdTime(d.getCreatedTime())
                    .updatedTime(d.getUpdatedTime())
                    .build();
        }).toList();

        //有下一页时，游标取本页最后一条的 id
        String nextCursor = hasMore ? String.valueOf(documents.get(documents.size() - 1).getId()) : null;
        return PageResult.of(records, nextCursor, total);
    }

    @Override
    public DocumentDetailVO detail(Long documentId) {
        Document document = getOwnedDocument(documentId);
        AnalysisTask task = analysisTaskMapper.selectOne(new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getDocumentId, documentId)
                .orderByDesc(AnalysisTask::getId)
                .last("LIMIT 1"));
        RiskResult riskResult = riskResultMapper.selectOne(new LambdaQueryWrapper<RiskResult>()
                .eq(RiskResult::getDocumentId, documentId)
                .orderByDesc(RiskResult::getId)
                .last("LIMIT 1"));
        return DocumentDetailVO.builder()
                .id(String.valueOf(document.getId()))
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .status(TaskStatus.fromCode(document.getStatus()).name())
                .summary(riskResult == null ? null : riskResult.getRiskSummary())
                .riskLevel(riskResult == null ? null : RiskLevelCalculator.derive(riskResult.getHighCount(),
                        riskResult.getMediumCount(),
                        riskResult.getLowCount()))
                .latestTaskId(task == null ? null : String.valueOf(task.getId()))
                .taskStatus(task == null ? null : TaskStatus.fromCode(task.getStatus()).name())
                .errorMessage(task == null ? null : task.getErrorMessage())
                .createdTime(document.getCreatedTime())
                .updatedTime(document.getUpdatedTime())
                .build();
    }

    @Override
    public void deleteDocument(Long documentId) {
        Document document = getOwnedDocument(documentId);
        //先删任务行：防止在途消息被消费端"条件更新抢占"后复活数据
        analysisTaskMapper.delete(new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getDocumentId, documentId));
        //为什么要使用selectList，删除之前重试生成的残留数据
        List<RiskResult> results = riskResultMapper.selectList(new LambdaQueryWrapper<RiskResult>()
                .eq(RiskResult::getDocumentId, documentId));
        for (RiskResult result : results) {
            riskDetailMapper.delete(new LambdaQueryWrapper<RiskDetail>()
                    .eq(RiskDetail::getRiskResultId, result.getId()));
        }
        riskResultMapper.delete(new LambdaQueryWrapper<RiskResult>()
                .eq(RiskResult::getDocumentId, documentId));
        documentSectionMapper.delete(new LambdaQueryWrapper<DocumentSection>()
                .eq(DocumentSection::getDocumentId, documentId));
        removeById(documentId);
        // 放最后：OSS 删除失败不影响已清理的数据，仅告警
        ossStorageService.delete(document.getFileUrl());
    }

    @Override
    public DocumentUploadVO reanalyze(Long documentId) {
        Document document = getOwnedDocument(documentId);
        Long processing = analysisTaskMapper.selectCount(new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getDocumentId, documentId)
                .in(AnalysisTask::getStatus, TaskStatus.PENDING.getCode(), TaskStatus.PROCESSING.getCode()));
        if (processing != null && processing > 0) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), "已有进行中的分析任务，请稍后再试");
        }
        return createAnalysisTask(document);
    }

    /**
     * 创建分析任务并投递消息（upload 与 reanalyze 共用）
     */
    private DocumentUploadVO createAnalysisTask(Document document) {
        long taskId = SnowUtil.nextId();
        LocalDateTime now = LocalDateTime.now();
        AnalysisTask task = AnalysisTask.builder()
                .id(taskId)
                .documentId(document.getId())
                .taskType(TASK_TYPE_RISK_ANALYSIS)
                .status(TaskStatus.PENDING.getCode())
                .retryCount(0)
                .createdTime(now)
                .updatedTime(now)
                .build();
        taskProducer.dispatchInTransaction(AnalysisTaskMessage.builder()
                .taskId(taskId)
                .documentId(document.getId())
                .fileUrl(document.getFileUrl())
                .fileType(document.getFileType())
                .build(), () -> {
            analysisTaskMapper.insert(task);
            //列表页读的是 document.status，不一起置回 PENDING 会出现「列表已完成、详情分析中」
            documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                    .eq(Document::getId, document.getId())
                    .set(Document::getStatus, TaskStatus.PENDING.getCode())
                    .set(Document::getUpdatedTime, LocalDateTime.now()));
        });
        return DocumentUploadVO.builder()
                .documentId(String.valueOf(document.getId()))
                .taskId(String.valueOf(taskId))
                .status(TaskStatus.PENDING.name())
                .build();
    }

    /**
     * 归属校验，文件不存在或非当前用户所有，统一按不存在处理
     */
    private Document getOwnedDocument(Long documentId) {
        Document document = getById(documentId);
        if (document == null || !document.getUserId().equals(BaseContext.getCurrentId())) {
            throw new ServiceException(ResultCode.DOCUMENT_NOT_EXIST);
        }
        return document;
    }

    /**
     * 批量取每个文件最新一条风险结果（摘要 + 各级数量；结果按 id 倒序，putIfAbsent 保留最新）
     */
    private Map<Long, RiskResult> loadLatestResults(List<Document> documents) {
        if (documents.isEmpty()) {
            return Map.of();
        }
        List<Long> documentIds = documents.stream().map(Document::getId).toList();
        /**
         * 相当于select * from risk_result
         * where document_id in (?, ?, ? ...)
         * order by id desc
         */
        List<RiskResult> results = riskResultMapper.selectList(new LambdaQueryWrapper<RiskResult>()
                .in(RiskResult::getDocumentId, documentIds)
                .orderByDesc(RiskResult::getId));
        Map<Long, RiskResult> latestByDocId = new HashMap<>();
        for (RiskResult result : results) {
            latestByDocId.putIfAbsent(result.getDocumentId(), result);
        }
        return latestByDocId;
    }

    /**
     * 游标解析：null/空 = 第一页；非数字或非正值按参数错误拒绝
     */
    private Long parserCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            Long id = Long.valueOf(cursor);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), "非法的分页游标 : " + cursor);
        }
    }

    /**
     * Tab 状态分组：ALL-全部，PROCESSING-分析中（PENDING/PROCESSING），SUCCESS-已完成，FAILED-失败
     */
    private String normalizeStatusGroup(String statusGroup) {
        if (statusGroup == null || statusGroup.isBlank() || "ALL".equals(statusGroup)) {
            return "ALL";
        }
        if ("PROCESSING".equals(statusGroup) || "SUCCESS".equals(statusGroup) || "FAILED".equals(statusGroup)) {
            return statusGroup;
        }
        throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), "非法的状态分组 : " + statusGroup);
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