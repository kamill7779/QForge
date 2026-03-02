# QForge 当前真实数据库表结构

**更新时间**: 2026-03-02  
**分支**: `feat/xml-stem-storage-schema`  
**数据库**: MySQL 8.4（schema: `qforge`）

## 1. 真实性校验依据

以下结构不是“设计稿”，而是按当前仓库可运行代码反向还原后的真实结构：

1. 主初始化脚本：`backend/sql/init-schema.sql`（`backend/README.md` 明确当前手工初始化，Flyway 不作为运行入口）。
2. 服务迁移脚本：
   - `backend/services/auth-service/src/main/resources/db/migration/V1__init_auth.sql`
   - `backend/services/question-service/src/main/resources/db/migration/V1__init_question_bank.sql`
   - `backend/services/question-service/src/main/resources/db/migration/V2__xml_stem_storage.sql`
   - `backend/services/ocr-service/src/main/resources/db/migration/V1__init_ocr.sql`
3. 实体与仓储/业务代码：
   - `auth-service` JPA 实体 `UserAccount`
   - `question-service` MyBatis-Plus 实体与仓储
   - `ocr-service` MyBatis-Plus 实体与仓储

## 2. 当前真实建表 SQL（可直接执行）

> 与 `backend/sql/init-schema.sql` 对齐。

```sql
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS q_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_uuid CHAR(36) NOT NULL UNIQUE,
    owner_user VARCHAR(128) NOT NULL,
    stem_text LONGTEXT NULL,
    stem_image_id BIGINT NULL COMMENT '题干配图，指向 q_question_asset.id',
    status VARCHAR(32) NOT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
    difficulty DECIMAL(3,2) NULL COMMENT 'P-value difficulty coefficient 0.00-1.00',
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记',
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
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_q_answer_question_order (question_id, sort_order),
    CONSTRAINT fk_q_answer_question FOREIGN KEY (question_id) REFERENCES q_question(id)
);

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
```

## 3. 当前真实初始化数据（种子数据）

```sql
INSERT INTO user_account (username, password, enabled)
SELECT 'admin', '{noop}admin123', TRUE
WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE username = 'admin');

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
```

## 4. 代码层真实枚举/状态值（反向还原）

> 这些值来自当前服务代码中的写入/判断逻辑，不是设计预留值。

- `q_question.status`：`DRAFT`、`READY`
- `q_question.visibility`：默认 `PRIVATE`
- `q_answer.answer_type`：当前固定写入 `LATEX_TEXT`
- `q_tag.scope`：`SYSTEM`、`USER`
- `q_tag_category.category_kind`：`MAIN`、`SECONDARY`
- `q_tag_category.input_mode`：`SELECT`、`FREE_TEXT`
- `q_question_ocr_task.biz_type`：当前使用 `QUESTION_STEM`
- `q_question_ocr_task.status`：`PENDING`、`SUCCESS`、`FAILED`、`CONFIRMED`
- `q_ocr_task.status`：`PENDING`、`PROCESSING`、`SUCCESS`、`FAILED`
- `q_ocr_task.provider`：当前固定写入 `GLM_OCR`

## 5. 表关系（当前真实 FK）

- `q_tag.category_code` -> `q_tag_category.category_code`
- `q_answer.question_id` -> `q_question.id`
- `q_question.stem_image_id` -> `q_question_asset.id`（ON DELETE SET NULL）
- `q_question_asset.question_id` -> `q_question.id`
- `q_question_tag_rel.question_id` -> `q_question.id`
- `q_question_tag_rel.tag_id` -> `q_tag.id`
- `q_question_tag_rel.category_code` -> `q_tag_category.category_code`

## 6. 使用建议

本项目当前主分支数据库初始化建议以 `backend/sql/init-schema.sql` 为准；本文件作为面向研发与联调的“可读版真实结构文档”，与主脚本同步维护。
