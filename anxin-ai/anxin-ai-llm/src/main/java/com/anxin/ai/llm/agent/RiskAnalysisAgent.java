package com.anxin.ai.llm.agent;

import com.anxin.ai.llm.model.RiskAnalysisResult;
import com.anxin.document.parser.model.ParsedSection;

import java.util.List;

public interface RiskAnalysisAgent {

    RiskAnalysisResult analyze(List<ParsedSection> sections);
}