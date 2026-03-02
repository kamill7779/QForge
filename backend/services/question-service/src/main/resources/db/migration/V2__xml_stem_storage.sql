-- V2: XML 题干存储方案 - 新增 q_question_asset 表、逻辑删除支持
SET NAMES utf8mb4;

-- 1. 新增 q_question_asset 表
CREATE TABLE IF NOT EXISTS q_question_asset (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    asset_uuid  CHAR(36)      NOT NULL UNIQUE,
    question_id BIGINT        NOT NULL,
    asset_type  VARCHAR(32)   NOT NULL  COMMENT 'STEM_IMAGE / CHOICE_IMAGE',
    image_data  LONGTEXT      NOT NULL  COMMENT '图片 base64 编码数据',
    file_name   VARCHAR(255)  NULL      COMMENT '原始文件名',
    mime_type   VARCHAR(128)  NULL      COMMENT 'image/png, image/jpeg 等',
    deleted     BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_q_asset_question (question_id, asset_type),
    INDEX idx_q_asset_deleted (deleted),
    CONSTRAINT fk_q_asset_question FOREIGN KEY (question_id) REFERENCES q_question(id)
) COMMENT '题目关联资源（图片 base64 存储）';

-- 2. q_question 新增 stem_image_id 和 deleted 字段
ALTER TABLE q_question
    ADD COLUMN stem_image_id BIGINT NULL COMMENT '题干配图，指向 q_question_asset.id' AFTER stem_text,
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记' AFTER difficulty;

ALTER TABLE q_question
    ADD CONSTRAINT fk_q_question_stem_image
        FOREIGN KEY (stem_image_id) REFERENCES q_question_asset(id)
        ON DELETE SET NULL;

-- 3. q_answer 新增 deleted 字段
ALTER TABLE q_answer
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记' AFTER is_official;
