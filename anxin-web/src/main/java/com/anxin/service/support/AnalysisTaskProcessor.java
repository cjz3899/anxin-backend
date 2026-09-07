package com.anxin.service.support;

import com.anxin.ai.llm.agent.RiskAnalysisAgent;
import com.anxin.ai.llm.model.RiskAnalysisResult;
import com.anxin.ai.llm.model.RiskDetailInfo;
import com.anxin.document.parser.DocumentParser;
import com.anxin.document.parser.model.ParsedSection;
import com.anxin.dto.AnalysisTaskMessage;
import com.anxin.entity.AnalysisTask;
import com.anxin.entity.Document;
import com.anxin.entity.DocumentSection;
import com.anxin.entity.RiskDetail;
import com.anxin.entity.RiskResult;
import com.anxin.enums.TaskStatus;
import com.anxin.mapper.AnalysisTaskMapper;
import com.anxin.mapper.DocumentMapper;
import com.anxin.mapper.DocumentSectionMapper;
import com.anxin.mapper.RiskDetailMapper;
import com.anxin.mapper.RiskResultMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class AnalysisTaskProcessor {

    private static final int MAX_RETRY = 3;

    @Resource
    private AnalysisTaskMapper analysisTaskMapper;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private DocumentSectionMapper documentSectionMapper;

    @Resource
    private RiskResultMapper riskResultMapper;

    @Resource
    private RiskDetailMapper riskDetailMapper;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private DocumentParser documentParser;

    @Resource
    private RiskAnalysisAgent riskAnalysisAgent;

    public void process(AnalysisTaskMessage message) {
        int rows = analysisTaskMapper.update(null, new LambdaUpdateWrapper<AnalysisTask>()
                .eq(AnalysisTask::getId, message.getTaskId())
                .eq(AnalysisTask::getStatus, TaskStatus.PENDING.getCode())
                .set(AnalysisTask::getStatus, TaskStatus.PROCESSING.getCode())
                .set(AnalysisTask::getStartedTime, LocalDateTime.now()));
        if (rows == 0) {
            log.warn("任务已被处理或不存在，跳过 taskId : {}", message.getTaskId());
            return;
        }
        try {
            doAnalysis(message);
            markFinished(message.getTaskId(), TaskStatus.SUCCESS, null);
        } catch (Exception e) {
            log.error("任务处理失败 taskId : {}", message.getTaskId(), e);
            AnalysisTask task = analysisTaskMapper.selectById(message.getTaskId());
            int retry = (task == null ? 0 : task.getRetryCount()) + 1;
            if (retry > MAX_RETRY) {
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                markFinished(message.getTaskId(), TaskStatus.FAILED, truncate(reason, 2000));
            } else {
                analysisTaskMapper.update(null, new LambdaUpdateWrapper<AnalysisTask>()
                        .eq(AnalysisTask::getId, message.getTaskId())
                        .set(AnalysisTask::getStatus, TaskStatus.PENDING.getCode())
                        .set(AnalysisTask::getRetryCount, retry));
                log.warn("任务将重试 taskId : {}, retry : {}", message.getTaskId(), retry);
            }
        }
    }

    /**
     * 执行解析。
     */
    private void doAnalysis(AnalysisTaskMessage message) {
        //1.下载文件
        byte[] fileBytes = downloadFile(message.getFileUrl());

        //2.解析文件
        log.info("开始解析文件 : {}", message.getFileUrl());
        List<ParsedSection> sections = documentParser.parse(
                new java.io.ByteArrayInputStream(fileBytes), message.getFileType());
        saveSections(message.getDocumentId(), sections);
        String fullText = buildFullText(sections);
        RiskAnalysisResult analysisResult = riskAnalysisAgent.analyze(fullText);
        saveRiskResult(message.getTaskId(), message.getDocumentId(), analysisResult, sections);
        updateDocumentSummary(message.getDocumentId(), analysisResult.getRiskSummary());
    }

    /**
     * 下载文件。
     */
    private byte[] downloadFile(String fileUrl) {
        log.info("开始下载文件 : {}", fileUrl);
        return restTemplate.getForObject(fileUrl, byte[].class);
    }

    private void saveSections(Long documentId, List<ParsedSection> sections) {
        for (int i = 0; i < sections.size(); i++) {
            ParsedSection section = sections.get(i);
            DocumentSection entity = DocumentSection.builder()
                    .documentId(documentId)
                    .sectionNo(section.getSectionNo())
                    .title(section.getTitle())
                    .content(section.getContent())
                    .pageNo(section.getPageNo())
                    .sort(i)
                    .createdTime(LocalDateTime.now())
                    .updatedTime(LocalDateTime.now())
                    .build();
            documentSectionMapper.insert(entity);
        }
    }

    /**
     * 构建全文。
     */
    private String buildFullText(List<ParsedSection> sections) {
        StringBuilder sb = new StringBuilder();
        for (ParsedSection section : sections) {
            sb.append("【").append(section.getSectionNo()).append("】\n");
            sb.append(section.getContent()).append("\n\n");
        }
        return sb.toString();
    }


    /**
     * 保存风险分析结果。
     */
    private void saveRiskResult(Long taskId, Long documentId, RiskAnalysisResult result, List<ParsedSection> sections) {
        RiskResult riskResult = RiskResult.builder()
                .taskId(taskId)
                .documentId(documentId)
                .riskSummary(result.getRiskSummary())
                .highCount(result.getHighCount())
                .mediumCount(result.getMediumCount())
                .lowCount(result.getLowCount())
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .build();
        log.info("保存风险分析结果 : {}", riskResult);
        riskResultMapper.insert(riskResult);

        if (result.getRisks() != null) {
            for (RiskDetailInfo riskInfo : result.getRisks()) {
                Long sectionId = findSectionId(sections, riskInfo.getOriginalText(), documentId);
                RiskDetail riskDetail = RiskDetail.builder()
                        .riskResultId(riskResult.getId())
                        .sectionId(sectionId)
                        .riskType(riskInfo.getRiskType())
                        .riskLevel(riskInfo.getRiskLevel())
                        .title(riskInfo.getTitle())
                        .originalText(riskInfo.getOriginalText())
                        .reason(riskInfo.getReason())
                        .impact(riskInfo.getImpact())
                        .suggestion(riskInfo.getSuggestion())
                        .createdTime(LocalDateTime.now())
                        .updatedTime(LocalDateTime.now())
                        .build();
                riskDetailMapper.insert(riskDetail);
            }
        }
    }

    /**
     * 查找章节ID
     */
    private Long findSectionId(List<ParsedSection> sections, String originalText, Long documentId) {
        for (ParsedSection section : sections) {
            if (section.getContent().contains(originalText)) {
                var entity = documentSectionMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentSection>()
                                .eq(DocumentSection::getDocumentId, documentId)
                                .eq(DocumentSection::getSectionNo, section.getSectionNo()));
                if (entity != null) {
                    return entity.getId();
                }
            }
        }
        return null;
    }

    private void updateDocumentSummary(Long documentId, String summary) {
        documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, documentId)
                .set(Document::getSummary, summary)
                .set(Document::getStatus, TaskStatus.SUCCESS.getCode())
                .set(Document::getUpdatedTime, LocalDateTime.now()));
    }

    private void markFinished(Long taskId, TaskStatus status, String errorMessage) {
        analysisTaskMapper.update(null, new LambdaUpdateWrapper<AnalysisTask>()
                .eq(AnalysisTask::getId, taskId)
                .set(AnalysisTask::getStatus, status.getCode())
                .set(AnalysisTask::getErrorMessage, errorMessage)
                .set(AnalysisTask::getFinishedTime, LocalDateTime.now()));
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
