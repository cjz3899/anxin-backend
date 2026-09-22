package com.anxin.task;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.RejectedExecutionException;

/**
 * 回答生成派发器：提问事务提交后再交给线程池，避免工作线程读不到刚插入的占位消息
 */
@Slf4j
@Service
public class ChatReplyDispatcher {

    @Resource
    private ChatReplyConsumer chatReplyConsumer;

    @Resource(name = "chatReplyExecutor")
    private ThreadPoolTaskExecutor chatReplyExecutor;

    public void dispatch(Long messageId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(messageId);
                }
            });
            return;
        }
        submit(messageId);
    }

    private void submit(Long messageId) {
        try {
            chatReplyExecutor.execute(() -> chatReplyConsumer.generate(messageId));
        } catch (RejectedExecutionException e) {
            //池满即背压，消息留在 PENDING，由 ChatReplyScheduler 的清扫重新派发
            log.warn("回答线程池已满，等待清扫重投 messageId : {}", messageId);
        }
    }
}
