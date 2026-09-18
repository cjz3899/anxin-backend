package com.anxin.rocketmq.producer;

import com.anxin.rocketmq.message.AnalysisTaskMessage;

/**
 * 任务生产者接口
 */
public interface TaskProducer {

    /**
     * 事务消息投递：半消息先落盘，本地事务成功后才提交消息，保证「任务落库」与「消息可消费」同时成立。
     * 落库失败则消息回滚，broker 不可用则本地事务根本不会执行，两端不会出现单边成功。
     *
     * @param message          消息体，taskId / documentId 需在调用前生成好（回查要靠消息里的 taskId 核对）
     * @param localTransaction 本地事务，通常是任务落库（失败要让异常抛出，不要在此捕获）
     */
    void dispatchInTransaction(AnalysisTaskMessage message, Runnable localTransaction);

    /**
     * 投递已落库任务的消息（重投场景：任务已在库，用普通消息即可，丢失由补偿扫描兜底）
     */
    void dispatch(AnalysisTaskMessage message);
}
