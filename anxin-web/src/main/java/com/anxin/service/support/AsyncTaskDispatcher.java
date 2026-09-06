package com.anxin.service.support;

import com.anxin.dto.AnalysisTaskMessage;
import com.anxin.service.TaskDispatcher;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 基于 @Async 线程池的任务投递实现（先行版，后续可替换为 RocketMQ 实现）。
 */
@Slf4j
@Service
public class AsyncTaskDispatcher implements TaskDispatcher {

    @Resource
    private AnalysisTaskProcessor analysisTaskProcessor;

    @Override
    @Async("taskExecutor")
    public void dispatch(AnalysisTaskMessage message) {
        log.info("收到异步任务 taskId : {}, documentId : {}", message.getTaskId(), message.getDocumentId());
        analysisTaskProcessor.process(message);
    }
}
