package com.anxin.rocketmq.producer;

import cn.hutool.json.JSONUtil;
import com.anxin.rocketmq.message.AnalysisTaskMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * RocketMQ生产者
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "anxin.mq.enabled", havingValue = "true")
public class MqTaskProducer implements TaskProducer {
    private static final String TASK_MQ_DESTINATION = "anxin-analysis:task";

    @Resource
    private RocketMQTemplate rocketMQTemplate;


    @Override
    public void dispatch(AnalysisTaskMessage message) {
        log.info("投递分析任务 taskId: {}，documentId: {}", message.getTaskId(), message.getDocumentId());
        /**
         * 引入mq的目的是异步解耦，但这里为什么还有进行同步发送
         * 首先明确异步解耦指的是什么
         * 这里是业务逻辑：提交文档和耗时逻辑：分析任务之间的解耦
         * 而同步阻塞的是业务逻辑和MQ服务器之间的网络io
         * 如果异步发送会怎样 1.消息假发送成功 2.内存溢出
         */
        rocketMQTemplate.syncSend(TASK_MQ_DESTINATION, JSONUtil.toJsonStr(message));
    }
}
