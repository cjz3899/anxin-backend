package com.anxin.rocketmq.consumer;

import cn.hutool.json.JSONUtil;
import com.anxin.rocketmq.message.AnalysisTaskMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 分析任务MQ监听器
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "anxin.mq.enabled", havingValue = "true")
@RocketMQMessageListener(topic = "anxin-analysis",
        consumerGroup = "anxin-analysis-consumer",
        selectorExpression = "task")
public class AnalysisTaskListener implements RocketMQListener<String> {
    @Resource
    private AnalysisTaskConsumer analysisTaskConsumer;

    @Override
    public void onMessage(String json) {
        try {
            analysisTaskConsumer.process(JSONUtil.toBean(json, AnalysisTaskMessage.class));
        } catch (Exception e) {
            /**
             * 不抛出异常，防止走rocketMQ自带的重试机制
             * 走自定义的状态机重试机制
             */
            log.error("消息处理异常 json: {}", json, e);
        }
    }
}
