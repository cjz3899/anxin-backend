package com.anxin.rocketmq.producer;

import com.anxin.rocketmq.consumer.AnalysisTaskConsumer;
import com.anxin.rocketmq.message.AnalysisTaskMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 本地开发任务生产者：不依赖 RocketMQ，直接复用消费者状态机。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "anxin.mq.enabled", havingValue = "false", matchIfMissing = true)
public class LocalTaskProducer implements TaskProducer {

    @Resource
    private AnalysisTaskConsumer analysisTaskConsumer;

    @Override
    public void dispatch(AnalysisTaskMessage message) {
        log.info("本地模式执行分析任务 taskId: {}，documentId: {}", message.getTaskId(), message.getDocumentId());
        analysisTaskConsumer.process(message);
    }
}
