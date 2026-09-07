package com.anxin.ai.prompt;

import com.anxin.ai.model.RiskType;
import com.anxin.parser.model.ParsedSection;

import java.util.List;

public class RiskAnalysisPromptTemplate {

    private RiskAnalysisPromptTemplate() {
    }

    public static String buildSystemPrompt() {
        return "你是一个专业的法律文档风险分析助手。你的任务是分析用户提供的合同或法律文档条款，"
                + "识别潜在风险，并按指定的 JSON 格式返回分析结果。\n\n"
                + "分析要求：\n"
                + "1. 仔细阅读每个条款，识别可能对用户不利的条款\n"
                + "2. 风险类型必须从下列取值中选择一个，输出该中文词本身：" + RiskType.candidateLabels() + "\n"
                + "3. 风险等级判定口径（riskLevel 字段只能输出 HIGH/MEDIUM/LOW）：\n"
                + "   - HIGH：可能导致重大经济损失或法律纠纷的条款\n"
                + "   - MEDIUM：可能造成一定损失但可通过协商修改的条款\n"
                + "   - LOW：需要注意但影响较小的条款\n"
                + "4. 每个风险必须包含：sectionNo、riskType、riskLevel、title、originalText、reason、impact、suggestion\n"
                + "5. sectionNo 必须取用户输入条款中出现的章节编号，便于原文溯源\n"
                + "6. originalText 必须忠实引用原文条款，不得改写或概括\n"
                + "7. 回答必须使用中文（riskLevel 除外）\n"
                + "8. 只返回合法的 JSON，不要包含任何其他文本或解释\n"
                + "9. 若文档无明显风险，risks 返回空数组，不要编造风险";
    }

    public static String buildUserPrompt(List<ParsedSection> sections) {
        StringBuilder sb = new StringBuilder("请分析以下文档条款，识别潜在风险：\n\n");
        for (ParsedSection section : sections) {
            sb.append("【").append(section.getSectionNo()).append("】");
            if (section.getTitle() != null && !section.getTitle().isEmpty()) {
                sb.append(" ").append(section.getTitle());
            }
            sb.append("\n").append(section.getContent()).append("\n\n");
        }
        sb.append("请按以下 JSON 格式返回分析结果：\n")
                .append("{\n")
                .append("  \"riskSummary\": \"整体风险概述，一段话总结主要风险并给出总体处理建议\",\n")
                .append("  \"risks\": [\n")
                .append("    {\n")
                .append("      \"sectionNo\": \"风险对应条款的章节编号\",\n")
                .append("      \"riskType\": \"风险类型（必须是给定取值之一）\",\n")
                .append("      \"riskLevel\": \"HIGH/MEDIUM/LOW\",\n")
                .append("      \"title\": \"风险标题\",\n")
                .append("      \"originalText\": \"风险对应的原文条款（忠实引用）\",\n")
                .append("      \"reason\": \"风险原因分析\",\n")
                .append("      \"impact\": \"潜在影响\",\n")
                .append("      \"suggestion\": \"修改或处理建议\"\n")
                .append("    }\n")
                .append("  ]\n")
                .append("}");
        return sb.toString();
    }
}
