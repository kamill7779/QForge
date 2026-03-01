SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS q_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_uuid CHAR(36) NOT NULL UNIQUE,
    owner_user VARCHAR(128) NOT NULL,
    stem_text LONGTEXT NULL,
    status VARCHAR(32) NOT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
    difficulty VARCHAR(32) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_q_question_owner_status (owner_user, status, visibility, updated_at)
);

CREATE TABLE IF NOT EXISTS q_tag_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_code VARCHAR(64) NOT NULL UNIQUE,
    category_name VARCHAR(128) NOT NULL,
    category_kind VARCHAR(16) NOT NULL,
    input_mode VARCHAR(16) NOT NULL,
    allow_user_create BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_q_tag_category_kind (category_kind, sort_order)
);

CREATE TABLE IF NOT EXISTS q_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tag_uuid CHAR(36) NOT NULL UNIQUE,
    category_code VARCHAR(64) NOT NULL,
    tag_code VARCHAR(128) NOT NULL,
    tag_name VARCHAR(255) NOT NULL,
    scope VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
    owner_user VARCHAR(128) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_q_tag_scope_owner_category_code (scope, owner_user, category_code, tag_code),
    INDEX idx_q_tag_category_scope (category_code, scope, owner_user, tag_name),
    CONSTRAINT fk_q_tag_category FOREIGN KEY (category_code) REFERENCES q_tag_category(category_code)
);

CREATE TABLE IF NOT EXISTS q_answer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    answer_uuid CHAR(36) NOT NULL UNIQUE,
    question_id BIGINT NOT NULL,
    answer_type VARCHAR(32) NOT NULL,
    latex_text LONGTEXT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    is_official BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_q_answer_question_order (question_id, sort_order),
    CONSTRAINT fk_q_answer_question FOREIGN KEY (question_id) REFERENCES q_question(id)
);

CREATE TABLE IF NOT EXISTS q_question_tag_rel (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    category_code VARCHAR(64) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_q_question_tag (question_id, tag_id),
    INDEX idx_q_qtagrel_question_category (question_id, category_code),
    INDEX idx_q_qtagrel_tag (tag_id),
    CONSTRAINT fk_q_qtagrel_question FOREIGN KEY (question_id) REFERENCES q_question(id),
    CONSTRAINT fk_q_qtagrel_tag FOREIGN KEY (tag_id) REFERENCES q_tag(id),
    CONSTRAINT fk_q_qtagrel_category FOREIGN KEY (category_code) REFERENCES q_tag_category(category_code)
);

CREATE TABLE IF NOT EXISTS q_question_ocr_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_uuid CHAR(36) NOT NULL UNIQUE,
    question_uuid CHAR(36) NOT NULL,
    biz_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_user VARCHAR(128) NOT NULL,
    recognized_text LONGTEXT NULL,
    error_msg VARCHAR(1024) NULL,
    confirmed_text LONGTEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_q_qocr_task_question (question_uuid, status, updated_at)
);

INSERT INTO q_tag_category (category_code, category_name, category_kind, input_mode, allow_user_create, sort_order, enabled)
SELECT 'MAIN_GRADE', '年级', 'MAIN', 'SELECT', FALSE, 10, TRUE
WHERE NOT EXISTS (SELECT 1 FROM q_tag_category WHERE category_code = 'MAIN_GRADE');

INSERT INTO q_tag_category (category_code, category_name, category_kind, input_mode, allow_user_create, sort_order, enabled)
SELECT 'MAIN_KNOWLEDGE', '知识点', 'MAIN', 'SELECT', FALSE, 20, TRUE
WHERE NOT EXISTS (SELECT 1 FROM q_tag_category WHERE category_code = 'MAIN_KNOWLEDGE');

INSERT INTO q_tag_category (category_code, category_name, category_kind, input_mode, allow_user_create, sort_order, enabled)
SELECT 'SECONDARY_CUSTOM', '副标签', 'SECONDARY', 'FREE_TEXT', TRUE, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM q_tag_category WHERE category_code = 'SECONDARY_CUSTOM');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000101', 'MAIN_GRADE', 'UNCATEGORIZED', '未分类', 'SYSTEM', ''
WHERE NOT EXISTS (
    SELECT 1 FROM q_tag
    WHERE scope = 'SYSTEM' AND owner_user = '' AND category_code = 'MAIN_GRADE' AND tag_code = 'UNCATEGORIZED'
);

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000102', 'MAIN_KNOWLEDGE', 'UNCATEGORIZED', '未分类', 'SYSTEM', ''
WHERE NOT EXISTS (
    SELECT 1 FROM q_tag
    WHERE scope = 'SYSTEM' AND owner_user = '' AND category_code = 'MAIN_KNOWLEDGE' AND tag_code = 'UNCATEGORIZED'
);


