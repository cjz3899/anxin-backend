package com.anxin.service;

import com.anxin.entity.AnalysisTask;
import com.anxin.vo.AnalysisTaskVO;
import com.anxin.vo.RiskDetailVO;
import com.anxin.vo.RiskReportVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IAnalysisQueryService extends IService<AnalysisTask> {

    AnalysisTaskVO getAnalysisTask(Long taskId);

    RiskReportVO getRiskReport(Long documentId);

    RiskDetailVO getRiskDetail(String documentId, String riskId);
}
