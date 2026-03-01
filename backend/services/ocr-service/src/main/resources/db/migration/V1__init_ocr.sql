CREATE TABLE IF NOT EXISTS q_ocr_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_uuid CHAR(36) NOT NULL UNIQUE,
    biz_type VARCHAR(32) NOT NULL,
    biz_id CHAR(36) NOT NULL,
    image_base64 LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    request_user VARCHAR(128) NOT NULL,
    recognized_text LONGTEXT NULL,
    error_msg VARCHAR(1024) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_q_ocr_task_biz (biz_type, biz_id, status, created_at)
);

