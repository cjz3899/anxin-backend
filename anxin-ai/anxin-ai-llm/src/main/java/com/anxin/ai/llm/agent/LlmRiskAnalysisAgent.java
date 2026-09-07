package com.anxin.ai.llm.agent;

import com.anxin.ai.llm.model.RiskAnalysisResult;
import com.anxin.ai.llm.prompt.RiskAnalysisPromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;


/**
 * 风险分析实现类
 * 调用大模型进行分析
 */
@Slf4j
@Component
public class LlmRiskAnalysisAgent implements RiskAnalysisAgent {

    // 构造器注入 ChatClient 实例
    private final ChatClient chatClient;

    public LlmRiskAnalysisAgent(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public RiskAnalysisResult analyze(String documentText) {
        String prompt = RiskAnalysisPromptTemplate.buildPrompt(documentText);
        try {
            log.info("调用大模型分析");
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            return RiskAnalysisPromptTemplate.parseResponse(response);
        } catch (Exception e) {
            log.error("LLM风险分析调用失败", e);
            return RiskAnalysisResult.builder()
                    .riskSummary("分析失败")
                    .highCount(0)
                    .mediumCount(0)
                    .lowCount(0)
                    .risks(java.util.List.of())
                    .build();
        }
    }
}
