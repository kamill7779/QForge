# XML 题干存储方案设计（最简可行版）

**日期:** 2026-03-02  
**状态:** 设计稿  
**关联:** [OCR 题库 MVP 设计文档](2026-02-28-ocr-question-bank-mvp-design.md)

---

## 1. 设计目标

### 1.1 核心理念

- **存储层不持有"题型"字段**：题型由 XML 标签组合隐式决定。
- **最少标签集**：只保留结构必需的标签，不提供富文本格式化能力。
- **LaTeX 不需要专用标签**：文本中直接写 LaTeX（如 `$x^2$`），前端自动检测并用 KaTeX 渲染。
- **图片通过接口随查询一并返回**：XML 中用 `<image />` 占位，后端查询时将图片 URL 附在响应体中一起给前端。

### 1.2 设计约束

| 约束 | 说明 |
|------|------|
| 每题至多一张题干配图 | `<image />` 在题干根层级至多出现 1 次 |
| 选项可含图片 | `<choice>` 内可放 `<image />` 占位标签 |
| LaTeX 公式 | 直接写在文本中，前端自动渲染，不需要 XML 标签 |
| 无富文本格式 | 不支持加粗、下划线等，纯文本 + LaTeX 即可 |
| 存储字段复用 | 直接存入现有 `q_question.stem_text`（LONGTEXT） |

---

## 2. XML Schema 定义

### 2.1 根元素

```xml
<stem version="1">
  <!-- 题干内容 -->
</stem>
```

### 2.2 完整标签清单（共 7 个）

| 标签 | 父元素 | 含义 | 属性 | 子元素 |
|------|--------|------|------|--------|
| `<stem>` | — | 根容器 | `version` | `<p>`, `<image>`, `<choices>`, `<blanks>`, `<answer-area>` |
| `<p>` | `<stem>`, `<choice>` | 文本段落 | — | 纯文本（可含 LaTeX） |
| `<image>` | `<stem>`, `<choice>` | 图片占位 | `ref`（资源 UUID） | 无（自闭合） |
| `<choices>` | `<stem>` | 选项组 | `mode`=`single`\|`multi` | `<choice>` |
| `<choice>` | `<choices>` | 单个选项 | `key`=`A`\|`B`\|… | `<p>`, `<image>` |
| `<blanks>` | `<stem>` | 填空组 | — | `<blank>` |
### 2.3 标签层级约束

```
stem
├── p*               // 文本段落（内含 LaTeX 文本前端自动渲染）
│   └── blank*       // 行内填空（嵌在句中）
├── image?           // 题干配图（至多一个）
├── choices?         // 选项组
│   └── choice+
│       ├── p?       // 选项文字
│       └── image?   // 选项配图
├── blanks?          // 独立填空组
│   └── blank+
└── answer-area?     // 解答区
```

> `*` = 0~N 次，`?` = 0~1 次，`+` = 至少 1 次。

---

## 3. 各题型 XML 示例

### 3.1 单选题

```xml
<stem version="1">
  <p>下列哪个是勾股定理的正确表述？</p>
  <choices mode="single">
    <choice key="A"><p>$a^2 + b^2 = c^2$</p></choice>
    <choice key="B"><p>$a + b = c$</p></choice>
    <choice key="C"><p>$a^2 - b^2 = c^2$</p></choice>
    <choice key="D"><p>$a^2 + b^2 = c$</p></choice>
  </choices>
</stem>
```

### 3.2 多选题

```xml
<stem version="1">
  <p>以下哪些属于三角函数？（多选）</p>
  <choices mode="multi">
    <choice key="A"><p>正弦函数 $\sin(x)$</p></choice>
    <choice key="B"><p>余弦函数 $\cos(x)$</p></choice>
    <choice key="C"><p>指数函数 $e^x$</p></choice>
    <choice key="D"><p>正切函数 $\tan(x)$</p></choice>
  </choices>
</stem>
```

### 3.3 选项含图片的选择题

```xml
<stem version="1">
  <p>下列图形中，哪一个是正三角形？</p>
  <choices mode="single">
    <choice key="A"><image ref="asset-uuid-001" /></choice>
    <choice key="B"><image ref="asset-uuid-002" /></choice>
    <choice key="C"><image ref="asset-uuid-003" /></choice>
    <choice key="D"><image ref="asset-uuid-004" /></choice>
  </choices>
</stem>
```

> 后端查询题目时，会把该题关联的所有 `q_question_asset` 记录一并返回（含 base64 图片数据），前端根据 `ref` UUID 匹配到对应的 `image_data` 渲染为 `<img src="data:image/png;base64,...">`。

### 3.4 填空题

```xml
<stem version="1">
  <p>已知 $f(x) = x^2$，则 $f(3) = $ <blank id="1" />。</p>
  <p>若 $f(a) = 16$，则 $a = $ <blank id="2" />。</p>
</stem>
```

### 3.5 解答题

```xml
<stem version="1">
  <p>已知直角三角形两直角边长分别为 3 和 4，求斜边长度。要求写出完整解题过程。</p>
  <answer-area />
</stem>
```

### 3.6 带配图的解答题

```xml
<stem version="1">
  <p>如图所示，求阴影部分的面积。</p>
  <image ref="asset-uuid-100" />
  <answer-area />
</stem>
```

---

## 4. 前后端协作方式

### 4.1 后端返回格式

查询题目详情时，后端一次性返回 XML 题干 + 资源列表：

```json
{
  "questionUuid": "cdf61c86-...",
  "stemXml": "<stem version=\"1\"><p>如图所示...</p><image ref=\"asset-uuid-100\" /><choices mode=\"single\">...</choices></stem>",
  "stemImage": {
    "assetUuid": "asset-uuid-100",
    "mimeType": "image/png",
    "imageData": "/9j/4AAQSkZJRg...（base64）"
  },
  "assets": [
    { "assetUuid": "asset-uuid-001", "assetType": "CHOICE_IMAGE", "mimeType": "image/png", "imageData": "iVBORw0KGgo...（base64）" }
  ],
  "answers": [ ... ]
}
```

> **图片数据量说明**：题干配图和选项配图一般为压缩后的小图（< 500KB），base64 编码后约 600~700KB，对于局域网/本地 Electron 应用完全可接受。列表页不返回图片数据，仅详情页按需加载。

### 4.2 前端渲染流程

```
1. 解析 stemXml → DOM 树
2. 递归遍历节点：
   - <p>：渲染文本段落，其中 $...$ 由 KaTeX 自动替换为公式
   - <image ref="xxx">：从 assets 列表中找到对应 imageData，渲染 <img src="data:{mimeType};base64,{imageData}">
   - <choices>：根据 mode 渲染 Radio/Checkbox 组
   - <choice>：递归渲染子内容
   - <blank>：渲染输入框
   - <answer-area>：渲染文本编辑区
   - 未知标签：静默忽略
```

### 4.3 标签 → 组件映射（精简版）

| XML 标签 | 前端组件 | 说明 |
|----------|---------|------|
| `<stem>` | 根容器 | — |
| `<p>` | 文本段落 | 文本中 `$...$` 自动 KaTeX 渲染 |
| `<image>` | 图片 | 从 `assets` 列表按 `ref` 查找 base64 数据，渲染为 data URI |
| `<choices>` | 选项组 | `mode` 决定单选/多选 |
| `<choice>` | 选项项 | 递归渲染子内容 |
| `<blanks>` | 填空容器 | — |
| `<blank>` | 输入框 | — |
| `<answer-area>` | 编辑区 | — |

---

## 5. 后端校验策略

后端在写入 `stem_text` 前执行校验：

1. **Well-formed 检查**：标准 XML 解析器验证格式。
2. **白名单标签**：仅允许 `stem`, `p`, `image`, `choices`, `choice`, `blanks`, `blank`, `answer-area`。
3. **结构约束**：
   - 根元素必须是 `<stem>`。
   - `<stem>` 直属 `<image>` 至多 1 个（题干配图限制）。
   - `<choices>` 内至少 2 个 `<choice>`。
   - `<choice>` 的 `key` 不重复。
   - `<blank>` 的 `id` 不重复。
4. **图片引用检查**：`<image ref="...">` 中的 UUID 在 `q_question_asset` 中存在。

```java
public class StemXmlValidator {

    private static final Set<String> ALLOWED_TAGS = Set.of(
        "stem", "p", "image", "choices", "choice",
        "blanks", "blank", "answer-area"
    );

    public void validate(String xml) {
        Document doc = parseXml(xml);
        Element root = doc.getDocumentElement();
        if (!"stem".equals(root.getTagName())) {
            throw new BusinessValidationException("INVALID_ROOT_ELEMENT");
        }
        walkAndValidateTags(root);
        validateSingleStemImage(root);
    }
}
```

---

## 6. 数据库适配性分析

### 6.1 现有表结构评估

| 需求 | 现有字段/表 | 结论 |
|------|-----------|------|
| XML 题干存储 | `q_question.stem_text` (LONGTEXT) | ✅ 直接可用 |
| 题干配图（至多1张） | 无图片/资源表，无配图字段 | ❌ 不满足 |
| 选项图片 | 同上 | ❌ 不满足 |
| 删除题目时资源处理 | 无软删除机制 | ❌ 不满足 |

设计文档中规划的 `q_question_asset` 表**从未落地到真实 schema**。

### 6.2 扩展方案

#### 6.2.1 `q_question` 表新增 `stem_image_id` 字段

**场景**：一道题至多一张题干配图，这是高频查询路径——列表页、详情页每次都要取配图。

**方案**：在 `q_question` 上新增一个外键字段直接指向 asset，避免每次查题都 JOIN 或子查询 asset 表。

```sql
ALTER TABLE q_question
    ADD COLUMN stem_image_id BIGINT NULL COMMENT '题干配图，指向 q_question_asset.id',
    ADD CONSTRAINT fk_q_question_stem_image
        FOREIGN KEY (stem_image_id) REFERENCES q_question_asset(id)
        ON DELETE SET NULL;
```

**为什么用 `ON DELETE SET NULL`？**  
当 asset 记录被清理时，`q_question.stem_image_id` 自动置空，不会导致外键悬挂，也不会级联删除题目。

**性能对比**：

| 方式 | 取题干配图 | 取选项配图 | 复杂度 |
|------|-----------|-----------|--------|
| 仅从 asset 表 FK 查 | `SELECT ... FROM q_question_asset WHERE question_id=? AND asset_type='STEM_IMAGE'` | 同表查 | 每次多一次查询 |
| **question 表冗余 `stem_image_id`** | `JOIN q_question_asset ON q_question.stem_image_id = asset.id`（或直接取到 id 后单查） | asset 表查 | 题干配图零额外查询 |

**结论**：题干配图是"一对一"且高频访问，冗余到主表上收益明确。选项配图是"一对多"且仅详情页需要，走 asset 表查即可。

#### 6.2.2 新增 `q_question_asset` 表

```sql
CREATE TABLE IF NOT EXISTS q_question_asset (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    asset_uuid  CHAR(36)      NOT NULL UNIQUE,
    question_id BIGINT        NOT NULL,
    asset_type  VARCHAR(32)   NOT NULL  COMMENT 'STEM_IMAGE / CHOICE_IMAGE',
    image_data  LONGTEXT      NOT NULL  COMMENT '图片 base64 编码数据（与 q_ocr_task.image_base64 一致）',
    file_name   VARCHAR(255)  NULL      COMMENT '原始文件名',
    mime_type   VARCHAR(128)  NULL      COMMENT 'image/png, image/jpeg 等',
    deleted     BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_q_asset_question (question_id, asset_type),
    INDEX idx_q_asset_deleted (deleted),
    CONSTRAINT fk_q_asset_question FOREIGN KEY (question_id) REFERENCES q_question(id)
) COMMENT '题目关联资源（图片 base64 存储）';
```

#### 6.2.3 `q_question` 表增加逻辑删除支持

现有 `q_question` 表没有软删除字段，需要补充：

```sql
ALTER TABLE q_question
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记';
```

### 6.3 删除策略：逻辑删除 + 延迟清理

#### 删除一道题时的完整流程

```
用户点击"删除题目"
        │
        v
┌───────────────────────────────────────┐
│ 1. UPDATE q_question                  │
│    SET deleted = TRUE                 │
│    WHERE id = ? AND owner_user = ?    │
│                                       │
│ 2. UPDATE q_question_asset            │
│    SET deleted = TRUE                 │
│    WHERE question_id = ?              │
│                                       │
│ 3. UPDATE q_answer                    │
│    SET deleted = TRUE                 │
│    WHERE question_id = ?              │
│                                       │
│ （同一事务内完成）                       │
└───────────────────────────────────────┘
        │
        v
  接口返回 200，前端刷新列表
        │
        │  ··· 30 天后 ···
        v
┌───────────────────────────────────────┐
│ 定时任务：物理清理                      │
│ 1. 查出 deleted=TRUE 且               │
│    updated_at < NOW() - 30 天的记录   │
│ 2. DELETE FROM q_question_asset       │
│    （base64 数据随行删除，无外部文件）   │
│ 3. DELETE FROM q_answer               │
│ 4. DELETE FROM q_question             │
└───────────────────────────────────────┘
```

#### 为什么选逻辑删除？

| 考量 | 物理删除 | 逻辑删除 |
|------|---------|---------|
| 误操作恢复 | ❌ 不可恢复 | ✅ 30天内可恢复 |
| 图片数据（DB 中 base64） | 立即丢失 | 保留，恢复时数据完整 |
| 外键一致性 | 需处理级联 | 不动 FK，只改标记 |
| 查询影响 | 无 | 所有查询加 `WHERE deleted = FALSE` |

#### 查询时过滤

MyBatis-Plus 原生支持 `@TableLogic` 注解，自动在所有查询中追加 `deleted = FALSE`：

```java
@TableName("q_question")
public class Question {
    // ...
    @TableLogic
    private Boolean deleted;
}

@TableName("q_question_asset")
public class QuestionAsset {
    // ...
    @TableLogic
    private Boolean deleted;
}
```

加上注解后，`selectList` / `selectById` 等所有操作自动追加 `WHERE deleted = 0`，无需手动处理。

#### `q_answer` 表同步改造

```sql
ALTER TABLE q_answer
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记';
```

### 6.4 完整查询示例

题目详情接口的查询策略：

```sql
-- 1. 取题目基本信息（不含图片数据，列表页/详情页通用）
SELECT q.*, a.asset_uuid AS stem_image_uuid, a.mime_type AS stem_image_mime
FROM q_question q
LEFT JOIN q_question_asset a ON q.stem_image_id = a.id AND a.deleted = FALSE
WHERE q.question_uuid = ? AND q.deleted = FALSE;

-- 2. 取题干配图数据（详情页单独请求，避免列表页加载大量 base64）
SELECT asset_uuid, mime_type, image_data
FROM q_question_asset
WHERE id = ? AND deleted = FALSE;

-- 3. 取选项配图（仅详情页需要）
SELECT asset_uuid, mime_type, image_data
FROM q_question_asset
WHERE question_id = ? AND asset_type = 'CHOICE_IMAGE' AND deleted = FALSE;

-- 4. 取答案
SELECT * FROM q_answer
WHERE question_id = ? AND deleted = FALSE ORDER BY sort_order;
```

**关键点：列表页 vs 详情页分离**

| 场景 | 查什么 | 是否含 `image_data` | 说明 |
|------|--------|-------------------|------|
| 列表页 | 查询 1 | ❌ 只取 UUID 和 mime_type | 避免列表接口返回大量 base64 |
| 详情页 | 查询 1 + 2 + 3 + 4 | ✅ 按需取图片数据 | 前端渲染时才加载 |

> 或者提供独立的图片接口 `GET /api/assets/{assetUuid}/data`，返回原始二进制 + `Content-Type`，前端直接作为 `<img src>` 使用。这样列表页的缩略图也可以懒加载。

后端详情接口组装成响应体：

```json
{
  "questionUuid": "cdf61c86-...",
  "stemXml": "<stem version=\"1\">...</stem>",
  "stemImage": {
    "assetUuid": "asset-uuid-100",
    "mimeType": "image/png",
    "imageData": "/9j/4AAQSkZJRg...（base64）"
  },
  "assets": [
    { "assetUuid": "asset-uuid-001", "assetType": "CHOICE_IMAGE", "mimeType": "image/png", "imageData": "iVBORw0KGgo..." }
  ],
  "answers": [ ... ]
}
```

> `stem_image_id` JOIN 定位到 asset 记录，再取数据；选项配图走 asset 表按 `question_id` 查。

### 6.5 变更清单

| 变更项 | 类型 | 说明 |
|--------|------|------|
| 新增 `q_question_asset` 表 | DDL | 含 `deleted` 字段 |
| `q_question` + `stem_image_id` | DDL | FK 指向 asset，`ON DELETE SET NULL` |
| `q_question` + `deleted` | DDL | 逻辑删除支持 |
| `q_answer` + `deleted` | DDL | 逻辑删除支持 |
| 实体类增加 `@TableLogic` | 代码 | Question / QuestionAsset / Answer |
| 删除接口改为逻辑删除 | 代码 | 一个事务内标记 question + asset + answer |
| 定时清理任务 | 代码 | 30天后物理删除（数据库中的 base64 数据）|

---

## 7. 总结

### 最简标签集（共 8 个标签）

`stem` · `p` · `image` · `choices` · `choice` · `blanks` · `blank` · `answer-area`

### 不需要的东西

- ~~`<latex>` 标签~~：LaTeX 以 `$...$` 形式写在文本里，前端自动渲染
- ~~`<bold>` / `<underline>` 等富文本标签~~：不支持
- ~~`<section>` 分组~~：MVP 不需要
- ~~题型枚举字段~~：由标签组合隐式决定

### 需要落地的变更

| 优先级 | 变更 | 影响范围 |
|--------|------|----------|
| P0 | 新增 `q_question_asset` 表 | DDL + 实体/仓储 |
| P0 | `q_question` 加 `stem_image_id` + `deleted` | DDL + 实体 |
| P0 | `q_answer` 加 `deleted` | DDL + 实体 |
| P0 | 实现 `StemXmlValidator` | question-service |
| P0 | 删除接口改为逻辑删除 | question-service |
| P1 | 查询接口返回 assets 列表 + stemImage（base64）| question-service API |
| P1 | 前端 XML 解析渲染器 | Electron 前端 |
| P2 | 定时物理清理任务（纯 DB 删除，无外部存储）| question-service 定时任务 |
