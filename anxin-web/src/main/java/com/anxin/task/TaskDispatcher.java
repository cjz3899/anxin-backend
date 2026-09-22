package com.anxin.task;

/**
 * 分析任务派发器
 */
public interface TaskDispatcher {

    /**
     * 派发已落库的任务：当前有事务时等提交成功后再执行，避免线程读到未提交的数据；无事务时立即执行。
     * 执行失败或线程池饱和都不重投，任务留在 PENDING 由 {@link TaskRetryScheduler} 兜底
     */
    void dispatch(AnalysisTaskMessage message);
}
