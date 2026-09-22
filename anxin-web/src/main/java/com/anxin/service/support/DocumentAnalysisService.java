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
import com.anxin.parser.model.ParsedSection;
import com.anxin.service.support.extract.SectionExtractor;
import com.anxin.service.support.extract.SectionExtractorRegistry;
import com.anxin.task.AnalysisTaskMessage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private SectionExtractorRegistry sectionExtractorRegistry;

    @Resource
    private RiskAnalyzer riskAnalyzer;

    public void analysis(AnalysisTaskMessage analysisTaskMessage) {
        byte[] bytes = ossStorageService.download(analysisTaskMessage.getFileUrl());

        //重试幂等，清掉上次执行可能残留的半截数据
        clearExisting(analysisTaskMessage);

        //按文件类型选策略：文档走 Tika 解析、图片走 OCR，之后的下游链路完全共用
        SectionExtractor extractor = sectionExtractorRegistry.get(analysisTaskMessage.getFileType());
        List<ParsedSection> sections = extractor.extract(
                analysisTaskMessage.getDocumentId(), analysisTaskMessage.getFileType(), bytes);
        if (sections.isEmpty()) {
            //兜底不变量：任何策略都应产出条款，空列表说明文本抽取无内容，重试无意义
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
        //按文件维度清理而非 taskId：reanalyze 会换新 taskId，按 taskId 删不掉旧任务的残留
        List<RiskResult> oldResults = riskResultMapper.selectList(new LambdaQueryWrapper<RiskResult>()
                .eq(RiskResult::getDocumentId, analysisTaskMessage.getDocumentId()));
        for (RiskResult old : oldResults) {
            riskDetailMapper.delete(new LambdaQueryWrapper<RiskDetail>()
                    .eq(RiskDetail::getRiskResultId, old.getId()));
        }
        riskResultMapper.delete(new LambdaQueryWrapper<RiskResult>()
                .eq(RiskResult::getDocumentId, analysisTaskMessage.getDocumentId()));
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
