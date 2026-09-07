package com.anxin.ai.analysis;

import com.anxin.ai.model.RiskAnalysisResult;
import com.anxin.parser.model.ParsedSection;

import java.util.List;

public interface RiskAnalyzer {

    RiskAnalysisResult analyze(List<ParsedSection> sections);
}
