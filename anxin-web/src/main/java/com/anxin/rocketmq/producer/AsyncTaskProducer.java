package com.anxin.rocketmq.producer;

import com.anxin.rocketmq.consumer.AnalysisTaskConsumer;
import com.anxin.rocketmq.message.AnalysisTaskMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 基于 @Async 线程池的任务生产者（先行版，将来切换 RocketMQ 时新增 MQ 版生产者即可）。
 */
@Slf4j
@Service
public class AsyncTaskProducer implements TaskProducer {

    @Resource
    private AnalysisTaskConsumer analysisTaskConsumer;

    @Override
    @Async("taskExecutor")
    public void dispatch(AnalysisTaskMessage message) {
        log.info("收到异步任务 taskId : {}, documentId : {}", message.getTaskId(), message.getDocumentId());
        analysisTaskConsumer.process(message);
    }
}
