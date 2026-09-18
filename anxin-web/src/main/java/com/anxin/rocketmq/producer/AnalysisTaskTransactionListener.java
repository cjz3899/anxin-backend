package com.anxin.rocketmq.producer;

import cn.hutool.json.JSONUtil;
import com.anxin.mapper.AnalysisTaskMapper;
import com.anxin.rocketmq.message.AnalysisTaskMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

/**
 * 分析任务事务消息监听器
 * 本地事务：任务落库；回查：按消息里的 taskId 核对库中任务是否真的存在
 */
@Slf4j
@Component
@RocketMQTransactionListener
public class AnalysisTaskTransactionListener implements RocketMQLocalTransactionListener {

    @Resource
    private AnalysisTaskMapper analysisTaskMapper;

    /**
     * 执行本地事务（在发送半消息的调用线程里同步执行）
     * 这里不要捕获异常：声明式事务靠异常传播回滚，一旦 catch 住再返回 ROLLBACK，
     * 事务代理看不到异常会照常提交，落库就只成功了一半，所以需要交给异常来触发回滚
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        ((Runnable) arg).run();
        return RocketMQLocalTransactionState.COMMIT;
    }

    /**
     * 回查：只在「本地事务已执行但提交状态没送达 broker」时触发（生产者崩溃、网络中断），
     * 此时看库：任务在说明当时落库成功，补提交；不在说明本地事务没成功，回滚
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String payload = payloadOf(msg);
        AnalysisTaskMessage message;
        try {
            message = JSONUtil.toBean(payload, AnalysisTaskMessage.class);
        } catch (Exception e) {
            //解析不了就无法核对，交给 broker 按回查策略重试，比直接丢弃消息安全
            log.error("事务消息回查解析失败 payload : {}", payload, e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
        boolean stored = message.getTaskId() != null
                && analysisTaskMapper.selectById(message.getTaskId()) != null;
        log.info("事务消息回查 taskId : {}，库中任务{}，{}", message.getTaskId(),
                stored ? "已存在" : "不存在", stored ? "提交" : "回滚");
        return stored ? RocketMQLocalTransactionState.COMMIT : RocketMQLocalTransactionState.ROLLBACK;
    }

    private String payloadOf(Message msg) {
        Object payload = msg.getPayload();
        return payload instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(payload);
    }
}
