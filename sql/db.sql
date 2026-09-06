CREATE DATABASE IF NOT EXISTS anxin_db
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE anxin_db;

-- =========================================================
-- 1. 用户表
-- =========================================================
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user`
(
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `openid`       VARCHAR(64) NOT NULL COMMENT '微信用户唯一标识',
    `nickname`     VARCHAR(100)         DEFAULT NULL COMMENT '用户昵称',
    `avatar`       VARCHAR(500)         DEFAULT NULL COMMENT '用户头像URL',
    `created_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_openid` (`openid`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户表';


-- =========================================================
-- 2. 文件表
-- =========================================================
DROP TABLE IF EXISTS `document`;

CREATE TABLE `document`
(
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`      BIGINT        NOT NULL COMMENT '用户ID，逻辑关联user.id',
    `file_name`    VARCHAR(255)  NOT NULL COMMENT '原始文件名',
    `file_type`    VARCHAR(20)   NOT NULL COMMENT '文件类型：PDF、DOCX、IMAGE等',
    `file_size`    BIGINT        NOT NULL DEFAULT 0 COMMENT '文件大小，单位：Byte',
    `file_url`     VARCHAR(1000) NOT NULL COMMENT '文件存储地址',
    `status`       TINYINT       NOT NULL DEFAULT 0 COMMENT '文件状态：0-待处理，1-处理中，2-处理完成，3-处理失败',
    `summary`      VARCHAR(2000)          DEFAULT NULL COMMENT '文件风险摘要',
    `created_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户上传文件表';


-- =========================================================
-- 3. 文档章节/条款表
-- =========================================================
DROP TABLE IF EXISTS `document_section`;

CREATE TABLE `document_section`
(
    `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `document_id`  BIGINT   NOT NULL COMMENT '文件ID，逻辑关联document.id',
    `section_no`   VARCHAR(50)       DEFAULT NULL COMMENT '章节/条款编号',
    `title`        VARCHAR(500)      DEFAULT NULL COMMENT '章节标题',
    `content`      TEXT     NOT NULL COMMENT '章节正文',
    `page_no`      INT               DEFAULT NULL COMMENT '所在页码',
    `sort`         INT      NOT NULL DEFAULT 0 COMMENT '章节排序',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='文档章节及条款表';


-- =========================================================
-- 4. AI分析任务表
-- =========================================================
DROP TABLE IF EXISTS `analysis_task`;

CREATE TABLE `analysis_task`
(
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `document_id`   BIGINT      NOT NULL COMMENT '文件ID，逻辑关联document.id',
    `task_type`     VARCHAR(30) NOT NULL COMMENT '任务类型：RISK_ANALYSIS等',
    `status`        TINYINT     NOT NULL DEFAULT 0 COMMENT '任务状态：0-待处理，1-处理中，2-成功，3-失败',
    `retry_count`   INT         NOT NULL DEFAULT 0 COMMENT '重试次数',
    `error_message` VARCHAR(2000)        DEFAULT NULL COMMENT '失败原因',
    `started_time`  DATETIME             DEFAULT NULL COMMENT '任务开始时间',
    `finished_time` DATETIME             DEFAULT NULL COMMENT '任务完成时间',
    `created_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='文件AI分析任务表';


-- =========================================================
-- 5. 风险分析结果表
-- =========================================================
DROP TABLE IF EXISTS `risk_result`;

CREATE TABLE `risk_result`
(
    `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`      BIGINT   NOT NULL COMMENT '分析任务ID，逻辑关联analysis_task.id',
    `document_id`  BIGINT   NOT NULL COMMENT '文件ID，逻辑关联document.id',
    `risk_summary` VARCHAR(3000)     DEFAULT NULL COMMENT '整体风险分析摘要',
    `high_count`   INT      NOT NULL DEFAULT 0 COMMENT '高风险数量',
    `medium_count` INT      NOT NULL DEFAULT 0 COMMENT '中风险数量',
    `low_count`    INT      NOT NULL DEFAULT 0 COMMENT '低风险数量',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='文件风险分析结果表';


-- =========================================================
-- 6. 风险详情表
-- =========================================================
DROP TABLE IF EXISTS `risk_detail`;

CREATE TABLE `risk_detail`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `risk_result_id` BIGINT       NOT NULL COMMENT '风险结果ID，逻辑关联risk_result.id',
    `section_id`     BIGINT       NOT NULL COMMENT '文档章节ID，逻辑关联document_section.id',
    `risk_type`      VARCHAR(50)  NOT NULL COMMENT '风险类型',
    `risk_level`     VARCHAR(20)  NOT NULL COMMENT '风险等级：HIGH、MEDIUM、LOW',
    `title`          VARCHAR(255) NOT NULL COMMENT '风险标题',
    `original_text`  TEXT         NOT NULL COMMENT '风险对应原文',
    `reason`         TEXT COMMENT '风险原因',
    `impact`         TEXT COMMENT '潜在影响',
    `suggestion`     TEXT COMMENT '处理建议',
    `start_position` INT                   DEFAULT NULL COMMENT '原文开始位置',
    `end_position`   INT                   DEFAULT NULL COMMENT '原文结束位置',
    `created_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='文件风险详情表';


-- =========================================================
-- 7. AI聊天会话表
-- =========================================================
DROP TABLE IF EXISTS `chat_session`;

CREATE TABLE `chat_session`
(
    `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`      BIGINT   NOT NULL COMMENT '用户ID，逻辑关联user.id',
    `document_id`  BIGINT   NOT NULL COMMENT '文件ID，逻辑关联document.id',
    `title`        VARCHAR(255)      DEFAULT NULL COMMENT '会话标题',
    `status`       TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：0-关闭，1-正常',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='AI聊天会话表';


-- =========================================================
-- 8. AI聊天消息表
-- =========================================================
DROP TABLE IF EXISTS `chat_message`;

CREATE TABLE `chat_message`
(
    `id`                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`         BIGINT      NOT NULL COMMENT '会话ID，逻辑关联chat_session.id',
    `role`               VARCHAR(20) NOT NULL COMMENT '消息角色：USER、ASSISTANT',
    `content`            TEXT        NOT NULL COMMENT '消息内容',
    `reference_sections` VARCHAR(2000)        DEFAULT NULL COMMENT '引用的文档章节ID，JSON格式',
    `token_usage`        INT                  DEFAULT NULL COMMENT '本次消息Token消耗',
    `created_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='AI聊天消息表';