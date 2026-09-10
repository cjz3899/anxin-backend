package com.anxin.service;

import com.anxin.vo.AnalysisTaskVO;
import com.anxin.vo.RiskReportVO;

public interface IAnalysisQueryService {

    AnalysisTaskVO getAnalysisTask(Long taskId);

    RiskReportVO getRiskReport(Long documentId);
}
