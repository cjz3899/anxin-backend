package com.anxin.ai.llm.agent;

import com.anxin.ai.llm.model.RiskAnalysisResult;

/**
 * 风险分析接口
 */
public interface RiskAnalysisAgent {

    RiskAnalysisResult analyze(String documentText);
}
