package com.anxin.rocketmq.producer;

import com.anxin.rocketmq.message.AnalysisTaskMessage;

/**
 * 任务生产者接口
 */
public interface TaskProducer {

    void dispatch(AnalysisTaskMessage message);
}
