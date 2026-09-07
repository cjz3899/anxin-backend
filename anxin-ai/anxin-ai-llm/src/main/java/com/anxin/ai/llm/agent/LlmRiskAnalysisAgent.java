package com.anxin.ai.llm.agent;

import com.anxin.ai.llm.model.RiskAnalysisResult;
import com.anxin.ai.llm.prompt.RiskAnalysisPromptTemplate;
import com.anxin.parser.model.ParsedSection;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class LlmRiskAnalysisAgent implements RiskAnalysisAgent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;

    public LlmRiskAnalysisAgent(ChatClient.Builder chatClientBuilder) {
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
            String json = extractJson(response);
            RiskAnalysisResult result = OBJECT_MAPPER.readValue(json, RiskAnalysisResult.class);
            if (result.getRisks() == null) {
                result.setRisks(List.of());
            }
            return result;
        } catch (Exception e) {
            log.warn("LLM 风险分析失败，返回兜底结果: {}", e.getMessage());
            return RiskAnalysisResult.builder()
                    .riskSummary("分析服务暂时不可用，请稍后重试")
                    .risks(List.of())
                    .build();
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