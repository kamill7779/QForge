-- QForge manual schema init (Flyway removed)
-- Usage:
--   mysql --default-character-set=utf8mb4 -h127.0.0.1 -P3306 -uqforge -pqforge qforge < backend/sql/init-schema.sql
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO user_account (username, password, enabled)
SELECT 'admin', '{noop}admin', TRUE
WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE username = 'admin');

CREATE TABLE IF NOT EXISTS q_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_uuid CHAR(36) NOT NULL UNIQUE,
    owner_user VARCHAR(128) NOT NULL,
    stem_text LONGTEXT NULL,
    stem_image_id BIGINT NULL COMMENT '题干配图，指向 q_question_asset.id',
    status VARCHAR(32) NOT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
    difficulty DECIMAL(3,2) NULL COMMENT 'P-value difficulty coefficient 0.00-1.00',
    source VARCHAR(255) DEFAULT '未分类' COMMENT '题目来源',
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
    asset_type  VARCHAR(32)   NOT NULL  COMMENT 'INLINE_IMAGE / STEM_IMAGE / CHOICE_IMAGE',
    ref_key     VARCHAR(64)   NULL      COMMENT '前端 XML 引用 key，如 img-1、img-2',
    image_data  MEDIUMTEXT    NOT NULL  COMMENT '图片 base64 编码数据（每张 ≤ 40KB）',
    file_name   VARCHAR(255)  NULL      COMMENT '原始文件名',
    mime_type   VARCHAR(128)  NULL      COMMENT 'image/png, image/jpeg 等',
    deleted     BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_q_asset_question     (question_id, asset_type),
    INDEX idx_q_asset_question_ref (question_id, ref_key),
    CONSTRAINT fk_q_asset_question FOREIGN KEY (question_id) REFERENCES q_question(id)
) COMMENT '题目关联资源（图片 base64），每题最多 10 张，每张最多 512KB';

CREATE TABLE IF NOT EXISTS q_answer_asset (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    asset_uuid  CHAR(36)      NOT NULL UNIQUE,
    question_id BIGINT        NOT NULL,
    answer_id   BIGINT        NOT NULL,
    ref_key     VARCHAR(64)   NOT NULL COMMENT '答案 XML 引用 key，如 a92f6c03-img-1',
    image_data  MEDIUMTEXT    NOT NULL COMMENT '图片 base64 编码数据',
    mime_type   VARCHAR(128)  NULL COMMENT 'image/png, image/jpeg 等',
    deleted     BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_q_answer_asset_answer_ref (answer_id, ref_key),
    INDEX idx_q_answer_asset_question (question_id, answer_id),
    CONSTRAINT fk_q_answer_asset_question FOREIGN KEY (question_id) REFERENCES q_question(id),
    CONSTRAINT fk_q_answer_asset_answer FOREIGN KEY (answer_id) REFERENCES q_answer(id)
) COMMENT '答案关联资源（图片 base64）';

ALTER TABLE q_question
    ADD CONSTRAINT fk_q_question_stem_image
        FOREIGN KEY (stem_image_id) REFERENCES q_question_asset(id)
        ON DELETE SET NULL;

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
    biz_type VARCHAR(32) NOT NULL COMMENT 'QUESTION_STEM / ANSWER_CONTENT',
    status VARCHAR(32) NOT NULL COMMENT 'PENDING / PROCESSING / CONFIRMED / FAILED',
    request_user VARCHAR(128) NOT NULL,
    recognized_text LONGTEXT NULL,
    error_msg VARCHAR(1024) NULL,
    confirmed_text LONGTEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_q_qocr_task_question_biz (question_uuid, biz_type, status, updated_at)
);

CREATE TABLE IF NOT EXISTS q_ocr_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_uuid CHAR(36) NOT NULL UNIQUE,
    biz_type VARCHAR(32) NOT NULL COMMENT 'QUESTION_STEM / ANSWER_CONTENT',
    biz_id CHAR(36) NOT NULL,
    image_base64 LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL COMMENT 'PENDING / PROCESSING / SUCCESS / FAILED',
    provider VARCHAR(64) NOT NULL,
    request_user VARCHAR(128) NOT NULL,
    recognized_text LONGTEXT NULL,
    error_msg VARCHAR(1024) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_q_ocr_task_biz (biz_type, biz_id, status, created_at)
);

-- ==================== AI Analysis Tables ====================

CREATE TABLE IF NOT EXISTS q_ai_analysis_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_uuid CHAR(36) NOT NULL UNIQUE,
    question_uuid CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL COMMENT 'PENDING / PROCESSING / SUCCESS / FAILED',
    model VARCHAR(64) NULL COMMENT '使用的 AI 模型标识',
    user_prompt LONGTEXT NULL COMMENT '拼接后的用户提示词',
    raw_response LONGTEXT NULL COMMENT 'AI 原始响应',
    suggested_tags TEXT NULL COMMENT '推荐标签 JSON array',
    suggested_difficulty DECIMAL(3,2) NULL COMMENT '推荐难度 P-value',
    reasoning VARCHAR(1024) NULL COMMENT '推荐理由',
    error_msg VARCHAR(2048) NULL,
    request_user VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_q_ai_task_question (question_uuid, status, created_at)
) COMMENT 'ocr-service 侧 AI 分析任务持久化';

CREATE TABLE IF NOT EXISTS q_question_ai_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_uuid CHAR(36) NOT NULL UNIQUE,
    question_uuid CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL COMMENT 'PENDING / SUCCESS / FAILED / APPLIED',
    suggested_tags TEXT NULL COMMENT '推荐标签 JSON array',
    suggested_difficulty DECIMAL(3,2) NULL COMMENT '推荐难度 P-value',
    reasoning VARCHAR(1024) NULL COMMENT '推荐理由',
    error_msg VARCHAR(2048) NULL,
    request_user VARCHAR(128) NOT NULL,
    applied_at DATETIME NULL COMMENT '用户应用推荐的时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_q_qai_task_question (question_uuid, status, created_at)
) COMMENT 'question-service 侧 AI 分析任务持久化';

-- ==================== Seed Data ====================

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

-- ── MAIN_GRADE real tags ──

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000201', 'MAIN_GRADE', 'GRADE_7', '七年级', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_GRADE' AND tag_code='GRADE_7');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000202', 'MAIN_GRADE', 'GRADE_8', '八年级', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_GRADE' AND tag_code='GRADE_8');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000203', 'MAIN_GRADE', 'GRADE_9', '九年级', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_GRADE' AND tag_code='GRADE_9');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000204', 'MAIN_GRADE', 'SENIOR_1', '高一', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_GRADE' AND tag_code='SENIOR_1');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000205', 'MAIN_GRADE', 'SENIOR_2', '高二', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_GRADE' AND tag_code='SENIOR_2');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000206', 'MAIN_GRADE', 'SENIOR_3', '高三', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_GRADE' AND tag_code='SENIOR_3');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000207', 'MAIN_GRADE', 'COLLEGE_1', '大一', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_GRADE' AND tag_code='COLLEGE_1');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000208', 'MAIN_GRADE', 'COLLEGE_2', '大二', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_GRADE' AND tag_code='COLLEGE_2');

-- ── MAIN_KNOWLEDGE real tags ──

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000301', 'MAIN_KNOWLEDGE', 'SETS_LOGIC', '集合与逻辑', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_KNOWLEDGE' AND tag_code='SETS_LOGIC');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000302', 'MAIN_KNOWLEDGE', 'FUNCTION', '函数', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_KNOWLEDGE' AND tag_code='FUNCTION');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000303', 'MAIN_KNOWLEDGE', 'EQUATION', '方程与不等式', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_KNOWLEDGE' AND tag_code='EQUATION');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000304', 'MAIN_KNOWLEDGE', 'TRIGONOMETRY', '三角函数', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_KNOWLEDGE' AND tag_code='TRIGONOMETRY');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000305', 'MAIN_KNOWLEDGE', 'SEQUENCE', '数列', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_KNOWLEDGE' AND tag_code='SEQUENCE');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000306', 'MAIN_KNOWLEDGE', 'VECTOR', '向量', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_KNOWLEDGE' AND tag_code='VECTOR');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000307', 'MAIN_KNOWLEDGE', 'SOLID_GEOMETRY', '立体几何', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_KNOWLEDGE' AND tag_code='SOLID_GEOMETRY');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000308', 'MAIN_KNOWLEDGE', 'ANALYTIC_GEOMETRY', '解析几何', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_KNOWLEDGE' AND tag_code='ANALYTIC_GEOMETRY');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000309', 'MAIN_KNOWLEDGE', 'PROBABILITY', '概率与统计', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_KNOWLEDGE' AND tag_code='PROBABILITY');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000310', 'MAIN_KNOWLEDGE', 'DERIVATIVE', '导数', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_KNOWLEDGE' AND tag_code='DERIVATIVE');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000311', 'MAIN_KNOWLEDGE', 'INTEGRAL', '积分', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_KNOWLEDGE' AND tag_code='INTEGRAL');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000312', 'MAIN_KNOWLEDGE', 'PLANE_GEOMETRY', '平面几何', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_KNOWLEDGE' AND tag_code='PLANE_GEOMETRY');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000313', 'MAIN_KNOWLEDGE', 'ALGEBRA', '代数', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_KNOWLEDGE' AND tag_code='ALGEBRA');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000314', 'MAIN_KNOWLEDGE', 'NUMBER_THEORY', '数论', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_KNOWLEDGE' AND tag_code='NUMBER_THEORY');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000315', 'MAIN_KNOWLEDGE', 'COMBINATORICS', '排列组合', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_KNOWLEDGE' AND tag_code='COMBINATORICS');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000316', 'MAIN_KNOWLEDGE', 'LINEAR_ALGEBRA', '线性代数', 'SYSTEM', ''
WHERE NOT EXISTS (SELECT 1 FROM q_tag WHERE scope='SYSTEM' AND owner_user='' AND category_code='MAIN_KNOWLEDGE' AND tag_code='LINEAR_ALGEBRA');

-- =====================================================================
-- 试卷自动解析 (Exam Auto-Parse) — 2026-03-06
-- =====================================================================

CREATE TABLE IF NOT EXISTS q_exam_parse_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_uuid       CHAR(36)     NOT NULL UNIQUE,
    owner_user      VARCHAR(128) NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    -- PENDING / OCR_PROCESSING / SPLITTING / GENERATING / SUCCESS / PARTIAL_FAILED / FAILED
    progress        TINYINT      NOT NULL DEFAULT 0,
    file_count      INT          NOT NULL DEFAULT 0,
    total_pages     INT          NOT NULL DEFAULT 0,
    question_count  INT          NOT NULL DEFAULT 0,
    has_answer_hint BOOLEAN      NOT NULL DEFAULT FALSE,
    error_msg       VARCHAR(2048)         NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ept_owner_user (owner_user),
    INDEX idx_ept_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS q_exam_parse_source_file (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_uuid       CHAR(36)     NOT NULL,
    file_index      INT          NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    file_type       VARCHAR(16)  NOT NULL,
    page_count      INT          NOT NULL DEFAULT 1,
    file_data       LONGTEXT     NOT NULL,
    ocr_status      VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    INDEX idx_epsf_task_uuid (task_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS q_exam_parse_question (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_uuid          CHAR(36)     NOT NULL,
    seq_no             INT          NOT NULL,
    question_type      VARCHAR(32)  NULL,
    raw_stem_text      LONGTEXT     NULL,
    stem_xml           LONGTEXT     NULL,
    raw_answer_text    LONGTEXT     NULL,
    answer_xml         LONGTEXT     NULL,
    stem_images_json   MEDIUMTEXT   NULL,
    answer_images_json MEDIUMTEXT   NULL,
    source_pages       VARCHAR(255) NULL,
    parse_error        TINYINT(1)   NOT NULL DEFAULT 0,
    question_uuid      CHAR(36)     NULL,
    confirm_status     VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    error_msg          VARCHAR(1024)         NULL,
    main_tags_json          TEXT     NULL     COMMENT '主标签 JSON: [{categoryCode,tagCode}]',
    secondary_tags_json     TEXT     NULL     COMMENT '副标签 JSON: ["tag1","tag2"]',
    difficulty              DECIMAL(3,2)  NULL COMMENT 'P-value difficulty 0.00-1.00',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_epq_task_uuid_seq (task_uuid, seq_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- NOTE: main_tags_json, secondary_tags_json, difficulty are already in the CREATE TABLE above.
-- The ALTER TABLE IF NOT EXISTS syntax is not supported in MySQL 8.4.
-- If upgrading from an older schema, run these manually:
--   ALTER TABLE q_exam_parse_question ADD COLUMN main_tags_json TEXT NULL AFTER error_msg;
--   ALTER TABLE q_exam_parse_question ADD COLUMN secondary_tags_json TEXT NULL AFTER main_tags_json;
--   ALTER TABLE q_exam_parse_question ADD COLUMN difficulty DECIMAL(3,2) NULL AFTER secondary_tags_json;

-- =====================================================================
-- 试卷组卷功能 (Exam Paper Compose) — 2026-03-09
-- =====================================================================

CREATE TABLE IF NOT EXISTS q_question_type (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    type_code   VARCHAR(64)  NOT NULL COMMENT '题型编码, e.g. SINGLE_CHOICE',
    type_label  VARCHAR(128) NOT NULL COMMENT '显示名, e.g. 单项选择题',
    owner_user  VARCHAR(128) NOT NULL DEFAULT '' COMMENT '空=系统预置, 非空=用户自定义',
    xml_hint    VARCHAR(64)  NULL     COMMENT 'XML 渲染提示: choices-single, choices-multi, blank, answer-area, etc.',
    sort_order  INT          NOT NULL DEFAULT 0,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_qt_code_owner (type_code, owner_user),
    INDEX idx_qt_owner_sort (owner_user, enabled, sort_order)
) COMMENT '题型配置（系统预置 + 用户自定义）';

CREATE TABLE IF NOT EXISTS q_exam_paper (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_uuid       CHAR(36)      NOT NULL UNIQUE,
    owner_user       VARCHAR(128)  NOT NULL,
    title            VARCHAR(512)  NOT NULL DEFAULT '未命名试卷',
    subtitle         VARCHAR(512)  NULL     COMMENT '副标题',
    description      TEXT          NULL     COMMENT '考试说明/注意事项',
    duration_minutes INT           NULL     DEFAULT 120,
    total_score      DECIMAL(6,1)  NOT NULL DEFAULT 0,
    status           VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED / ARCHIVED',
    deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ep_owner_status (owner_user, status, deleted, updated_at)
) COMMENT '试卷主表';

CREATE TABLE IF NOT EXISTS q_exam_section (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    section_uuid        CHAR(36)     NOT NULL UNIQUE,
    paper_id            BIGINT       NOT NULL,
    title               VARCHAR(255) NOT NULL COMMENT '大题标题',
    description         TEXT         NULL     COMMENT '大题说明',
    question_type_code  VARCHAR(64)  NULL     COMMENT '关联题型编码',
    default_score       DECIMAL(5,1) NOT NULL DEFAULT 5.0,
    sort_order          INT          NOT NULL DEFAULT 0,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_es_paper_order (paper_id, sort_order),
    CONSTRAINT fk_es_paper FOREIGN KEY (paper_id) REFERENCES q_exam_paper(id) ON DELETE CASCADE
) COMMENT '试卷大题（区块）';

CREATE TABLE IF NOT EXISTS q_exam_question (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    section_id      BIGINT       NOT NULL,
    question_id     BIGINT       NOT NULL,
    question_uuid   CHAR(36)     NOT NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    score           DECIMAL(5,1) NOT NULL DEFAULT 5.0,
    note            VARCHAR(512) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_eq_section_question (section_id, question_id),
    INDEX idx_eq_section_order (section_id, sort_order),
    INDEX idx_eq_question_uuid (question_uuid),
    CONSTRAINT fk_eq_section FOREIGN KEY (section_id) REFERENCES q_exam_section(id) ON DELETE CASCADE,
    CONSTRAINT fk_eq_question FOREIGN KEY (question_id) REFERENCES q_question(id)
) COMMENT '试卷题目关联';

INSERT INTO q_question_type (type_code, type_label, owner_user, xml_hint, sort_order) VALUES
('SINGLE_CHOICE',  '单项选择题', '', 'choices-single',  10),
('MULTI_CHOICE',   '多项选择题', '', 'choices-multi',   20),
('TRUE_FALSE',     '判断题',     '', 'choices-single',  30),
('FILL_BLANK',     '填空题',     '', 'blank',           40),
('SHORT_ANSWER',   '简答题',     '', 'answer-area',     50),
('CALCULATION',    '计算题',     '', 'answer-area',     60),
('PROOF',          '证明题',     '', 'answer-area',     70),
('ESSAY',          '论述题',     '', 'answer-area',     80),
('COMPREHENSIVE',  '综合题',     '', 'answer-area',     90),
('OTHER',          '其他',       '', NULL,              999)
ON DUPLICATE KEY UPDATE type_label = VALUES(type_label);

-- =====================================================================
-- 试题篮 (Question Basket / Cart) — 2026-03-10
-- =====================================================================

CREATE TABLE IF NOT EXISTS q_question_basket (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_user    VARCHAR(128) NOT NULL,
    question_id   BIGINT       NOT NULL,
    question_uuid CHAR(36)     NOT NULL,
    added_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_qb_owner_question (owner_user, question_id),
    INDEX idx_qb_owner_added (owner_user, added_at),
    CONSTRAINT fk_qb_question FOREIGN KEY (question_id) REFERENCES q_question(id)
) COMMENT '试题篮（购物车），每个用户一个篮';
