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
import com.anxin.ocr.service.OcrService;
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
import java.util.Collections;
import com.anxin.ocr.OcrException;
import com.anxin.parser.SectionSplitter;

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

    @Resource
    private OcrService ocrService;

    public void analysis(AnalysisTaskMessage analysisTaskMessage) {
        byte[] bytes = ossStorageService.download(analysisTaskMessage.getFileUrl());
        boolean image = "IMAGE".equals(analysisTaskMessage.getFileType());
        //重试幂等，清掉上次执行可能残留的半截数据（图片与文档分支都要清）
        clearExisting(analysisTaskMessage);

        //取条款：文档走 Tika 解析，图片走 OCR 后复用同一套条款切分逻辑
        List<ParsedSection> sections = image
                ? splitOcrText(analysisTaskMessage, bytes)
                : documentParser.parse(new ByteArrayInputStream(bytes), analysisTaskMessage.getFileType());

        if (sections.isEmpty()) {
            //解析不出内容，重试无意义，直接终止并提示用户
            throw new NonRetryableTaskException(image
                    ? "未识别到有效条款内容，请上传清晰的合同图片"
                    : "未解析到有效条款内容，请重新上传");
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

    /**
     * 图片分支：OCR 识别全文后，复用文档同一套条款切分逻辑，
     * 使图片与 PDF/Word 走完全一致的下游链路（拆节落库 → LLM 分析 → 风险落库）。
     */
    private List<ParsedSection> splitOcrText(AnalysisTaskMessage analysisTaskMessage, byte[] bytes) {
        try {
            //mime 当前未参与 OCR 推理，仅作接口语义占位
            String fullText = ocrService.recognize(bytes, "image/jpeg");
            if (fullText == null) {
                //识别服务返回 null 时按未识别到内容处理，避免下方取 length 时空指针
                throw new NonRetryableTaskException("未识别到有效条款内容，请上传清晰的合同图片");
            }
            log.info("图片 OCR 完成 documentId : {}, 文本长度 : {}",
                    analysisTaskMessage.getDocumentId(), fullText.length());
            return SectionSplitter.splitByClause(fullText);
        } catch (OcrException e) {
            //图片模糊、无文字属业务性失败，重试无意义
            throw new NonRetryableTaskException("图片识别失败：" + e.getMessage());
        }
    }
}
