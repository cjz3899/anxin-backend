package com.anxin.task;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.RejectedExecutionException;

/**
 * 本机线程池版任务派发器
 */
@Slf4j
@Service
public class LocalTaskDispatcher implements TaskDispatcher {

    @Resource
    private AnalysisTaskConsumer analysisTaskConsumer;

    @Resource(name = "analysisTaskExecutor")
    private ThreadPoolTaskExecutor analysisTaskExecutor;

    @Override
    public void dispatch(AnalysisTaskMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(message);
                }
            });
            return;
        }
        submit(message);
    }

    private void submit(AnalysisTaskMessage message) {
        try {
            analysisTaskExecutor.execute(() -> analysisTaskConsumer.process(message));
        } catch (RejectedExecutionException e) {
            /**
             * 池满即背压：不改成 CallerRunsPolicy，否则几十秒的分析会跑在 HTTP 线程或调度线程上，
             * 上传接口会直接被拖死。留在 PENDING 等下一轮扫描才是正确姿势
             */
            log.warn("分析线程池已满，任务留在 PENDING 等待补偿扫描 taskId : {}", message.getTaskId());
        }
    }
}
