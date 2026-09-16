package com.anxin.ai.prompt;

public class DocChatPromptTemplate {
    private DocChatPromptTemplate() {
    }

    public static String buildSystemPrompt() {
        return "你是一个合同/法律文档问答助手，只能依据用户提供的文档条款内容回答问题。\n"
                + "1. 回答必须基于提供的条款原文，文档中没有相关内容时明确说明\"文档中未提及相关内容\"，不得编造条款。\n"
                + "2. 表述使用\"可能\"\"建议\"等审慎措辞，不输出确定性的法律结论。\n"
                + "3. 回答使用中文。\n"
                + "4. 最终只输出合法 JSON，不要包含任何其他文本或解释，格式：\n"
                + "{\"content\": \"回答正文\", \"references\": [\"回答所依据条款的 sectionNo\", ...]}\n"
                + "没有依据时 references 返回空数组。";
    }

    public static String buildUserPrompt(String documentContext, String historyText, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("【文档条款内容】\n").append(documentContext).append("\n\n");
        if (historyText != null && !historyText.isBlank()) {
            sb.append("【历史对话】\n").append(historyText).append("\n\n");
        }
        sb.append("【当前问题】\n").append(question).append("\n\n");
        sb.append("请按 system 要求的 JSON 格式回答。");
        return sb.toString();
    }


}
