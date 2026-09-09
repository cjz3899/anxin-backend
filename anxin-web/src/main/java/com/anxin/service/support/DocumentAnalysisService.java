package com.anxin.service.support;

import com.anxin.ai.analysis.RiskAnalyzer;
import com.anxin.ai.model.RiskAnalysisResult;
import com.anxin.ai.model.RiskDetailInfo;
import com.anxin.entity.Document;
import com.anxin.entity.DocumentSection;
import com.anxin.entity.RiskDetail;
import com.anxin.entity.RiskResult;
import com.anxin.enums.TaskStatus;
import com.anxin.exception.NonRetryableTaskException;
import com.anxin.mapper.DocumentMapper;
import com.anxin.mapper.DocumentSectionMapper;
import com.anxin.mapper.RiskDetailMapper;
import com.anxin.mapper.RiskResultMapper;
import com.anxin.parser.DocumentParser;
import com.anxin.parser.model.ParsedSection;
import com.anxin.rocketmq.message.AnalysisTaskMessage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档风险分析服务
 */
@Slf4j
@Service
public class DocumentAnalysisService {

    @Resource
    private OssStorageService ossStorageService;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private RiskResultMapper riskResultMapper;

    @Resource
    private RiskDetailMapper riskDetailMapper;

    @Resource
    private DocumentSectionMapper documentSectionMapper;

    @Resource
    private DocumentParser documentParser;

    @Resource
    private RiskAnalyzer riskAnalyzer;

    public void analysis(AnalysisTaskMessage analysisTaskMessage) {
        byte[] bytes = ossStorageService.download(analysisTaskMessage.getFileUrl());
        // TODO 引入OCR解析图片
        if ("IMAGE".equals(analysisTaskMessage.getFileType())) {
            markDocumentSuccess(analysisTaskMessage.getDocumentId());
            return;
        }

        //重试幂等，清掉上次执行可能残留的半截数据
        clearExisting(analysisTaskMessage);

        List<ParsedSection> sections = documentParser.parse(new ByteArrayInputStream(bytes), analysisTaskMessage.getFileType());
        if (sections.isEmpty()) {
            //文件解析不出来，重试无意义，直接终止并提示用户上传有效文件
            throw new NonRetryableTaskException("未解析到有效条款内容，请重新上传");
        }

        Map<String, Long> sectionIdByNo = saveSections(analysisTaskMessage.getDocumentId(), sections);

        RiskAnalysisResult result = riskAnalyzer.analyze(sections);
        saveRiskResult(analysisTaskMessage, result, sectionIdByNo);
        markDocumentSuccess(analysisTaskMessage.getDocumentId());
    }

    private void saveRiskResult(AnalysisTaskMessage analysisTaskMessage, RiskAnalysisResult result, Map<String, Long> sectionIdByNo) {
        int high = 0;
        int medium = 0;
        int low = 0;
        for (RiskDetailInfo risk : result.getRisks()) {
            switch (risk.getRiskLevel()) {
                case HIGH -> high++;
                case MEDIUM -> medium++;
                case LOW -> low++;
            }
        }
        LocalDateTime now = LocalDateTime.now();
        RiskResult riskResult = RiskResult.builder()
                .taskId(analysisTaskMessage.getTaskId())
                .documentId(analysisTaskMessage.getDocumentId())
                .riskSummary(result.getRiskSummary())
                .highCount(high)
                .mediumCount(medium)
                .lowCount(low)
                .createdTime(now)
                .updatedTime(now)
                .build();
        riskResultMapper.insert(riskResult);

        Long fallbackSectionId = sectionIdByNo.isEmpty() ? null : sectionIdByNo.values().iterator().next();
        for (RiskDetailInfo risk : result.getRisks()) {
            Long sectionId = sectionIdByNo.get(risk.getSectionNo());
            if (sectionId == null) {
                //LLM 返回的 sectionNo 与解析章节对不上时回退第一条，满足 section_id NOT NULL 约束
                log.warn("风险 sectionNo 无法匹配解析章节，回退第一条 sectionNo : {}, title : {}",
                        risk.getSectionNo(), risk.getTitle());
                sectionId = fallbackSectionId;
            }
            if (sectionId == null) {
                continue;
            }
            riskDetailMapper.insert(RiskDetail.builder()
                    .riskResultId(riskResult.getId())
                    .sectionId(sectionId)
                    .riskType(risk.getRiskType())
                    .riskLevel(risk.getRiskLevel().name())
                    .title(risk.getTitle() == null ? "未命名风险" : risk.getTitle())
                    .originalText(risk.getOriginalText() == null ? "" : risk.getOriginalText())
                    .reason(risk.getReason())
                    .impact(risk.getImpact())
                    .suggestion(risk.getSuggestion())
                    .createdTime(now)
                    .updatedTime(now)
                    .build());
        }
    }

    /**
     * 解析结果逐条落库并回填id，返回 sectionNo->document_section.id 映射供风险明细溯源
     */
    private Map<String, Long> saveSections(Long documentId, List<ParsedSection> sections) {
        Map<String, Long> sectionIdByNo = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (ParsedSection section : sections) {
            DocumentSection ds = DocumentSection.builder().
                    documentId(documentId)
                    .sectionNo(section.getSectionNo())
                    .title(section.getTitle())
                    .content(section.getContent())
                    .pageNo(section.getPageNo())
                    .sort(section.getSortOrder())
                    .createdTime(now)
                    .updatedTime(now)
                    .build();
            documentSectionMapper.insert(ds);
            if (section.getSectionNo() != null) {
                sectionIdByNo.putIfAbsent(section.getSectionNo(), ds.getId());
            }
        }
        return sectionIdByNo;
    }

    private void clearExisting(AnalysisTaskMessage analysisTaskMessage) {
        List<RiskResult> oldResults = riskResultMapper.selectList(new LambdaQueryWrapper<RiskResult>()
                .eq(RiskResult::getTaskId, analysisTaskMessage.getTaskId()));
        for (RiskResult old : oldResults) {
            riskDetailMapper.delete(new LambdaQueryWrapper<RiskDetail>()
                    .eq(RiskDetail::getRiskResultId, old.getId()));
        }
        riskResultMapper.delete(new LambdaQueryWrapper<RiskResult>()
                .eq(RiskResult::getTaskId, analysisTaskMessage.getTaskId()));
        documentSectionMapper.delete(new LambdaQueryWrapper<DocumentSection>()
                .eq(DocumentSection::getDocumentId, analysisTaskMessage.getDocumentId()));
    }

    private void markDocumentSuccess(Long documentId) {
        documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, documentId)
                .set(Document::getStatus, TaskStatus.SUCCESS.getCode())
                .set(Document::getUpdatedTime, LocalDateTime.now()));
    }
}
