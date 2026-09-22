package com.anxin.task;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务创建入口：把「任务落库」和「派发」收进同一个事务，事务提交后才真正投递
 */
@Slf4j
@Service
public class AnalysisTaskSubmitter {

    @Resource
    private TaskDispatcher taskDispatcher;

    /**
     * 事务边界刻意只包住这两步：OSS 上传、雪花取号等网络与外部调用必须留在外面，
     * 否则一次几十秒的上传会占住数据库连接和行锁
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(AnalysisTaskMessage message, Runnable localWrites) {
        localWrites.run();
        taskDispatcher.dispatch(message);
    }
}
