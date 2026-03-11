-- =====================================================================
-- 高考数学题库 Schema (gaokao-corpus / gaokao-analysis)
-- Usage:
--   mysql --default-character-set=utf8mb4 -h127.0.0.1 -P3306 -uqforge -pqforge qforge < backend/sql/gaokao-schema.sql
-- =====================================================================
SET NAMES utf8mb4;

-- =====================================================================
-- 第一层：录入草稿层 (Draft / Ingest)
-- =====================================================================

-- 5.1 录入会话
CREATE TABLE IF NOT EXISTS gk_ingest_session (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_uuid        CHAR(36)     NOT NULL UNIQUE,
    status              VARCHAR(32)  NOT NULL DEFAULT 'UPLOADED'
                        COMMENT 'UPLOADED / OCRING / SPLIT_READY / EDITING / ANALYZING / READY_TO_PUBLISH / PUBLISHED / FAILED',
    source_kind         VARCHAR(16)  NOT NULL DEFAULT 'PDF' COMMENT 'PDF / IMAGE_SET',
    subject_code        VARCHAR(16)  NOT NULL DEFAULT 'MATH',
    operator_user       VARCHAR(128) NOT NULL,
    paper_name_guess    VARCHAR(512) NULL     COMMENT 'OCR 初猜卷名',
    exam_year_guess     SMALLINT     NULL     COMMENT 'OCR 初猜年份',
    province_code_guess VARCHAR(32)  NULL     COMMENT 'OCR 初猜地区',
    error_msg           VARCHAR(2048) NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_gk_ingest_operator (operator_user, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='高考数学录入会话';

-- 5.2 录入源文件
CREATE TABLE IF NOT EXISTS gk_ingest_source_file (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_file_uuid    CHAR(36)     NOT NULL UNIQUE,
    session_id          BIGINT       NOT NULL,
    file_name           VARCHAR(255) NOT NULL,
    file_type           VARCHAR(16)  NOT NULL COMMENT 'PDF / PNG / JPG',
    storage_ref         VARCHAR(1024) NOT NULL COMMENT '文件存储引用 (COS URI / legacy local path)',
    page_count          INT          NOT NULL DEFAULT 1,
    checksum_sha256     CHAR(64)     NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_gk_isf_session (session_id),
    CONSTRAINT fk_gk_isf_session FOREIGN KEY (session_id) REFERENCES gk_ingest_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='录入源文件';

-- 5.3 OCR 页级结果
CREATE TABLE IF NOT EXISTS gk_ingest_ocr_page (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id          BIGINT       NOT NULL,
    source_file_id      BIGINT       NOT NULL,
    page_no             INT          NOT NULL,
    full_text           LONGTEXT     NULL,
    layout_json         JSON         NULL     COMMENT '版面块 JSON',
    formula_json        JSON         NULL     COMMENT '公式识别结果 JSON',
    page_image_ref      VARCHAR(1024) NULL    COMMENT '页图片引用',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_gk_iop_session_page (session_id, source_file_id, page_no),
    CONSTRAINT fk_gk_iop_session FOREIGN KEY (session_id) REFERENCES gk_ingest_session(id),
    CONSTRAINT fk_gk_iop_file    FOREIGN KEY (source_file_id) REFERENCES gk_ingest_source_file(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCR 页级原始结果';

-- 5.4 草稿试卷
CREATE TABLE IF NOT EXISTS gk_draft_paper (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_paper_uuid    CHAR(36)     NOT NULL UNIQUE,
    session_id          BIGINT       NOT NULL,
    paper_name          VARCHAR(512) NOT NULL DEFAULT '未命名试卷',
    paper_type_code     VARCHAR(32)  NULL     COMMENT '卷型: NATIONAL_A / NATIONAL_B / PROVINCE / MOCK 等',
    exam_year           SMALLINT     NULL,
    province_code       VARCHAR(32)  NULL,
    total_score         DECIMAL(6,1) NULL,
    duration_minutes    INT          NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'EDITING'
                        COMMENT 'EDITING / ANALYZING / READY_TO_PUBLISH',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gk_dp_session (session_id),
    CONSTRAINT fk_gk_dp_session FOREIGN KEY (session_id) REFERENCES gk_ingest_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='草稿试卷';

-- 5.5 草稿 section
CREATE TABLE IF NOT EXISTS gk_draft_section (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_section_uuid    CHAR(36)     NOT NULL UNIQUE,
    draft_paper_id        BIGINT       NOT NULL,
    section_code          VARCHAR(64)  NOT NULL COMMENT 'SINGLE_CHOICE / FILL_BLANK / SOLUTION',
    section_title         VARCHAR(255) NULL,
    sort_order            INT          NOT NULL DEFAULT 0,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_gk_ds_paper_order (draft_paper_id, sort_order),
    CONSTRAINT fk_gk_ds_paper FOREIGN KEY (draft_paper_id) REFERENCES gk_draft_paper(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='草稿试卷 section';

-- 5.6 草稿题
CREATE TABLE IF NOT EXISTS gk_draft_question (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_question_uuid   CHAR(36)     NOT NULL UNIQUE,
    draft_paper_id        BIGINT       NOT NULL,
    draft_section_id      BIGINT       NULL,
    parent_question_id    BIGINT       NULL     COMMENT '父题 ID (复合题)',
    root_question_id      BIGINT       NULL     COMMENT '根题 ID',
    question_no           VARCHAR(16)  NULL     COMMENT '题号',
    question_type_code    VARCHAR(64)  NULL     COMMENT '题型',
    answer_mode           VARCHAR(32)  NULL     COMMENT 'OBJECTIVE / SUBJECTIVE / COMPOSITE',
    stem_text             LONGTEXT     NULL     COMMENT 'OCR 原始题干',
    stem_xml              LONGTEXT     NULL     COMMENT '清洗后 XML',
    normalized_stem_text  LONGTEXT     NULL     COMMENT '规范化文本',
    score                 DECIMAL(5,1) NULL,
    has_answer            BOOLEAN      NOT NULL DEFAULT FALSE,
    edit_version          INT          NOT NULL DEFAULT 1 COMMENT '前端编辑版本',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_gk_dq_paper (draft_paper_id),
    INDEX idx_gk_dq_section (draft_section_id),
    INDEX idx_gk_dq_parent (parent_question_id),
    CONSTRAINT fk_gk_dq_paper   FOREIGN KEY (draft_paper_id) REFERENCES gk_draft_paper(id) ON DELETE CASCADE,
    CONSTRAINT fk_gk_dq_section FOREIGN KEY (draft_section_id) REFERENCES gk_draft_section(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='草稿题主表';

-- 5.7 草稿选项
CREATE TABLE IF NOT EXISTS gk_draft_option (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_option_uuid     CHAR(36)     NOT NULL UNIQUE,
    draft_question_id     BIGINT       NOT NULL,
    option_label          VARCHAR(8)   NOT NULL COMMENT 'A / B / C / D',
    option_text           LONGTEXT     NULL,
    option_xml            LONGTEXT     NULL,
    sort_order            INT          NOT NULL DEFAULT 0,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_gk_do_question (draft_question_id, sort_order),
    CONSTRAINT fk_gk_do_question FOREIGN KEY (draft_question_id) REFERENCES gk_draft_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='草稿选项';

-- 5.8 草稿答案
CREATE TABLE IF NOT EXISTS gk_draft_answer (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_answer_uuid     CHAR(36)     NOT NULL UNIQUE,
    draft_question_id     BIGINT       NOT NULL,
    answer_type           VARCHAR(32)  NOT NULL COMMENT 'OFFICIAL / STEP / ALTERNATIVE / CHOICE_KEY',
    answer_text           LONGTEXT     NULL,
    answer_xml            LONGTEXT     NULL,
    is_official           BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order            INT          NOT NULL DEFAULT 0,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_gk_da_question (draft_question_id, sort_order),
    CONSTRAINT fk_gk_da_question FOREIGN KEY (draft_question_id) REFERENCES gk_draft_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='草稿答案';

-- 5.9 草稿题干资源
CREATE TABLE IF NOT EXISTS gk_draft_question_asset (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_question_id     BIGINT       NOT NULL,
    asset_type            VARCHAR(32)  NOT NULL COMMENT 'IMAGE / FORMULA_IMAGE / REGION_CROP',
    storage_ref           VARCHAR(1024) NOT NULL,
    sort_order            INT          NOT NULL DEFAULT 0,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_gk_dqa_question (draft_question_id, sort_order),
    CONSTRAINT fk_gk_dqa_question FOREIGN KEY (draft_question_id) REFERENCES gk_draft_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='草稿题干图片';

-- 5.10 草稿答案资源
CREATE TABLE IF NOT EXISTS gk_draft_answer_asset (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_answer_id       BIGINT       NOT NULL,
    asset_type            VARCHAR(32)  NOT NULL COMMENT 'IMAGE / FORMULA_IMAGE',
    storage_ref           VARCHAR(1024) NOT NULL,
    sort_order            INT          NOT NULL DEFAULT 0,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_gk_daa_answer (draft_answer_id, sort_order),
    CONSTRAINT fk_gk_daa_answer FOREIGN KEY (draft_answer_id) REFERENCES gk_draft_answer(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='草稿答案图片';

-- 5.11 AI 分析预览
CREATE TABLE IF NOT EXISTS gk_draft_profile_preview (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_question_id     BIGINT       NOT NULL,
    knowledge_tags_json   JSON         NULL COMMENT '知识点',
    method_tags_json      JSON         NULL COMMENT '解法标签',
    formula_tags_json     JSON         NULL COMMENT '关键公式',
    mistake_tags_json     JSON         NULL COMMENT '易错点',
    ability_tags_json     JSON         NULL COMMENT '能力标签',
    difficulty_score      DECIMAL(3,2) NULL COMMENT '难度值 0.00-1.00',
    difficulty_level      VARCHAR(16)  NULL COMMENT '难度层级: EASY / MEDIUM / HARD / VERY_HARD',
    reasoning_steps_json  JSON         NULL COMMENT '推理步数/步骤',
    analysis_summary_text LONGTEXT     NULL COMMENT '解析摘要',
    recommend_seed_text   LONGTEXT     NULL COMMENT '用于后续向量化的联合语料',
    profile_version       INT          NOT NULL DEFAULT 1,
    confirmed             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_gk_dpp_question (draft_question_id, confirmed),
    CONSTRAINT fk_gk_dpp_question FOREIGN KEY (draft_question_id) REFERENCES gk_draft_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 分析预览 (前端确认前)';

-- =====================================================================
-- 第二层：高考数学正式语料层
-- =====================================================================

-- 6.1 正式试卷
CREATE TABLE IF NOT EXISTS gk_paper (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_uuid            CHAR(36)     NOT NULL UNIQUE,
    source_session_uuid   CHAR(36)     NULL     COMMENT '来源录入会话 UUID',
    draft_paper_id        BIGINT       NULL     COMMENT '来源草稿试卷 ID, 用于发布幂等',
    paper_name            VARCHAR(512) NOT NULL,
    paper_type_code       VARCHAR(32)  NULL,
    exam_year             SMALLINT     NULL,
    province_code         VARCHAR(32)  NULL,
    subject_code          VARCHAR(16)  NOT NULL DEFAULT 'MATH',
    status                VARCHAR(16)  NOT NULL DEFAULT 'READY' COMMENT 'READY / ARCHIVED',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gk_paper_draft (draft_paper_id),
    INDEX idx_gk_paper_year (exam_year, province_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='高考数学正式试卷';

-- 6.2 正式 section
CREATE TABLE IF NOT EXISTS gk_paper_section (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    section_uuid          CHAR(36)     NOT NULL UNIQUE,
    paper_id              BIGINT       NOT NULL,
    section_code          VARCHAR(64)  NOT NULL,
    section_title         VARCHAR(255) NULL,
    sort_order            INT          NOT NULL DEFAULT 0,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_gk_ps_paper (paper_id, sort_order),
    CONSTRAINT fk_gk_ps_paper FOREIGN KEY (paper_id) REFERENCES gk_paper(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='正式试卷 section';

-- 6.3 正式题目
CREATE TABLE IF NOT EXISTS gk_question (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_uuid         CHAR(36)     NOT NULL UNIQUE,
    paper_id              BIGINT       NOT NULL,
    section_id            BIGINT       NULL,
    parent_question_id    BIGINT       NULL,
    root_question_id      BIGINT       NULL,
    question_no           VARCHAR(16)  NULL,
    question_type_code    VARCHAR(64)  NULL,
    answer_mode           VARCHAR(32)  NULL,
    stem_text             LONGTEXT     NULL,
    stem_xml              LONGTEXT     NULL,
    normalized_stem_text  LONGTEXT     NULL,
    score                 DECIMAL(5,1) NULL,
    difficulty_score      DECIMAL(3,2) NULL,
    difficulty_level      VARCHAR(16)  NULL,
    reasoning_step_count  INT          NULL,
    has_answer            BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at          DATETIME     NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_gk_q_paper (paper_id),
    INDEX idx_gk_q_section (section_id),
    INDEX idx_gk_q_type_diff (question_type_code, difficulty_level),
    CONSTRAINT fk_gk_q_paper   FOREIGN KEY (paper_id) REFERENCES gk_paper(id),
    CONSTRAINT fk_gk_q_section FOREIGN KEY (section_id) REFERENCES gk_paper_section(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='高考数学正式题目';

-- 6.4 正式选项
CREATE TABLE IF NOT EXISTS gk_question_option (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    option_uuid           CHAR(36)     NOT NULL UNIQUE,
    question_id           BIGINT       NOT NULL,
    option_label          VARCHAR(8)   NOT NULL,
    option_text           LONGTEXT     NULL,
    option_xml            LONGTEXT     NULL,
    is_correct            BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order            INT          NOT NULL DEFAULT 0,
    INDEX idx_gk_qo_question (question_id, sort_order),
    CONSTRAINT fk_gk_qo_question FOREIGN KEY (question_id) REFERENCES gk_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='正式选项';

-- 6.5 正式答案
CREATE TABLE IF NOT EXISTS gk_question_answer (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    answer_uuid           CHAR(36)     NOT NULL UNIQUE,
    question_id           BIGINT       NOT NULL,
    answer_type           VARCHAR(32)  NOT NULL,
    answer_text           LONGTEXT     NULL,
    answer_xml            LONGTEXT     NULL,
    is_official           BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order            INT          NOT NULL DEFAULT 0,
    INDEX idx_gk_qa_question (question_id, sort_order),
    CONSTRAINT fk_gk_qa_question FOREIGN KEY (question_id) REFERENCES gk_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='正式答案';

-- 6.6 正式题干资源
CREATE TABLE IF NOT EXISTS gk_question_asset (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_id           BIGINT       NOT NULL,
    asset_type            VARCHAR(32)  NOT NULL,
    storage_ref           VARCHAR(1024) NOT NULL,
    sort_order            INT          NOT NULL DEFAULT 0,
    INDEX idx_gk_qas_question (question_id, sort_order),
    CONSTRAINT fk_gk_qas_question FOREIGN KEY (question_id) REFERENCES gk_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='正式题干资源';

-- 6.7 正式答案资源
CREATE TABLE IF NOT EXISTS gk_answer_asset (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    answer_id             BIGINT       NOT NULL,
    asset_type            VARCHAR(32)  NOT NULL,
    storage_ref           VARCHAR(1024) NOT NULL,
    sort_order            INT          NOT NULL DEFAULT 0,
    INDEX idx_gk_aas_answer (answer_id, sort_order),
    CONSTRAINT fk_gk_aas_answer FOREIGN KEY (answer_id) REFERENCES gk_question_answer(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='正式答案资源';

-- 6.8 正式深分析结果
CREATE TABLE IF NOT EXISTS gk_question_profile (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_id           BIGINT       NOT NULL UNIQUE,
    knowledge_path_json   JSON         NULL,
    method_tags_json      JSON         NULL,
    ability_tags_json     JSON         NULL,
    mistake_tags_json     JSON         NULL,
    formula_tags_json     JSON         NULL,
    answer_structure_json JSON         NULL,
    reasoning_steps_json  JSON         NULL,
    analysis_summary_text LONGTEXT     NULL,
    solve_path_text       LONGTEXT     NULL,
    difficulty_score      DECIMAL(3,2) NULL,
    difficulty_level      VARCHAR(16)  NULL,
    profile_version       INT          NOT NULL DEFAULT 1,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_gk_qp_question FOREIGN KEY (question_id) REFERENCES gk_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='高考数学深分析结果';

-- 6.9 数学标签树
CREATE TABLE IF NOT EXISTS gk_taxonomy_node (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    node_uuid             CHAR(36)     NOT NULL UNIQUE,
    taxonomy_code         VARCHAR(32)  NOT NULL COMMENT 'KNOWLEDGE / METHOD / ABILITY / MISTAKE / FORMULA / QUESTION_TYPE',
    parent_node_id        BIGINT       NULL,
    node_code             VARCHAR(128) NOT NULL,
    node_name             VARCHAR(255) NOT NULL,
    node_path             VARCHAR(1024) NULL   COMMENT '冗余全路径, e.g. /KNOWLEDGE/函数/导数',
    sort_order            INT          NOT NULL DEFAULT 0,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gk_tn_taxonomy_code (taxonomy_code, node_code),
    INDEX idx_gk_tn_parent (parent_node_id),
    INDEX idx_gk_tn_taxonomy (taxonomy_code, enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数学标签树';

-- 6.10 题目-标签关系
CREATE TABLE IF NOT EXISTS gk_question_taxonomy_rel (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_id           BIGINT       NOT NULL,
    node_id               BIGINT       NOT NULL,
    taxonomy_code         VARCHAR(32)  NOT NULL,
    source_kind           VARCHAR(16)  NOT NULL DEFAULT 'AI' COMMENT 'AI / MANUAL',
    confidence            DECIMAL(3,2) NULL,
    is_primary            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gk_qtr (question_id, node_id),
    INDEX idx_gk_qtr_node (node_id),
    INDEX idx_gk_qtr_taxonomy (taxonomy_code, question_id),
    CONSTRAINT fk_gk_qtr_question FOREIGN KEY (question_id) REFERENCES gk_question(id) ON DELETE CASCADE,
    CONSTRAINT fk_gk_qtr_node     FOREIGN KEY (node_id)     REFERENCES gk_taxonomy_node(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目-标签关系';

-- 6.11 RAG chunk
CREATE TABLE IF NOT EXISTS gk_rag_chunk (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    chunk_uuid            CHAR(36)     NOT NULL UNIQUE,
    question_id           BIGINT       NOT NULL,
    chunk_type            VARCHAR(32)  NOT NULL COMMENT 'STEM / ANSWER / ANALYSIS_SUMMARY / METHOD_NOTE / MISTAKE_NOTE / FORMULA_NOTE / STEP_NOTE',
    chunk_text            LONGTEXT     NOT NULL,
    token_count           INT          NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_gk_rc_question (question_id, chunk_type),
    CONSTRAINT fk_gk_rc_question FOREIGN KEY (question_id) REFERENCES gk_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 结构化 chunk';

-- 6.12 Qdrant 点位映射
CREATE TABLE IF NOT EXISTS gk_vector_point (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    target_type           VARCHAR(32)  NOT NULL COMMENT 'QUESTION / RAG_CHUNK',
    target_id             BIGINT       NOT NULL,
    vector_kind           VARCHAR(32)  NOT NULL COMMENT 'stem / analysis / joint / chunk',
    collection_name       VARCHAR(128) NOT NULL,
    qdrant_point_id       VARCHAR(128) NOT NULL COMMENT 'Qdrant UUID',
    payload_json          JSON         NULL,
    status                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DELETED',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gk_vp_target (target_type, target_id, vector_kind),
    INDEX idx_gk_vp_qdrant (collection_name, qdrant_point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Qdrant 点位映射';

-- 6.13 推荐边
CREATE TABLE IF NOT EXISTS gk_recommend_edge (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_question_id    BIGINT       NOT NULL,
    target_question_id    BIGINT       NOT NULL,
    relation_type         VARCHAR(32)  NOT NULL COMMENT 'SAME_CLASS / VARIANT / ADVANCED / MISTAKE / GENERAL',
    score                 DECIMAL(5,4) NULL     COMMENT '相似度分数',
    computed_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gk_re (source_question_id, target_question_id, relation_type),
    INDEX idx_gk_re_target (target_question_id),
    CONSTRAINT fk_gk_re_source FOREIGN KEY (source_question_id) REFERENCES gk_question(id) ON DELETE CASCADE,
    CONSTRAINT fk_gk_re_target FOREIGN KEY (target_question_id) REFERENCES gk_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离线推荐边';

-- 6.14 物化桥接记录
CREATE TABLE IF NOT EXISTS gk_question_materialization (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    gk_question_id        BIGINT       NOT NULL,
    target_question_uuid  CHAR(36)     NOT NULL COMMENT '物化到 question-core-service 的 UUID',
    owner_user            VARCHAR(128) NOT NULL,
    mode                  VARCHAR(16)  NOT NULL DEFAULT 'COPY' COMMENT 'COPY / LINK',
    status                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / REVOKED',
    source_hash           CHAR(64)     NULL     COMMENT '来源数据摘要, 用于检测更新',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gk_qm_gk_target (gk_question_id, target_question_uuid),
    UNIQUE KEY uk_gk_qm_active_source (gk_question_id, owner_user, mode, status, source_hash),
    INDEX idx_gk_qm_target (target_question_uuid),
    CONSTRAINT fk_gk_qm_question FOREIGN KEY (gk_question_id) REFERENCES gk_question(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='高考题物化到正式题库的桥接';

-- =====================================================================
-- 高考数学标签树种子数据
-- =====================================================================

-- 知识点
INSERT INTO gk_taxonomy_node (node_uuid, taxonomy_code, parent_node_id, node_code, node_name, node_path, sort_order) VALUES
('10000000-0000-0000-0000-000000000001', 'KNOWLEDGE', NULL, 'SETS_LOGIC',        '集合与逻辑',     '/KNOWLEDGE/集合与逻辑',      10),
('10000000-0000-0000-0000-000000000002', 'KNOWLEDGE', NULL, 'FUNCTION',           '函数',           '/KNOWLEDGE/函数',            20),
('10000000-0000-0000-0000-000000000003', 'KNOWLEDGE', NULL, 'TRIGONOMETRY',       '三角函数',       '/KNOWLEDGE/三角函数',        30),
('10000000-0000-0000-0000-000000000004', 'KNOWLEDGE', NULL, 'SEQUENCE',           '数列',           '/KNOWLEDGE/数列',            40),
('10000000-0000-0000-0000-000000000005', 'KNOWLEDGE', NULL, 'EQUATION',           '方程与不等式',   '/KNOWLEDGE/方程与不等式',    50),
('10000000-0000-0000-0000-000000000006', 'KNOWLEDGE', NULL, 'VECTOR',             '向量',           '/KNOWLEDGE/向量',            60),
('10000000-0000-0000-0000-000000000007', 'KNOWLEDGE', NULL, 'SOLID_GEOMETRY',     '立体几何',       '/KNOWLEDGE/立体几何',        70),
('10000000-0000-0000-0000-000000000008', 'KNOWLEDGE', NULL, 'ANALYTIC_GEOMETRY',  '解析几何',       '/KNOWLEDGE/解析几何',        80),
('10000000-0000-0000-0000-000000000009', 'KNOWLEDGE', NULL, 'PROBABILITY',        '概率与统计',     '/KNOWLEDGE/概率与统计',      90),
('10000000-0000-0000-0000-000000000010', 'KNOWLEDGE', NULL, 'DERIVATIVE',         '导数',           '/KNOWLEDGE/导数',           100),
('10000000-0000-0000-0000-000000000011', 'KNOWLEDGE', NULL, 'COMBINATORICS',      '排列组合',       '/KNOWLEDGE/排列组合',       110),
('10000000-0000-0000-0000-000000000012', 'KNOWLEDGE', NULL, 'COMPLEX_NUMBER',     '复数',           '/KNOWLEDGE/复数',           120)
ON DUPLICATE KEY UPDATE node_name = VALUES(node_name);

-- 解法标签
INSERT INTO gk_taxonomy_node (node_uuid, taxonomy_code, parent_node_id, node_code, node_name, node_path, sort_order) VALUES
('20000000-0000-0000-0000-000000000001', 'METHOD', NULL, 'SUBSTITUTION',     '换元法',       '/METHOD/换元法',       10),
('20000000-0000-0000-0000-000000000002', 'METHOD', NULL, 'ELIMINATION',      '消元法',       '/METHOD/消元法',       20),
('20000000-0000-0000-0000-000000000003', 'METHOD', NULL, 'SPECIAL_VALUE',    '特值法',       '/METHOD/特值法',       30),
('20000000-0000-0000-0000-000000000004', 'METHOD', NULL, 'GRAPHICAL',        '图解法',       '/METHOD/图解法',       40),
('20000000-0000-0000-0000-000000000005', 'METHOD', NULL, 'INDUCTION',        '归纳法',       '/METHOD/归纳法',       50),
('20000000-0000-0000-0000-000000000006', 'METHOD', NULL, 'PROOF_CONTRADICTION', '反证法',    '/METHOD/反证法',       60),
('20000000-0000-0000-0000-000000000007', 'METHOD', NULL, 'COORDINATE',       '坐标法',       '/METHOD/坐标法',       70),
('20000000-0000-0000-0000-000000000008', 'METHOD', NULL, 'PARAMETRIC',       '参数法',       '/METHOD/参数法',       80)
ON DUPLICATE KEY UPDATE node_name = VALUES(node_name);

-- 能力标签
INSERT INTO gk_taxonomy_node (node_uuid, taxonomy_code, parent_node_id, node_code, node_name, node_path, sort_order) VALUES
('30000000-0000-0000-0000-000000000001', 'ABILITY', NULL, 'COMPUTATION',    '运算能力',     '/ABILITY/运算能力',     10),
('30000000-0000-0000-0000-000000000002', 'ABILITY', NULL, 'LOGIC',          '逻辑推理',     '/ABILITY/逻辑推理',     20),
('30000000-0000-0000-0000-000000000003', 'ABILITY', NULL, 'SPATIAL',        '空间想象',     '/ABILITY/空间想象',     30),
('30000000-0000-0000-0000-000000000004', 'ABILITY', NULL, 'MODELING',       '数学建模',     '/ABILITY/数学建模',     40),
('30000000-0000-0000-0000-000000000005', 'ABILITY', NULL, 'DATA_ANALYSIS',  '数据分析',     '/ABILITY/数据分析',     50)
ON DUPLICATE KEY UPDATE node_name = VALUES(node_name);

-- 题型
INSERT INTO gk_taxonomy_node (node_uuid, taxonomy_code, parent_node_id, node_code, node_name, node_path, sort_order) VALUES
('60000000-0000-0000-0000-000000000001', 'QUESTION_TYPE', NULL, 'SINGLE_CHOICE',   '单选题',   '/QUESTION_TYPE/单选题',    10),
('60000000-0000-0000-0000-000000000002', 'QUESTION_TYPE', NULL, 'MULTI_CHOICE',    '多选题',   '/QUESTION_TYPE/多选题',    20),
('60000000-0000-0000-0000-000000000003', 'QUESTION_TYPE', NULL, 'FILL_BLANK',      '填空题',   '/QUESTION_TYPE/填空题',    30),
('60000000-0000-0000-0000-000000000004', 'QUESTION_TYPE', NULL, 'SOLUTION',        '解答题',   '/QUESTION_TYPE/解答题',    40)
ON DUPLICATE KEY UPDATE node_name = VALUES(node_name);
