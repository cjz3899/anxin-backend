package com.anxin.controller;

import com.anxin.result.Result;
import com.anxin.service.IAnalysisQueryService;
import com.anxin.vo.AnalysisTaskVO;
import com.anxin.vo.RiskReportVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/analysis")
public class AnalysisTaskController {
    @Resource
    private IAnalysisQueryService analysisQueryService;

    /**
     * 客户端轮询任务状态（间隔2s，检测到SUCCESS/FAILED状态后停止轮询）
     */
    @GetMapping("/task/{taskId}")
    public Result<AnalysisTaskVO> getAnalysisTaskStatus(@PathVariable Long taskId) {
        return Result.success(analysisQueryService.getAnalysisTask(taskId));
    }

    /**
     * 查询风险报告（分析任务状态置为SUCCESS后调用）
     */
    @GetMapping("/report/{documentId}")
    public Result<RiskReportVO> getRiskReport(@PathVariable Long documentId) {
        return Result.success(analysisQueryService.getRiskReport(documentId));
    }
}
