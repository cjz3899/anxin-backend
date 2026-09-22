-- 移除 RocketMQ 后，TaskRetryScheduler 每 30 秒按状态扫描任务表，缺索引会退化成全表扫
-- 已上线库执行这一句即可；新库直接跑 db.sql
ALTER TABLE `analysis_task`
    ADD INDEX `idx_status_started` (`status`, `started_time`);
