package com.anxin.rocketmq.producer;

import com.anxin.rocketmq.message.AnalysisTaskMessage;

/**
 * 任务生产者接口：上传端只依赖本接口，不感知底层是线程池还是 RocketMQ。
 */
public interface TaskProducer {

    void dispatch(AnalysisTaskMessage message);
}
