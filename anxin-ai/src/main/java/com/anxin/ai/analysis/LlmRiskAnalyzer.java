package com.anxin.ai.analysis;

import com.anxin.ai.model.RiskAnalysisResult;
import com.anxin.ai.model.RiskDetailInfo;
import com.anxin.ai.prompt.RiskAnalysisPromptTemplate;
import com.anxin.parser.model.ParsedSection;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class LlmRiskAnalyzer implements RiskAnalyzer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;

    public LlmRiskAnalyzer(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public RiskAnalysisResult analyze(List<ParsedSection> sections) {
        try {
            String response = chatClient.prompt()
                    .system(RiskAnalysisPromptTemplate.buildSystemPrompt())
                    .user(RiskAnalysisPromptTemplate.buildUserPrompt(sections))
                    .call()
                    .content();
            RiskAnalysisResult result = OBJECT_MAPPER.readValue(extractJson(response), RiskAnalysisResult.class);
            validate(result);
            fillPageNo(result, sections);
            return result;
        } catch (Exception e) {
            log.error("LLM 风险分析失败", e);
            throw new AnalysisException("LLM 风险分析失败: " + e.getMessage(), e);
        }
    }

    private void validate(RiskAnalysisResult result) {
        if (result.getRisks() == null) {
            result.setRisks(new ArrayList<>());
        }
        if (result.getRiskSummary() == null || result.getRiskSummary().isBlank()) {
            result.setRiskSummary("已识别 " + result.getRisks().size() + " 处风险，具体风险见下方明细");
        }
        for (RiskDetailInfo risk : result.getRisks()) {
            if (risk.getRiskLevel() == null) {
                throw new IllegalArgumentException("风险缺少 riskLevel: " + risk.getTitle());
            }
            if (risk.getSectionNo() == null || risk.getSectionNo().isBlank()) {
                throw new IllegalArgumentException("风险缺少 sectionNo: " + risk.getTitle());
            }
        }
    }

    private void fillPageNo(RiskAnalysisResult result, List<ParsedSection> sections) {
        Map<String, Integer> pageBySectionNo = new HashMap<>();
        for (ParsedSection section : sections) {
            if (section.getSectionNo() != null) {
                pageBySectionNo.putIfAbsent(section.getSectionNo(), section.getPageNo());
            }
        }
        for (RiskDetailInfo risk : result.getRisks()) {
            if (risk.getPageNo() == null) {
                risk.setPageNo(pageBySectionNo.get(risk.getSectionNo()));
            }
        }
    }

    private String extractJson(String response) {
        if (response == null || response.isBlank()) {
            return "{}";
        }
        String cleaned = response
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }
}
