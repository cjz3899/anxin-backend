package com.anxin.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 分析任务线程池。任务是 OCR + LLM 混合负载，并发数受实例 CPU 与内存约束，不要按 Web 线程池的习惯放大
 */
@Configuration
public class TaskExecutorConfig {

    @Bean(name = "analysisTaskExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor analysisTaskExecutor(
            @Value("${anxin.task.worker-size:2}") int workerSize,
            @Value("${anxin.task.queue-size:64}") int queueSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workerSize);
        executor.setMaxPoolSize(workerSize);
        executor.setQueueCapacity(queueSize);
        executor.setThreadNamePrefix("analysis-task-");
        //饱和直接拒绝，交给补偿扫描重投，不让调用线程代跑
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        //实例被回收时等在跑的任务收尾，收不完的由 PROCESSING 超时回收兜底
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 回答生成的线程池，与分析任务分开：分析是 OCR + LLM 的 CPU 密集长任务，
     * 共用一个池会让用户提问排在一份扫描件后面
     */
    @Bean(name = "chatReplyExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor chatReplyExecutor(
            @Value("${anxin.task.chat-worker-size:4}") int workerSize,
            @Value("${anxin.task.chat-queue-size:64}") int queueSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workerSize);
        executor.setMaxPoolSize(workerSize);
        executor.setQueueCapacity(queueSize);
        executor.setThreadNamePrefix("chat-reply-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
