package com.anxin.service;

import com.anxin.dto.AnalysisTaskMessage;

/**
 * 分析任务投递接口：上传端只依赖本接口，不感知底层是线程池还是 MQ。
 */
public interface TaskDispatcher {

    void dispatch(AnalysisTaskMessage message);
}
