package com.anxin.rocketmq.consumer;

import com.anxin.entity.AnalysisTask;
import com.anxin.entity.Document;
import com.anxin.enums.TaskStatus;
import com.anxin.mapper.AnalysisTaskMapper;
import com.anxin.mapper.DocumentMapper;
import com.anxin.rocketmq.message.AnalysisTaskMessage;
import com.anxin.rocketmq.producer.TaskProducer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 待重试任务补偿扫描：失败置回PENDING后消息已消费完毕，需要定时重投
 */
@Slf4j
@Component
public class TaskRetrySchedular {

    @Resource
    private AnalysisTaskMapper analysisTaskMapper;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private TaskProducer taskProducer;

    // TODO 之后可改成DLQ死信队列补偿机制
    @Scheduled(fixedDelay = 30000)
    public void retryPendingTasks() {
        List<AnalysisTask> tasks = analysisTaskMapper.selectList(new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getStatus, TaskStatus.PENDING.getCode())
                .gt(AnalysisTask::getRetryCount, 0));
        for (AnalysisTask task : tasks) {
            Document document = documentMapper.selectById(task.getDocumentId());
            if (Objects.isNull(document)) {
                continue;
            }
            log.info("补偿重投分析任务 taskId : {}，retryCount : {}", task.getId(), task.getRetryCount());
            taskProducer.dispatch(AnalysisTaskMessage.builder()
                    .taskId(task.getId())
                    .documentId(task.getDocumentId())
                    .fileUrl(document.getFileUrl())
                    .fileType(document.getFileType())
                    .build());

        }
    }
}
