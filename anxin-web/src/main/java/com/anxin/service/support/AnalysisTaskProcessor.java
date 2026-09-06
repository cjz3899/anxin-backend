package com.anxin.service.support;

import com.anxin.dto.AnalysisTaskMessage;
import com.anxin.entity.AnalysisTask;
import com.anxin.enums.TaskStatus;
import com.anxin.mapper.AnalysisTaskMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 异步任务处理器（状态机核心）。
 * <p>
 * 幂等策略：条件更新抢占 PENDING → PROCESSING，抢占失败说明已被其它线程/实例处理，直接返回；
 * 状态只允许 PENDING → PROCESSING → SUCCESS/FAILED 单向流转，重试时置回 PENDING。
 */
@Slf4j
@Service
public class AnalysisTaskProcessor {

    private static final int MAX_RETRY = 3;

    @Resource
    private AnalysisTaskMapper analysisTaskMapper;

    public void process(AnalysisTaskMessage message) {
        int rows = analysisTaskMapper.update(null, new LambdaUpdateWrapper<AnalysisTask>()
                .eq(AnalysisTask::getId, message.getTaskId())
                .eq(AnalysisTask::getStatus, TaskStatus.PENDING.getCode())
                .set(AnalysisTask::getStatus, TaskStatus.PROCESSING.getCode())
                .set(AnalysisTask::getStartedTime, LocalDateTime.now()));
        if (rows == 0) {
            log.warn("任务已被处理或不存在，跳过 taskId : {}", message.getTaskId());
            return;
        }
        try {
            // TODO 异步审核与分析骨架：
            // 1) 按 message.getFileUrl() 从 OSS 拉取文件内容；
            // 2) 文档/超4MB图片提交微信异步审核（wxSecurityService.checkMediaAsync，当前 mock 放行）；
            // 3) document-parser 解析拆 document_section → AI 分析写 risk_result。
            // 练习阶段直接置成功，客户端按任务状态轮询。
            markFinished(message.getTaskId(), TaskStatus.SUCCESS, null);
        } catch (Exception e) {
            log.error("任务处理失败 taskId : {}", message.getTaskId(), e);
            AnalysisTask task = analysisTaskMapper.selectById(message.getTaskId());
            int retry = (task == null ? 0 : task.getRetryCount()) + 1;
            if (retry > MAX_RETRY) {
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                markFinished(message.getTaskId(), TaskStatus.FAILED, truncate(reason, 2000));
            } else {
                analysisTaskMapper.update(null, new LambdaUpdateWrapper<AnalysisTask>()
                        .eq(AnalysisTask::getId, message.getTaskId())
                        .set(AnalysisTask::getStatus, TaskStatus.PENDING.getCode())
                        .set(AnalysisTask::getRetryCount, retry));
                log.warn("任务将重试 taskId : {}, retry : {}", message.getTaskId(), retry);
            }
        }
    }

    private void markFinished(Long taskId, TaskStatus status, String errorMessage) {
        analysisTaskMapper.update(null, new LambdaUpdateWrapper<AnalysisTask>()
                .eq(AnalysisTask::getId, taskId)
                .set(AnalysisTask::getStatus, status.getCode())
                .set(AnalysisTask::getErrorMessage, errorMessage)
                .set(AnalysisTask::getFinishedTime, LocalDateTime.now()));
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
