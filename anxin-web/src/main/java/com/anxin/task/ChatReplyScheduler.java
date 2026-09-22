package com.anxin.task;

import com.anxin.enums.TaskStatus;
import com.anxin.entity.ChatMessage;
import com.anxin.mapper.ChatMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回答清扫调度器：提问是用户在前台等的，所以这里只保证「不会永远转圈」，不做多次重试
 * 一是把 PENDING 却迟迟没跑起来的重新派发（实例在事务提交后、执行前被回收）；
 * 二是把 PROCESSING 超时的判失败，给用户一个终态
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "anxin.task", name = "compensation-enabled", havingValue = "true", matchIfMissing = true)
public class ChatReplyScheduler {

    /**
     * PENDING 超过该秒数还没开跑，说明派发丢了，重新派发
     */
    private static final long PENDING_STALE_SECONDS = 120;

    /**
     * PROCESSING 超过该分钟数还没写回终态，判失败。模型正常几秒到几十秒，取 5 分钟留足余量
     */
    private static final long PROCESSING_TIMEOUT_MINUTES = 5;

    private static final int BATCH_SIZE = 100;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private ChatReplyDispatcher chatReplyDispatcher;

    @Scheduled(fixedDelay = 60000)
    public void sweep() {
        try {
            recoverStaleProcessing();
        } catch (Exception e) {
            //判失败失败不能拖累重投
            log.error("回收超时回答失败", e);
        }
        redispatchPending();
    }

    private void recoverStaleProcessing() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(PROCESSING_TIMEOUT_MINUTES);
        List<ChatMessage> stale = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getStatus, TaskStatus.PROCESSING.getCode())
                .lt(ChatMessage::getUpdatedTime, deadline)
                .orderByAsc(ChatMessage::getId)
                .last("LIMIT " + BATCH_SIZE));
        for (ChatMessage message : stale) {
            int rows = chatMessageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                    .eq(ChatMessage::getId, message.getId())
                    .eq(ChatMessage::getStatus, TaskStatus.PROCESSING.getCode())
                    .set(ChatMessage::getStatus, TaskStatus.FAILED.getCode())
                    .set(ChatMessage::getErrorMessage, "回答生成超时，请重新提问")
                    .set(ChatMessage::getUpdatedTime, LocalDateTime.now()));
            if (rows > 0) {
                log.warn("回答生成超时，置为失败 messageId : {}", message.getId());
            }
        }
    }

    private void redispatchPending() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(PENDING_STALE_SECONDS);
        List<ChatMessage> pending = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getStatus, TaskStatus.PENDING.getCode())
                .lt(ChatMessage::getCreatedTime, deadline)
                .orderByAsc(ChatMessage::getId)
                .last("LIMIT " + BATCH_SIZE));
        for (ChatMessage message : pending) {
            try {
                log.info("重新派发回答生成 messageId : {}", message.getId());
                chatReplyDispatcher.dispatch(message.getId());
            } catch (Exception e) {
                log.error("重新派发回答失败 messageId : {}，本轮跳过", message.getId(), e);
            }
        }
    }
}
