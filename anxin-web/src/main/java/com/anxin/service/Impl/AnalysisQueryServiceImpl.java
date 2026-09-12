package com.anxin.service.impl;

import com.anxin.entity.AnalysisTask;
import com.anxin.entity.Document;
import com.anxin.entity.RiskDetail;
import com.anxin.entity.RiskResult;
import com.anxin.enums.ResultCode;
import com.anxin.enums.TaskStatus;
import com.anxin.exception.ServiceException;
import com.anxin.mapper.AnalysisTaskMapper;
import com.anxin.mapper.DocumentMapper;
import com.anxin.mapper.RiskDetailMapper;
import com.anxin.mapper.RiskResultMapper;
import com.anxin.service.IAnalysisQueryService;
import com.anxin.service.support.RiskLevelCalculator;
import com.anxin.threadlocal.BaseContext;
import com.anxin.vo.AnalysisTaskVO;
import com.anxin.vo.RiskDetailVO;
import com.anxin.vo.RiskReportVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;


@Slf4j
@Service
public class AnalysisQueryServiceImpl implements IAnalysisQueryService {

    private static final String TASK_TYPE_RISK_ANALYSIS = "RISK_ANALYSIS";

    @Resource
    private AnalysisTaskMapper analysisTaskMapper;

    @Resource
    private RiskResultMapper riskResultMapper;

    @Resource
    private RiskDetailMapper riskDetailMapper;

    @Resource
    private DocumentMapper documentMapper;

    @Override
    public AnalysisTaskVO getAnalysisTask(Long taskId) {
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (Objects.isNull(task)) {
            throw new ServiceException(ResultCode.ANALYSIS_TASK_NOT_FOUND);
        }
        return AnalysisTaskVO.builder()
                .taskId(String.valueOf(task.getId()))
                .documentId(String.valueOf(task.getDocumentId()))
                .taskType(task.getTaskType())
                .status(TaskStatus.fromCode(task.getStatus()).name())
                .retryCount(task.getRetryCount())
                .errorMessage(task.getErrorMessage())
                .startedTime(task.getStartedTime())
                .finishedTime(task.getFinishedTime())
                .build();
    }

    @Override
    public RiskReportVO getRiskReport(Long documentId) {
        Document document = documentMapper.selectById(documentId);
        if (Objects.isNull(document) || !document.getUserId().equals(BaseContext.getCurrentId())) {
            throw new ServiceException(ResultCode.DOCUMENT_NOT_EXIST);
        }
        AnalysisTask task = analysisTaskMapper.selectOne(new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getDocumentId, documentId)
                .eq(AnalysisTask::getTaskType, TASK_TYPE_RISK_ANALYSIS)
                .orderByDesc(AnalysisTask::getId)
                .last("LIMIT 1"));
        if (Objects.isNull(task)) {
            throw new ServiceException(ResultCode.ANALYSIS_TASK_NOT_FOUND);
        }
        if (TaskStatus.SUCCESS.getCode() != task.getStatus()) {
            throw new ServiceException(ResultCode.ANALYSIS_NOT_COMPLETED);
        }
        RiskResult riskResult = riskResultMapper.selectOne(new LambdaQueryWrapper<RiskResult>()
                .eq(RiskResult::getTaskId, task.getId())
                .orderByDesc(RiskResult::getId)
                .last("LIMIT 1"));
        if (Objects.isNull(riskResult)) {
            throw new ServiceException(ResultCode.ANALYSIS_RESULT_MISSING);
        }
        List<RiskDetail> details = riskDetailMapper.selectList(new LambdaQueryWrapper<RiskDetail>()
                .eq(RiskDetail::getRiskResultId, riskResult.getId())
                .orderByAsc(RiskDetail::getId));
        List<RiskDetailVO> risks = details.stream()
                .map(d -> RiskDetailVO.builder()
                        .id(String.valueOf(d.getId()))
                        .sectionId(String.valueOf(d.getSectionId()))
                        .riskType(d.getRiskType())
                        .riskLevel(d.getRiskLevel())
                        .title(d.getTitle())
                        .originalText(d.getOriginalText())
                        .reason(d.getReason())
                        .impact(d.getImpact())
                        .suggestion(d.getSuggestion())
                        .build()).toList();
        return RiskReportVO.builder()
                .documentId(String.valueOf(documentId))
                .taskId(String.valueOf(task.getId()))
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .startedTime(task.getStartedTime())
                .finishedTime(task.getFinishedTime())
                .riskSummary(riskResult.getRiskSummary())
                //整体风险等级由各级数量推导，数量本身不返回前端
                .riskLevel(RiskLevelCalculator.derive(
                        riskResult.getHighCount(), riskResult.getMediumCount(), riskResult.getLowCount()))
                .riskCount(risks.size())
                .risks(risks)
                .build();
    }
}
