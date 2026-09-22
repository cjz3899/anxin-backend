package com.anxin.task;

import com.anxin.constant.TaskConstant;
import com.anxin.entity.AnalysisTask;
import com.anxin.entity.Document;
import com.anxin.enums.TaskStatus;
import com.anxin.mapper.AnalysisTaskMapper;
import com.anxin.mapper.DocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 任务补偿调度器。它不只是兜底，而是投递链路的一部分：
 * 一是把漏网与失败的 PENDING 任务重新派发（含进程在事务提交后、执行前被回收的情况）；
 * 二是回收僵死的 PROCESSING：消费端抢占后若写回终态时再次失败，任务会永远停在 PROCESSING，按超时兜底
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "anxin.task", name = "compensation-enabled", havingValue = "true", matchIfMissing = true)
public class TaskRetryScheduler {

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
    private TaskDispatcher taskDispatcher;

    //fixedDelay：上一次任务执行完毕后，等待固定时间再执行下一次任务
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
     * 重投待处理任务：单条失败只跳过它自己，本轮其余任务照常处理。
     * 扫描条件不带 retry_count，新建任务即使没被 afterCommit 投递成功（进程刚好被回收），这里也能捞回来
     */
    void retryPending() {
        List<AnalysisTask> tasks = analysisTaskMapper.selectList(new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getStatus, TaskStatus.PENDING.getCode())
                .orderByAsc(AnalysisTask::getId)
                .last("LIMIT " + BATCH_SIZE));
        for (AnalysisTask task : tasks) {
            try {
                Document document = documentMapper.selectById(task.getDocumentId());
                if (Objects.isNull(document)) {
                    continue;
                }
                log.info("补偿派发分析任务 taskId : {}，retryCount : {}", task.getId(), task.getRetryCount());
                taskDispatcher.dispatch(AnalysisTaskMessage.builder()
                        .taskId(task.getId())
                        .documentId(task.getDocumentId())
                        .fileUrl(document.getFileUrl())
                        .fileType(document.getFileType())
                        .build());
            } catch (Exception e) {
                log.error("补偿派发失败 taskId : {}，本轮跳过", task.getId(), e);
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
