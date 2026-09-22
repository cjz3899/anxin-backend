package com.anxin.task;

import com.anxin.constant.TaskConstant;
import com.anxin.entity.AnalysisTask;
import com.anxin.entity.Document;
import com.anxin.enums.TaskStatus;
import com.anxin.exception.NonRetryableTaskException;
import com.anxin.mapper.AnalysisTaskMapper;
import com.anxin.mapper.DocumentMapper;
import com.anxin.service.support.DocumentAnalysisService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 分析任务消费者（状态机核心，无论任务由本机线程池还是补偿扫描带进来，都走同一处理逻辑）
 * 幂等策略：条件更新抢占 PENDING → PROCESSING，抢占失败说明已被其它线程/实例处理，直接返回；
 * 状态只允许 PENDING → PROCESSING → SUCCESS/FAILED 单向流转，重试时置回 PENDING
 */
@Slf4j
@Service
public class AnalysisTaskConsumer {

    @Resource
    private AnalysisTaskMapper analysisTaskMapper;

    @Resource
    private DocumentAnalysisService documentAnalysisService;

    @Resource
    private DocumentMapper documentMapper;

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
            documentAnalysisService.analysis(message);
            markFinished(message.getTaskId(), TaskStatus.SUCCESS, null);
        } catch (NonRetryableTaskException e) {
            log.warn("任务不可重试 taskId : {}，reason : {}", message.getTaskId(), e.getMessage());
            markFinished(message.getTaskId(), TaskStatus.FAILED, e.getMessage());
            markDocumentFailed(message.getDocumentId());
        } catch (Exception e) {
            log.error("任务处理失败 taskId : {}", message.getTaskId(), e);
            AnalysisTask task = analysisTaskMapper.selectById(message.getTaskId());
            int retry = (task == null ? 0 : task.getRetryCount()) + 1;
            if (retry > TaskConstant.MAX_RETRY) {
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                markFinished(message.getTaskId(), TaskStatus.FAILED, truncate(reason, 2000));
                markDocumentFailed(message.getDocumentId());
            } else {
                analysisTaskMapper.update(null, new LambdaUpdateWrapper<AnalysisTask>()
                        .eq(AnalysisTask::getId, message.getTaskId())
                        .set(AnalysisTask::getStatus, TaskStatus.PENDING.getCode())
                        .set(AnalysisTask::getRetryCount, retry));
                log.warn("任务将重试 taskId : {}, retry : {}", message.getTaskId(), retry);
            }
        }
    }

    private void markDocumentFailed(Long documentId) {
        documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, documentId)
                .set(Document::getStatus, TaskStatus.FAILED.getCode())
                .set(Document::getUpdatedTime, LocalDateTime.now()));
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
