-- 聊天回答改异步：提问接口立即返回 ASSISTANT 消息 ID，前端轮询它的 status
-- status 取值与 analysis_task.status 一致：0-待生成，1-生成中，2-成功，3-失败
-- 默认值取 2，存量消息与用户消息天然就是终态，不需要回填
ALTER TABLE `chat_message`
    MODIFY COLUMN `id` BIGINT NOT NULL COMMENT '主键ID，应用侧雪花生成',
    ADD COLUMN `status` TINYINT NOT NULL DEFAULT 2 COMMENT '状态：0-待生成，1-生成中，2-成功，3-失败；用户消息恒为2' AFTER `content`,
    ADD COLUMN `error_message` VARCHAR(500) DEFAULT NULL COMMENT '回答生成失败原因' AFTER `status`,
    ADD INDEX `idx_status_created` (`status`, `created_time`);
