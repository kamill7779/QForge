-- V5: 试卷组卷功能 — 自定义题型 + 试卷 + 大题 + 选题
-- 2026-03-09

-- ─────────────────────────────────────────
-- 1. 自定义题型配置表
-- ─────────────────────────────────────────
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

-- ─────────────────────────────────────────
-- 2. 试卷主表
-- ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS q_exam_paper (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_uuid       CHAR(36)      NOT NULL UNIQUE,
    owner_user       VARCHAR(128)  NOT NULL,
    title            VARCHAR(512)  NOT NULL DEFAULT '未命名试卷',
    subtitle         VARCHAR(512)  NULL     COMMENT '副标题 e.g. 2026年春季期中考试',
    description      TEXT          NULL     COMMENT '考试说明/注意事项',
    duration_minutes INT           NULL     DEFAULT 120,
    total_score      DECIMAL(6,1)  NOT NULL DEFAULT 0,
    status           VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED / ARCHIVED',
    deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ep_owner_status (owner_user, status, deleted, updated_at)
) COMMENT '试卷主表';

-- ─────────────────────────────────────────
-- 3. 试卷大题（区块）
-- ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS q_exam_section (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    section_uuid        CHAR(36)     NOT NULL UNIQUE,
    paper_id            BIGINT       NOT NULL,
    title               VARCHAR(255) NOT NULL COMMENT '大题标题, e.g. 一、选择题',
    description         TEXT         NULL     COMMENT '大题说明, e.g. 每小题5分，共30分',
    question_type_code  VARCHAR(64)  NULL     COMMENT '关联题型编码（可为空，仅做分类辅助）',
    default_score       DECIMAL(5,1) NOT NULL DEFAULT 5.0 COMMENT '该大题下题目默认分值',
    sort_order          INT          NOT NULL DEFAULT 0,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_es_paper_order (paper_id, sort_order),
    CONSTRAINT fk_es_paper FOREIGN KEY (paper_id) REFERENCES q_exam_paper(id) ON DELETE CASCADE
) COMMENT '试卷大题（区块）';

-- ─────────────────────────────────────────
-- 4. 试卷题目关联
-- ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS q_exam_question (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    section_id      BIGINT       NOT NULL,
    question_id     BIGINT       NOT NULL,
    question_uuid   CHAR(36)     NOT NULL COMMENT '冗余, 方便查询导出',
    sort_order      INT          NOT NULL DEFAULT 0,
    score           DECIMAL(5,1) NOT NULL DEFAULT 5.0,
    note            VARCHAR(512) NULL     COMMENT '出题人备注',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_eq_section_question (section_id, question_id),
    INDEX idx_eq_section_order (section_id, sort_order),
    INDEX idx_eq_question_uuid (question_uuid),
    CONSTRAINT fk_eq_section FOREIGN KEY (section_id) REFERENCES q_exam_section(id) ON DELETE CASCADE,
    CONSTRAINT fk_eq_question FOREIGN KEY (question_id) REFERENCES q_question(id)
) COMMENT '试卷题目关联（大题内的题目）';

-- ─────────────────────────────────────────
-- 5. 系统预置题型
-- ─────────────────────────────────────────
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
