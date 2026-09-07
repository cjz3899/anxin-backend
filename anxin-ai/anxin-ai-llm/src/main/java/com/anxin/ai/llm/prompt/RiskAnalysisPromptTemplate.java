package com.anxin.ai.llm.prompt;

import com.anxin.parser.model.ParsedSection;

import java.util.List;

public class RiskAnalysisPromptTemplate {

    private RiskAnalysisPromptTemplate() {
    }

    public static String buildSystemPrompt() {
        return "你是一个专业的法律文档风险分析助手。你的任务是分析用户提供的合同或法律文档条款，"
                + "识别潜在风险，并按指定的 JSON 格式返回分析结果。\n\n"
                + "分析要求：\n"
                + "1. 仔细阅读每个条款，识别可能对某一方不利的条款\n"
                + "2. 风险类型包括：违约责任、免责条款、争议解决、管辖权、知识产权、保密义务、赔偿责任、合同解除、其他\n"
                + "3. 风险等级标准：\n"
                + "   - HIGH：可能导致重大经济损失或法律纠纷的条款\n"
                + "   - MEDIUM：可能造成一定损失但可通过协商修改的条款\n"
                + "   - LOW：需要注意但影响较小的条款\n"
                + "4. 每个风险必须包含：风险类型、等级、标题、原文、原因、影响、建议\n"
                + "5. 回答必须使用中文\n"
                + "6. 必须返回合法的 JSON 格式，不要包含其他文本";
    }

    public static String buildUserPrompt(List<ParsedSection> sections) {
        StringBuilder sb = new StringBuilder("请分析以下法律文档，识别潜在风险：\n\n");
        for (ParsedSection section : sections) {
            sb.append("【").append(section.getSectionNo()).append("】");
            if (section.getTitle() != null && !section.getTitle().isEmpty()) {
                sb.append(" ").append(section.getTitle());
            }
            sb.append("\n").append(section.getContent()).append("\n\n");
        }
        sb.append("请按以下 JSON 格式返回分析结果：\n")
                .append("{\n")
                .append("  \"riskSummary\": \"整体风险摘要，一段话总结主要风险\",\n")
                .append("  \"risks\": [\n")
                .append("    {\n")
                .append("      \"sectionNo\": \"章节编号\",\n")
                .append("      \"riskType\": \"风险类型\",\n")
                .append("      \"riskLevel\": \"HIGH/MEDIUM/LOW\",\n")
                .append("      \"title\": \"风险标题\",\n")
                .append("      \"originalText\": \"风险对应原文\",\n")
                .append("      \"reason\": \"风险原因分析\",\n")
                .append("      \"impact\": \"潜在影响\",\n")
                .append("      \"suggestion\": \"处理建议\"\n")
                .append("    }\n")
                .append("  ]\n")
                .append("}");
        return sb.toString();
    }
}