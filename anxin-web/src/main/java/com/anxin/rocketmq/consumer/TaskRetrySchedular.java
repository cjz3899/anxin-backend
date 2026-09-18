package com.anxin.rocketmq.consumer;

import com.anxin.constant.TaskConstant;
import com.anxin.entity.AnalysisTask;
import com.anxin.entity.Document;
import com.anxin.enums.TaskStatus;
import com.anxin.mapper.AnalysisTaskMapper;
import com.anxin.mapper.DocumentMapper;
import com.anxin.rocketmq.message.AnalysisTaskMessage;
import com.anxin.rocketmq.producer.TaskProducer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 任务补偿调度器
 * 一、回收僵死的 PROCESSING：消费端抢占后若写回终态时再次失败，任务会永远停在 PROCESSING，按超时兜底；
 * 二、重投 PENDING：消费失败置回 PENDING 后消息已消费完毕，需要定时重投
 */
@Slf4j
@Component
public class TaskRetrySchedular {

    /**
     * PROCESSING 超过该分钟数仍未收尾，视为消费者异常退出或状态写回失败。
     * 必须远大于正常分析耗时，否则会误伤正在处理中的任务，造成重复消费
     */
    private static final long PROCESSING_TIMEOUT_MINUTES = 10;

    /**
     * 单轮扫描上限：积压时分批处理，剩下的下一轮继续
     */
    private static final int BATCH_SIZE = 200;

    @Resource
    private AnalysisTaskMapper analysisTaskMapper;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private TaskProducer taskProducer;

    // TODO 之后可改成DLQ死信队列补偿机制
    @Scheduled(fixedDelay = 30000)
    public void retryPendingTasks() {
        try {
            recoverStaleProcessing();
        } catch (Exception e) {
            //回收失败不能拖累重投
            log.error("回收超时任务失败", e);
        }
        retryPending();
    }

    /**
     * 回收僵死的 PROCESSING：置回 PENDING 并计入重试次数，交给下一轮扫描重投；次数用尽则判失败
     */
    void recoverStaleProcessing() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(PROCESSING_TIMEOUT_MINUTES);
        List<AnalysisTask> staleTasks = analysisTaskMapper.selectList(new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getStatus, TaskStatus.PROCESSING.getCode())
                .lt(AnalysisTask::getStartedTime, deadline)
                .orderByAsc(AnalysisTask::getId)
                .last("LIMIT " + BATCH_SIZE));
        for (AnalysisTask task : staleTasks) {
            int retry = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
            //反复不收尾说明不是偶发失败，判失败给客户端一个终态，别让它无限等
            if (retry > TaskConstant.MAX_RETRY) {
                int rows = analysisTaskMapper.update(null, new LambdaUpdateWrapper<AnalysisTask>()
                        .eq(AnalysisTask::getId, task.getId())
                        .eq(AnalysisTask::getStatus, TaskStatus.PROCESSING.getCode())
                        .set(AnalysisTask::getStatus, TaskStatus.FAILED.getCode())
                        .set(AnalysisTask::getErrorMessage, "任务处理超时，请重新分析")
                        .set(AnalysisTask::getFinishedTime, LocalDateTime.now()));
                if (rows > 0) {
                    log.error("任务超时次数用尽，置为失败 taskId : {}", task.getId());
                    markDocumentFailed(task.getDocumentId());
                }
                continue;
            }
            //条件更新：只回收仍是 PROCESSING 的，避免把查询与更新之间刚好正常结束的任务拽回来
            int rows = analysisTaskMapper.update(null, new LambdaUpdateWrapper<AnalysisTask>()
                    .eq(AnalysisTask::getId, task.getId())
                    .eq(AnalysisTask::getStatus, TaskStatus.PROCESSING.getCode())
                    .set(AnalysisTask::getStatus, TaskStatus.PENDING.getCode())
                    .set(AnalysisTask::getRetryCount, retry));
            if (rows > 0) {
                log.warn("回收超时未收尾的任务 taskId : {}，startedTime : {}，retry : {}",
                        task.getId(), task.getStartedTime(), retry);
            }
        }
    }

    /**
     * 重投待处理任务：单条失败只跳过它自己，本轮其余任务照常处理
     */
    void retryPending() {
        List<AnalysisTask> tasks = analysisTaskMapper.selectList(new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getStatus, TaskStatus.PENDING.getCode())
                .gt(AnalysisTask::getRetryCount, 0)
                .orderByAsc(AnalysisTask::getId)
                .last("LIMIT " + BATCH_SIZE));
        for (AnalysisTask task : tasks) {
            try {
                Document document = documentMapper.selectById(task.getDocumentId());
                if (Objects.isNull(document)) {
                    continue;
                }
                log.info("补偿重投分析任务 taskId : {}，retryCount : {}", task.getId(), task.getRetryCount());
                taskProducer.dispatch(AnalysisTaskMessage.builder()
                        .taskId(task.getId())
                        .documentId(task.getDocumentId())
                        .fileUrl(document.getFileUrl())
                        .fileType(document.getFileType())
                        .build());
            } catch (Exception e) {
                log.error("补偿重投失败 taskId : {}，本轮跳过", task.getId(), e);
            }
        }
    }

    private void markDocumentFailed(Long documentId) {
        documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, documentId)
                .set(Document::getStatus, TaskStatus.FAILED.getCode())
                .set(Document::getUpdatedTime, LocalDateTime.now()));
    }
}
