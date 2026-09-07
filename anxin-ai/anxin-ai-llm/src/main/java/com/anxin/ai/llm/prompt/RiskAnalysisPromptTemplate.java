package com.anxin.ai.llm.prompt;

import com.anxin.ai.llm.model.RiskAnalysisResult;
import com.anxin.ai.llm.model.RiskDetailInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 风险分析提示词模板
 */
@Slf4j
public class RiskAnalysisPromptTemplate {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            你是一个专业的法律文档风险分析助手。请分析以下合同条款，识别潜在风险。
            
            要求：
            1. 逐条分析每个条款
            2. 识别风险类型（如：违约责任、免责条款、知识产权、争议解决等）
            3. 评估风险等级（HIGH/MEDIUM/LOW）
            4. 给出风险原因、影响和建议
            
            请按以下JSON格式返回分析结果：
            {
              "riskSummary": "整体风险概述",
              "highCount": 0,
              "mediumCount": 0,
              "lowCount": 0,
              "risks": [
                {
                  "riskType": "风险类型",
                  "riskLevel": "HIGH/MEDIUM/LOW",
                  "title": "风险标题",
                  "originalText": "原文内容",
                  "reason": "风险原因",
                  "impact": "潜在影响",
                  "suggestion": "建议"
                }
              ]
            }
            
            只返回JSON，不要包含其他内容。
            """;

    public static String buildPrompt(String documentText) {
        return SYSTEM_PROMPT + "\n\n---\n\n待分析的合同条款：\n\n" + documentText;
    }

    /**
     * 解析LLM返回的JSON响应
     */
    public static RiskAnalysisResult parseResponse(String response) {
        try {
            String json = extractJson(response);
            JsonNode root = OBJECT_MAPPER.readTree(json);

            List<RiskDetailInfo> risks = new ArrayList<>();
            JsonNode risksNode = root.get("risks");
            if (risksNode != null && risksNode.isArray()) {
                for (JsonNode riskNode : risksNode) {
                    risks.add(RiskDetailInfo.builder()
                            .riskType(riskNode.get("riskType").asText())
                            .riskLevel(riskNode.get("riskLevel").asText())
                            .title(riskNode.get("title").asText())
                            .originalText(riskNode.get("originalText").asText())
                            .reason(riskNode.get("reason").asText())
                            .impact(riskNode.get("impact").asText())
                            .suggestion(riskNode.get("suggestion").asText())
                            .build());
                }
            }

            return RiskAnalysisResult.builder()
                    .riskSummary(root.get("riskSummary").asText())
                    .highCount(root.get("highCount").asInt())
                    .mediumCount(root.get("mediumCount").asInt())
                    .lowCount(root.get("lowCount").asInt())
                    .risks(risks)
                    .build();
        } catch (Exception e) {
            log.error("解析LLM响应失败: {}", response, e);
            return RiskAnalysisResult.builder()
                    .riskSummary("解析失败")
                    .highCount(0)
                    .mediumCount(0)
                    .lowCount(0)
                    .risks(List.of())
                    .build();
        }
    }


    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
