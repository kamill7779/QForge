# QForge 褰撳墠鐪熷疄鏁版嵁搴撹〃缁撴瀯

**鏇存柊鏃堕棿**: 2026-03-04  
**鍒嗘敮**: `feat/xml-stem-storage-schema`  
**鏁版嵁搴?*: MySQL 8.4锛坰chema: `qforge`锛?

## 1. 鐪熷疄鎬ф牎楠屼緷鎹?

浠ヤ笅缁撴瀯涓嶆槸鈥滆璁＄鈥濓紝鑰屾槸鎸夊綋鍓嶄粨搴撳彲杩愯浠ｇ爜鍙嶅悜杩樺師鍚庣殑鐪熷疄缁撴瀯锛?

1. 涓诲垵濮嬪寲鑴氭湰锛歚backend/sql/init-schema.sql`锛坄backend/README.md` 鏄庣‘褰撳墠鎵嬪伐鍒濆鍖栵紝Flyway 涓嶄綔涓鸿繍琛屽叆鍙ｏ級銆?
2. 鏈嶅姟杩佺Щ鑴氭湰锛?
   - `backend/services/auth-service/src/main/resources/db/migration/V1__init_auth.sql`
   - `backend/services/question-service/src/main/resources/db/migration/V1__init_question_bank.sql`
   - `backend/services/question-service/src/main/resources/db/migration/V2__xml_stem_storage.sql`
   - `backend/services/question-service/src/main/resources/db/migration/V3__alter_difficulty_to_decimal.sql`
   - `backend/services/question-service/src/main/resources/db/migration/V4__answer_asset_and_answer_ocr_xml.sql`
   - `backend/services/ocr-service/src/main/resources/db/migration/V1__init_ocr.sql`
3. 瀹炰綋涓庝粨鍌?涓氬姟浠ｇ爜锛?
   - `auth-service` JPA 瀹炰綋 `UserAccount`
   - `question-service` MyBatis-Plus 瀹炰綋涓庝粨鍌?
   - `ocr-service` MyBatis-Plus 瀹炰綋涓庝粨鍌?

## 2. 褰撳墠鐪熷疄寤鸿〃 SQL锛堝彲鐩存帴鎵ц锛?

> 涓?`backend/sql/init-schema.sql` 瀵归綈銆?

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
    stem_image_id BIGINT NULL COMMENT '棰樺共閰嶅浘锛屾寚鍚?q_question_asset.id',
    status VARCHAR(32) NOT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
    difficulty DECIMAL(3,2) NULL COMMENT 'P-value difficulty coefficient 0.00-1.00',
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '閫昏緫鍒犻櫎鏍囪',
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
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '閫昏緫鍒犻櫎鏍囪',
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
    ref_key     VARCHAR(64)   NULL      COMMENT '鍓嶇 XML 寮曠敤 key锛屽 img-1銆乮mg-2',
    image_data  MEDIUMTEXT    NOT NULL  COMMENT '鍥剧墖 base64 缂栫爜鏁版嵁锛堟瘡寮?鈮?40KB锛?,
    file_name   VARCHAR(255)  NULL      COMMENT '鍘熷鏂囦欢鍚?,
    mime_type   VARCHAR(128)  NULL      COMMENT 'image/png, image/jpeg 绛?,
    deleted     BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '閫昏緫鍒犻櫎鏍囪',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_q_asset_question     (question_id, asset_type),
    INDEX idx_q_asset_question_ref (question_id, ref_key),
    CONSTRAINT fk_q_asset_question FOREIGN KEY (question_id) REFERENCES q_question(id)
) COMMENT '棰樼洰鍏宠仈璧勬簮锛堝浘鐗?base64锛夛紝姣忛鏈€澶?10 寮狅紝姣忓紶鏈€澶?512KB';

CREATE TABLE IF NOT EXISTS q_answer_asset (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    asset_uuid  CHAR(36)      NOT NULL UNIQUE,
    question_id BIGINT        NOT NULL,
    answer_id   BIGINT        NOT NULL,
    ref_key     VARCHAR(64)   NOT NULL COMMENT '绛旀 XML 寮曠敤 key锛屽 a92f6c03-img-1',
    image_data  MEDIUMTEXT    NOT NULL COMMENT '鍥剧墖 base64 缂栫爜鏁版嵁',
    mime_type   VARCHAR(128)  NULL COMMENT 'image/png, image/jpeg 绛?,
    deleted     BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '閫昏緫鍒犻櫎鏍囪',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_q_answer_asset_answer_ref (answer_id, ref_key),
    INDEX idx_q_answer_asset_question (question_id, answer_id),
    CONSTRAINT fk_q_answer_asset_question FOREIGN KEY (question_id) REFERENCES q_question(id),
    CONSTRAINT fk_q_answer_asset_answer FOREIGN KEY (answer_id) REFERENCES q_answer(id)
) COMMENT '绛旀鍏宠仈璧勬簮锛堝浘鐗?base64锛?;

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
```

## 3. 褰撳墠鐪熷疄鍒濆鍖栨暟鎹紙绉嶅瓙鏁版嵁锛?

```sql
INSERT INTO user_account (username, password, enabled)
SELECT 'admin', '{noop}admin123', TRUE
WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE username = 'admin');

INSERT INTO q_tag_category (category_code, category_name, category_kind, input_mode, allow_user_create, sort_order, enabled)
SELECT 'MAIN_GRADE', '骞寸骇', 'MAIN', 'SELECT', FALSE, 10, TRUE
WHERE NOT EXISTS (SELECT 1 FROM q_tag_category WHERE category_code = 'MAIN_GRADE');

INSERT INTO q_tag_category (category_code, category_name, category_kind, input_mode, allow_user_create, sort_order, enabled)
SELECT 'MAIN_KNOWLEDGE', '鐭ヨ瘑鐐?, 'MAIN', 'SELECT', FALSE, 20, TRUE
WHERE NOT EXISTS (SELECT 1 FROM q_tag_category WHERE category_code = 'MAIN_KNOWLEDGE');

INSERT INTO q_tag_category (category_code, category_name, category_kind, input_mode, allow_user_create, sort_order, enabled)
SELECT 'SECONDARY_CUSTOM', '鍓爣绛?, 'SECONDARY', 'FREE_TEXT', TRUE, 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM q_tag_category WHERE category_code = 'SECONDARY_CUSTOM');

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000101', 'MAIN_GRADE', 'UNCATEGORIZED', '鏈垎绫?, 'SYSTEM', ''
WHERE NOT EXISTS (
    SELECT 1 FROM q_tag
    WHERE scope = 'SYSTEM' AND owner_user = '' AND category_code = 'MAIN_GRADE' AND tag_code = 'UNCATEGORIZED'
);

INSERT INTO q_tag (tag_uuid, category_code, tag_code, tag_name, scope, owner_user)
SELECT '00000000-0000-0000-0000-000000000102', 'MAIN_KNOWLEDGE', 'UNCATEGORIZED', '鏈垎绫?, 'SYSTEM', ''
WHERE NOT EXISTS (
    SELECT 1 FROM q_tag
    WHERE scope = 'SYSTEM' AND owner_user = '' AND category_code = 'MAIN_KNOWLEDGE' AND tag_code = 'UNCATEGORIZED'
);
```

## 4. 浠ｇ爜灞傜湡瀹炴灇涓?鐘舵€佸€硷紙鍙嶅悜杩樺師锛?

> 杩欎簺鍊兼潵鑷綋鍓嶆湇鍔′唬鐮佷腑鐨勫啓鍏?鍒ゆ柇閫昏緫锛屼笉鏄璁￠鐣欏€笺€?

- `q_question.status`锛歚DRAFT`銆乣READY`
- `q_question.visibility`锛氶粯璁?`PRIVATE`
- `q_answer.answer_type`锛氬綋鍓嶅浐瀹氬啓鍏?`LATEX_TEXT`
- `q_tag.scope`锛歚SYSTEM`銆乣USER`
- `q_tag_category.category_kind`锛歚MAIN`銆乣SECONDARY`
- `q_tag_category.input_mode`锛歚SELECT`銆乣FREE_TEXT`
- `q_question_ocr_task.biz_type`锛歚QUESTION_STEM`銆乣ANSWER_CONTENT`
- `q_question_ocr_task.status`锛歚PENDING`銆乣PROCESSING`銆乣CONFIRMED`銆乣FAILED`
- `q_ocr_task.status`锛歚PENDING`銆乣PROCESSING`銆乣SUCCESS`銆乣FAILED`
- `q_ocr_task.provider`锛氬綋鍓嶅浐瀹氬啓鍏?`GLM_OCR`
- `q_answer_asset.ref_key`锛氬缓璁娇鐢?`a{task8}-img-N` 椋庢牸锛屼緵绛旀 XML `<image ref="..."/>` 鐩存帴寮曠敤
- `ANSWER_CONTENT` 鍚岄骞跺彂绾︽潫锛氱敱 Redis 閿?`ocr:answer:guard:{questionUuid}` + DB 鐘舵€佽仈鍚堜繚璇侊紝鍚岄鍚屾椂浠呭厑璁?1 涓繘琛屼腑鐨勭瓟妗?OCR 浠诲姟

## 5. 琛ㄥ叧绯伙紙褰撳墠鐪熷疄 FK锛?

- `q_tag.category_code` -> `q_tag_category.category_code`
- `q_answer.question_id` -> `q_question.id`
- `q_question.stem_image_id` -> `q_question_asset.id`锛圤N DELETE SET NULL锛?- `q_question_asset.question_id` -> `q_question.id`
- `q_answer_asset.question_id` -> `q_question.id`
- `q_answer_asset.answer_id` -> `q_answer.id`
- `q_question_tag_rel.question_id` -> `q_question.id`
- `q_question_tag_rel.tag_id` -> `q_tag.id`
- `q_question_tag_rel.category_code` -> `q_tag_category.category_code`

## 6. 浣跨敤寤鸿

鏈」鐩綋鍓嶄富鍒嗘敮鏁版嵁搴撳垵濮嬪寲寤鸿浠?`backend/sql/init-schema.sql` 涓哄噯锛涙湰鏂囦欢浣滀负闈㈠悜鐮斿彂涓庤仈璋冪殑鈥滃彲璇荤増鐪熷疄缁撴瀯鏂囨。鈥濓紝涓庝富鑴氭湰鍚屾缁存姢銆?

## 7. 涓枃涔辩爜鎺掓煡涓庝慨澶嶏紙閲嶈锛?
### 鐜拌薄
- `q_tag_category.category_name`銆乣q_tag.tag_name` 绛変腑鏂囧瓧娈垫樉绀轰负涔辩爜鎴?`??`銆?
### 鏍瑰洜
- 鍦?Windows PowerShell 涓娇鐢?`Get-Content ... | docker exec ... mysql` 瀵煎叆 SQL 鏃讹紝鍙兘鍙戠敓鎺у埗鍙扮紪鐮佽浆鎹紝瀵艰嚧 UTF-8 涓枃鍦ㄨ繘鍏?MySQL 鍓嶈鐮村潖銆?
### 鎺ㄨ崘瀵煎叆鏂瑰紡锛堥伩鍏嶄贡鐮侊級
1. 鍏堟妸 SQL 鏂囦欢澶嶅埗杩?MySQL 瀹瑰櫒锛堜繚鐣欏師濮?UTF-8 瀛楄妭锛夈€?2. 鍦ㄥ鍣ㄥ唴鐢ㄩ噸瀹氬悜鎵ц锛屽苟鏄惧紡鎸囧畾瀛楃闆?`utf8mb4`銆?
```bash
# 鍦?backend 鐩綍鎵ц
cd backend

docker cp sql/init-schema.sql qforge-mysql:/tmp/init-schema.sql
docker exec qforge-mysql sh -c "mysql --default-character-set=utf8mb4 -uroot -proot qforge < /tmp/init-schema.sql"
```

### 蹇€熸牎楠岋紙鎺ㄨ崘鐢?HEX锛?```sql
SELECT category_code, HEX(category_name) FROM q_tag_category;
SELECT category_code, tag_code, HEX(tag_name) FROM q_tag WHERE tag_code='UNCATEGORIZED';
```

棰勬湡锛?- 骞寸骇: `E5B9B4E7BAA7`
- 鐭ヨ瘑鐐? `E79FA5E8AF86E782B9`
- 鍓爣绛? `E589AFE6A087E7ADBE`
- 鏈垎绫? `E69CAAE58886E7B1BB`

