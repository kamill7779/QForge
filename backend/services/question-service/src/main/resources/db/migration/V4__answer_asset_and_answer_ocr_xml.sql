SET NAMES utf8mb4;

-- V4: answer OCR assets + answer image persistence
-- 1) Backfill q_question_asset for ref-key based XML references (if older V2 schema)
SET @has_ref_key := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'q_question_asset'
      AND COLUMN_NAME = 'ref_key'
);
SET @ddl_add_ref_key := IF(
    @has_ref_key = 0,
    'ALTER TABLE q_question_asset ADD COLUMN ref_key VARCHAR(64) NULL COMMENT ''frontend XML reference key, e.g. img-1'' AFTER asset_type',
    'SELECT 1'
);
PREPARE stmt_add_ref_key FROM @ddl_add_ref_key;
EXECUTE stmt_add_ref_key;
DEALLOCATE PREPARE stmt_add_ref_key;

ALTER TABLE q_question_asset
    MODIFY COLUMN image_data MEDIUMTEXT NOT NULL COMMENT 'base64 image data';

-- 2) New answer asset table (persist answer inline images and OCR cropped images)
CREATE TABLE IF NOT EXISTS q_answer_asset (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    asset_uuid  CHAR(36)      NOT NULL UNIQUE,
    question_id BIGINT        NOT NULL,
    answer_id   BIGINT        NOT NULL,
    ref_key     VARCHAR(64)   NOT NULL COMMENT 'answer XML ref key, e.g. a92f6c03-img-1',
    image_data  MEDIUMTEXT    NOT NULL COMMENT 'base64 image data',
    mime_type   VARCHAR(128)  NULL COMMENT 'image/png, image/jpeg etc',
    deleted     BOOLEAN       NOT NULL DEFAULT FALSE COMMENT 'logical delete flag',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_q_answer_asset_answer_ref (answer_id, ref_key),
    INDEX idx_q_answer_asset_question (question_id, answer_id),
    CONSTRAINT fk_q_answer_asset_question FOREIGN KEY (question_id) REFERENCES q_question(id),
    CONSTRAINT fk_q_answer_asset_answer FOREIGN KEY (answer_id) REFERENCES q_answer(id)
);
