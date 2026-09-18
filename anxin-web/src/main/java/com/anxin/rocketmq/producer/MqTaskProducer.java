package com.anxin.rocketmq.producer;

import cn.hutool.json.JSONUtil;
import com.anxin.enums.ResultCode;
import com.anxin.exception.ServiceException;
import com.anxin.rocketmq.message.AnalysisTaskMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * RocketMQ生产者
 */
@Slf4j
@Service
public class MqTaskProducer implements TaskProducer {
    private static final String TASK_MQ_DESTINATION = "anxin-analysis:task";

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void dispatchInTransaction(AnalysisTaskMessage message, Runnable localTransaction) {
        log.info("投递分析任务-事务消息 taskId: {}，documentId: {}", message.getTaskId(), message.getDocumentId());
        /**
         * 与普通投递的区别：这里先发半消息，此刻消息对消费者不可见，
         * 本地事务（任务落库）提交后才对消费者可见，因此不会出现「任务已落库但消息丢了」或「消息投了但库里没任务」
         */
        TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(
                TASK_MQ_DESTINATION,
                MessageBuilder.withPayload(JSONUtil.toJsonStr(message)).build(),
                localTransaction);
        //本地事务失败时半消息已被回滚，但 sendMessageInTransaction 不会抛异常，不检查会让调用方误判成功
        if (result.getLocalTransactionState() != LocalTransactionState.COMMIT_MESSAGE) {
            log.error("本地事务未提交，任务创建失败 taskId : {}，state : {}",
                    message.getTaskId(), result.getLocalTransactionState());
            throw new ServiceException(ResultCode.ANALYSIS_TASK_CREATE_FAILED);
        }
    }

    @Override
    public void dispatch(AnalysisTaskMessage message) {
        log.info("补偿重投分析任务 taskId: {}，documentId: {}", message.getTaskId(), message.getDocumentId());
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
