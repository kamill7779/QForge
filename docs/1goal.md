# 高考数学题库最终方案

更新时间：2026-03-09

本文是当前仓库中关于高考数学题库、OCR、AI 分析、向量检索与 RAG 的唯一有效方案。此前偏“大而全”或落库时机不准确的设计已删除。

## 1. 最终结论

这次设计只围绕两条主流程展开：

1. 整套试卷录入流
2. 单题拍照检索流

并明确以下最终约束：

1. 题库当前聚焦 **高考数学**，不再为了“全学科通用”提前引入过宽设计。
2. `ocr-service` 单独作为 **OCR RPC 能力服务** 存在，只负责识别与分块，不负责高考数学推荐语义。
3. 高考数学的 AI 清洗、XML 规范化、标签提取、难度分析、向量构建、RAG 推荐理由，统一由单独的 `gaokao-analysis-service` 负责。
4. 高考数学题库主域由 `gaokao-corpus-service` 负责，包含录入暂存、人工校正、确认发布、查询与推荐。
5. 向量数据库是强制组件，正式采用 `Qdrant`。
6. 整套试卷流程采用 **先暂存、后校正、再分析、最后确认发布** 的模式。
7. 单题拍照流程默认 **不落业务主库**，只返回渲染结果与推荐结果。
8. 当前 `question-core-service` 仍然只承接正式题库与组卷运行态，不直接承接高考数学语料主存。
9. 高考数学题只有在需要进入试题篮、组卷、导出时，才通过桥接动作物化到 `question-core-service`。

## 2. 最终服务架构

## 2.1 服务职责

### `ocr-service`

职责：

- PDF / 图片 OCR
- 版面识别
- 数学公式识别
- 区域切块与分题辅助

不负责：

- XML 清洗
- 高考数学知识点分析
- 难度分析
- 推荐检索
- RAG 理由生成

### `gaokao-corpus-service`

职责：

- 整套试卷上传入口
- 录入暂存与人工修正
- 高考数学正式题库主数据
- 相似题查询入口
- 与 `question-core-service` 的桥接

它是这套高考数学系统的业务主服务。

### `gaokao-analysis-service`

职责：

- OCR 后文本清洗
- 题干 / 答案转 XML
- 数学题专用标签分析
- 难度分析
- 解法标签、公式标签、易错点、推理步数分析
- 向量构建
- 相似题召回后的重排
- RAG 推荐理由生成

### `question-core-service`

保持原定位：

- 正式题库
- 用户题目编辑域
- 与 `exam-service` 的稳定内部契约

高考数学题只有在“进入正式组卷链路”时才写入它。

### `exam-service`

保持原定位：

- 试题篮
- 组卷
- 导出前的数据编排

### `export-sidecar`

保持原定位：

- 只负责导出渲染

## 2.2 为什么不复用 `exam-parse-service`

`exam-parse-service` 当前适合的是：

- 用户上传试卷
- 暂存解析结果
- 确认后直接落正式题库

它不适合这次高考数学题库流程，原因是：

1. 这次不是“确认后直接入正式题库”，而是“确认后进入高考语料库主域”。
2. 这次需要前端对整卷和单题进行反复修正、分析预览、二次确认。
3. 这次需要向量库、RAG chunk、推荐关系和数学专用 profile。
4. 这次有单题拍照检索流，而不是只做试卷解析确认。

因此：

- OCR 相关识别能力可以复用 `ocr-service`
- 但业务域不能继续复用 `exam-parse-service`

## 3. 两条唯一主流程

## 3.1 流程 A：整套试卷录入

这是高考数学题库的主生产链路。

### 流程目标

把一整套高考数学试卷从 PDF / 图片变成：

- 可人工校正
- 可 AI 深分析
- 可正式发布
- 可进入向量检索
- 可在未来物化到正式组卷链路

### 流程规则

1. 每次上传视为“一整套完整试卷”。
2. OCR 分题阶段允许没有答案。
3. OCR 分题后先展示给前端，不直接写入正式题库。
4. 前端修正后先保存为“录入草稿”。
5. AI 分析可以对单题触发，也可以对整套试卷批量触发。
6. AI 返回结果先给前端预览和确认。
7. 只有前端确认后，题目才正式写入高考数学主库与向量数据库。

### 为什么必须先草稿后发布

因为高考数学的 OCR、分题、公式还原、子题拆分都容易产生误差。若直接入正式语料库，会把错误的题干、错误的题号、错误的结构直接写成推荐基础数据，后续越分析越偏。

## 3.2 流程 B：单题拍照检索

这是高考数学题库的在线消费链路。

### 流程目标

用户拍一张题目图，系统完成：

1. OCR
2. 文本清洗
3. XML 规范化
4. 标签和难度分析
5. 相似题推荐
6. 一次性返回完整渲染结果

### 流程规则

1. 这类拍照题 **不进入高考数学主库**。
2. 这类拍照题 **不写入 `question-core-service` 现有题库表**。
3. 这类拍照题仅作为一次请求的查询对象存在。
4. 返回前端的结果格式应尽量复用现有题目渲染结构：
   - `stemXml`
   - `answerXml`
   - `stemImages`
   - `answerImages`
   - `answers`
   - `tags`
5. 如果 OCR 没有识别出答案，就只返回题干侧结果，不强补答案。

## 4. 最终数据分层

这次设计不再把“草稿态”“正式语料态”“正式组卷态”混在一起，而是拆成三层。

## 4.1 第一层：录入草稿层

用途：

- 承接整套试卷上传后的 OCR / 分题结果
- 允许前端修改
- 允许答案暂缺
- 允许重复触发 AI 分析

特点：

- 是业务暂存，不是正式语料
- 允许字段不完整
- 允许 AI 分析结果被覆盖

## 4.2 第二层：高考数学正式语料层

用途：

- 存放已经确认发布的高考数学试卷与题目
- 作为相似题推荐、系列题推荐和 RAG 的正式数据源

特点：

- 题目结构必须完整到可被检索和分析
- AI 分析结果必须是已确认版本
- 向量与 chunk 在这一层建立

## 4.3 第三层：正式组卷运行层

用途：

- 承接“从高考数学语料库中挑题进入组卷”

特点：

- 仍使用当前 `question-core-service` + `exam-service` + `export-sidecar`
- 通过物化或复制桥接，不直接读取 `gk_*` 表

## 5. 草稿层表结构

## 5.1 `gk_ingest_session`

用途：

- 一次完整试卷录入会话

关键字段：

| 字段 | 说明 |
| --- | --- |
| `session_uuid` | 会话 UUID |
| `status` | `UPLOADED / OCRING / SPLIT_READY / EDITING / ANALYZING / READY_TO_PUBLISH / PUBLISHED / FAILED` |
| `source_kind` | `PDF / IMAGE_SET` |
| `subject_code` | 默认 `MATH` |
| `operator_user` | 操作人 |
| `paper_name_guess` | OCR 初猜卷名 |
| `exam_year_guess` | OCR 初猜年份 |
| `province_code_guess` | OCR 初猜地区 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

## 5.2 `gk_ingest_source_file`

用途：

- 会话中的原始 PDF / 图片文件

关键字段：

| 字段 | 说明 |
| --- | --- |
| `source_file_uuid` | 文件 UUID |
| `session_id` | 所属录入会话 |
| `file_name` | 文件名 |
| `file_type` | `PDF / PNG / JPG` |
| `storage_ref` | 文件存储引用 |
| `page_count` | 页数 |
| `checksum_sha256` | 哈希 |

## 5.3 `gk_ingest_ocr_page`

用途：

- 整卷 OCR 的页级原始结果

关键字段：

| 字段 | 说明 |
| --- | --- |
| `session_id` | 录入会话 |
| `source_file_id` | 源文件 |
| `page_no` | 页码 |
| `full_text` | 页全文 |
| `layout_json` | 版面块 |
| `formula_json` | 公式识别结果 |
| `page_image_ref` | 页图片引用 |

## 5.4 `gk_draft_paper`

用途：

- 前端可编辑的整卷草稿

关键字段：

| 字段 | 说明 |
| --- | --- |
| `draft_paper_uuid` | 草稿试卷 UUID |
| `session_id` | 录入会话 |
| `paper_name` | 试卷名称 |
| `paper_type_code` | 卷型 |
| `exam_year` | 年份 |
| `province_code` | 地区 |
| `total_score` | 总分 |
| `duration_minutes` | 时长 |
| `status` | `EDITING / ANALYZING / READY_TO_PUBLISH` |

## 5.5 `gk_draft_section`

用途：

- 草稿试卷中的 section

关键字段：

| 字段 | 说明 |
| --- | --- |
| `draft_section_uuid` | UUID |
| `draft_paper_id` | 所属草稿试卷 |
| `section_code` | `SINGLE_CHOICE / FILL_BLANK / SOLUTION` |
| `section_title` | 标题 |
| `sort_order` | 顺序 |

## 5.6 `gk_draft_question`

用途：

- 草稿题主表

关键字段：

| 字段 | 说明 |
| --- | --- |
| `draft_question_uuid` | UUID |
| `draft_paper_id` | 所属草稿试卷 |
| `draft_section_id` | 所属 section |
| `parent_question_id` | 父题 |
| `root_question_id` | 根题 |
| `question_no` | 题号 |
| `question_type_code` | 题型 |
| `answer_mode` | `OBJECTIVE / SUBJECTIVE / COMPOSITE` |
| `stem_text` | OCR 题干文本 |
| `stem_xml` | 清洗后 XML |
| `normalized_stem_text` | 规范化文本 |
| `score` | 分值 |
| `has_answer` | 是否已有答案 |
| `edit_version` | 前端编辑版本 |

说明：

- 草稿题允许 `stem_xml` 暂时为空。
- 草稿题允许没有答案。

## 5.7 `gk_draft_option`

用途：

- 草稿选择题选项

关键字段：

| 字段 | 说明 |
| --- | --- |
| `draft_option_uuid` | UUID |
| `draft_question_id` | 草稿题 |
| `option_label` | `A / B / C / D` |
| `option_text` | 文本 |
| `option_xml` | XML |
| `sort_order` | 顺序 |

## 5.8 `gk_draft_answer`

用途：

- 草稿答案

关键字段：

| 字段 | 说明 |
| --- | --- |
| `draft_answer_uuid` | UUID |
| `draft_question_id` | 草稿题 |
| `answer_type` | `OFFICIAL / STEP / ALTERNATIVE / CHOICE_KEY` |
| `answer_text` | 文本 |
| `answer_xml` | XML |
| `is_official` | 是否官方答案 |
| `sort_order` | 顺序 |

## 5.9 `gk_draft_question_asset`

用途：

- 草稿题干图片或裁剪图

关键字段：

| 字段 | 说明 |
| --- | --- |
| `draft_question_id` | 草稿题 |
| `asset_type` | `IMAGE / FORMULA_IMAGE / REGION_CROP` |
| `storage_ref` | 存储引用 |
| `sort_order` | 顺序 |

## 5.10 `gk_draft_answer_asset`

用途：

- 草稿答案图片

关键字段：

| 字段 | 说明 |
| --- | --- |
| `draft_answer_id` | 草稿答案 |
| `asset_type` | `IMAGE / FORMULA_IMAGE` |
| `storage_ref` | 存储引用 |
| `sort_order` | 顺序 |

## 5.11 `gk_draft_profile_preview`

用途：

- AI 分析后返回给前端确认的预览结果

关键字段：

| 字段 | 说明 |
| --- | --- |
| `draft_question_id` | 草稿题 |
| `knowledge_tags_json` | 知识点 |
| `method_tags_json` | 解法标签 |
| `formula_tags_json` | 关键公式 |
| `mistake_tags_json` | 易错点 |
| `ability_tags_json` | 能力标签 |
| `difficulty_score` | 难度值 |
| `difficulty_level` | 难度层级 |
| `reasoning_steps_json` | 推理步数 / 步骤 |
| `analysis_summary_text` | 解析摘要 |
| `recommend_seed_text` | 用于后续向量化的联合语料 |
| `profile_version` | 分析版本 |
| `confirmed` | 前端是否确认 |

这张表非常关键，因为你的流程要求：

- AI 先分析
- 前端先看结果
- 前端确认后才进入正式库

## 6. 正式语料层表结构

## 6.1 `gk_paper`

用途：

- 已发布高考数学试卷主表

关键字段：

| 字段 | 说明 |
| --- | --- |
| `paper_uuid` | UUID |
| `source_session_uuid` | 来源录入会话 |
| `paper_name` | 名称 |
| `paper_type_code` | 卷型 |
| `exam_year` | 年份 |
| `province_code` | 地区 |
| `subject_code` | 默认 `MATH` |
| `status` | `READY / ARCHIVED` |

## 6.2 `gk_paper_section`

用途：

- 已发布试卷 section

核心字段：

- `section_uuid`
- `paper_id`
- `section_code`
- `section_title`
- `sort_order`

## 6.3 `gk_question`

用途：

- 已发布高考数学题主表

关键字段：

| 字段 | 说明 |
| --- | --- |
| `question_uuid` | UUID |
| `paper_id` | 试卷 |
| `section_id` | section |
| `parent_question_id` | 父题 |
| `root_question_id` | 根题 |
| `question_no` | 题号 |
| `question_type_code` | 题型 |
| `answer_mode` | 作答模式 |
| `stem_text` | 题干文本 |
| `stem_xml` | 正式 XML |
| `normalized_stem_text` | 规范化文本 |
| `score` | 分值 |
| `difficulty_score` | 难度值 |
| `difficulty_level` | 难度层级 |
| `reasoning_step_count` | 推理步数 |
| `has_answer` | 是否有答案 |
| `published_at` | 发布时间 |

## 6.4 `gk_question_option`

用途：

- 已发布选项

核心字段：

- `option_uuid`
- `question_id`
- `option_label`
- `option_text`
- `option_xml`
- `is_correct`
- `sort_order`

## 6.5 `gk_question_answer`

用途：

- 已发布答案

核心字段：

- `answer_uuid`
- `question_id`
- `answer_type`
- `answer_text`
- `answer_xml`
- `is_official`
- `sort_order`

## 6.6 `gk_question_asset`

用途：

- 已发布题干图片 / 资源

核心字段：

- `question_id`
- `asset_type`
- `storage_ref`
- `sort_order`

## 6.7 `gk_answer_asset`

用途：

- 已发布答案图片 / 资源

核心字段：

- `answer_id`
- `asset_type`
- `storage_ref`
- `sort_order`

## 6.8 `gk_question_profile`

用途：

- 已确认的高考数学深分析结果

关键字段：

| 字段 | 说明 |
| --- | --- |
| `question_id` | 对应题目 |
| `knowledge_path_json` | 知识点路径 |
| `method_tags_json` | 解法标签 |
| `ability_tags_json` | 能力标签 |
| `mistake_tags_json` | 易错点 |
| `formula_tags_json` | 公式标签 |
| `answer_structure_json` | 答案结构 |
| `reasoning_steps_json` | 推理步骤 |
| `analysis_summary_text` | 解析摘要 |
| `solve_path_text` | 解题路径 |
| `difficulty_score` | 难度值 |
| `difficulty_level` | 难度层级 |
| `profile_version` | 分析版本 |

## 6.9 `gk_taxonomy_node`

用途：

- 数学标签树

分类至少包含：

1. `KNOWLEDGE`
2. `METHOD`
3. `ABILITY`
4. `MISTAKE`
5. `FORMULA`
6. `QUESTION_TYPE`

## 6.10 `gk_question_taxonomy_rel`

用途：

- 题目与标签树关系

核心字段：

- `question_id`
- `node_id`
- `taxonomy_code`
- `source_kind`
- `confidence`
- `is_primary`

## 6.11 `gk_rag_chunk`

用途：

- RAG 解释用的结构化 chunk

chunk 类型至少包含：

1. `STEM`
2. `ANSWER`
3. `ANALYSIS_SUMMARY`
4. `METHOD_NOTE`
5. `MISTAKE_NOTE`
6. `FORMULA_NOTE`
7. `STEP_NOTE`

## 6.12 `gk_vector_point`

用途：

- MySQL 中保存 Qdrant 点位映射

核心字段：

- `target_type`
- `target_id`
- `vector_kind`
- `collection_name`
- `qdrant_point_id`
- `payload_json`
- `status`

## 6.13 `gk_recommend_edge`

用途：

- 离线预计算推荐边

关系类型至少包含：

1. `SAME_CLASS`
2. `VARIANT`
3. `ADVANCED`
4. `MISTAKE`
5. `GENERAL`

## 6.14 `gk_question_materialization`

用途：

- 从高考数学语料到 `question-core-service` 的桥接记录

核心字段：

- `gk_question_id`
- `target_question_uuid`
- `owner_user`
- `mode`
- `status`
- `source_hash`

## 7. 单题拍照流的数据策略

这一条是本次设计里最容易被误做重的地方。

最终规则：

1. 单题拍照流不写 `gk_question`
2. 单题拍照流不写 `q_question`
3. 单题拍照流不进入正式语料
4. 单题拍照流只把“查询输入题”作为临时对象用于推荐

因此 Phase 1 不新增专门的“拍照题业务主表”。

只保留两类技术状态：

1. `ocr-service` 自身任务状态
2. `gaokao-analysis-service` 自身任务状态

如需审计，可后续补 `gk_query_trace`，但不作为第一阶段必需表。

## 8. 向量数据库与 RAG 最终设计

## 8.1 Qdrant 集合设计

推荐两个集合：

1. `gk_question_vectors`
2. `gk_rag_chunks`

其中：

- `gk_question_vectors` 采用 named vectors：
  - `stem`
  - `analysis`
  - `joint`
- `gk_rag_chunks` 保存 chunk 向量

## 8.2 payload 过滤字段

至少包含：

1. `questionUuid`
2. `paperUuid`
3. `subjectCode`
4. `questionTypeCode`
5. `difficultyLevel`
6. `examYear`
7. `provinceCode`
8. `knowledgeCodes`
9. `methodCodes`
10. `rootQuestionId`

## 8.3 RAG 的真实职责

RAG 不负责“做题”，而负责：

1. 解释为什么推荐这些题
2. 解释相似点和差异点
3. 解释适合做同类巩固还是变式提升

RAG 输入必须来源于：

- 检索命中的正式题库题
- `gk_question_profile`
- `gk_rag_chunk`
- 推荐分组与分数

不能直接拿模型自由发挥。

## 9. 两条主流程的接口设计

## 9.1 整套试卷录入流接口

建议接口：

1. `POST /api/gaokao/ingest-sessions`
   - 创建录入会话
2. `POST /api/gaokao/ingest-sessions/{sessionUuid}/files`
   - 上传 PDF / 图片
3. `POST /api/gaokao/ingest-sessions/{sessionUuid}/ocr-split`
   - 触发 OCR + 分题
4. `GET /api/gaokao/ingest-sessions/{sessionUuid}/draft-paper`
   - 取整卷草稿
5. `PUT /api/gaokao/draft-papers/{draftPaperUuid}`
   - 保存整卷修正
6. `PUT /api/gaokao/draft-questions/{draftQuestionUuid}`
   - 保存单题修正
7. `POST /api/gaokao/draft-questions/{draftQuestionUuid}/analyze`
   - 对单题做 AI 分析
8. `POST /api/gaokao/draft-papers/{draftPaperUuid}/analyze`
   - 对整卷全部题做 AI 分析
9. `POST /api/gaokao/draft-questions/{draftQuestionUuid}/confirm`
   - 确认单题分析结果
10. `POST /api/gaokao/draft-papers/{draftPaperUuid}/publish`
   - 正式发布整卷到 `gk_*` 与 Qdrant

## 9.2 单题拍照检索流接口

建议接口：

1. `POST /api/gaokao/photo-query`

单次请求内部完成：

1. OCR
2. 题干清洗
3. XML 规范化
4. 标签与难度分析
5. 相似题召回
6. 重排
7. RAG 理由生成

一次性返回：

1. `queryQuestion`
2. `ocrRaw`
3. `analysisProfile`
4. `recommendGroups`
5. `reasonSummary`

如果后续发现请求时间过长，再拆成：

1. `POST /photo-query/tasks`
2. `GET /photo-query/tasks/{taskUuid}`

但第一版不必先做重。

## 10. AI 分析职责拆分

## 10.1 整卷录入阶段 AI

目标：

- 把草稿题修正为可发布题

输出：

1. `stem_xml`
2. `answer_xml`
3. `knowledge_tags`
4. `method_tags`
5. `formula_tags`
6. `mistake_tags`
7. `difficulty`
8. `reasoning_steps`
9. `analysis_summary`

这一阶段结果先写 `gk_draft_profile_preview`。

## 10.2 单题拍照阶段 AI

目标：

- 把临时查询题转成检索 query object

输出：

1. 清洗后的题干
2. 可渲染 XML
3. 若识别到答案则返回答案 XML
4. 标签与难度
5. 检索 query embedding
6. 推荐理由

这一阶段结果只返回前端，不进入正式业务表。

## 11. 与现有题库和组卷体系的关系

最终边界如下：

1. 高考数学语料库主存不在 `question-core-service`
2. 正式组卷主存仍在 `question-core-service`
3. `exam-service` 不直接读取 `gk_*` 表
4. `export-sidecar` 继续只吃 `exam-service` 编排后的导出 payload

当用户要把高考数学题加入试题篮或用于正式试卷导出时，执行：

1. `gaokao-corpus-service` -> `question-core-service` 物化
2. 生成正式 `questionUuid`
3. 后续走现有：
   - 试题篮
   - 组卷
   - 导出

## 12. 这次必须避免的错误设计

1. 不要把整卷 OCR 结果直接写进 `gk_question`
2. 不要把单题拍照结果写进 `q_question`
3. 不要把高考数学深分析继续塞进 `question-core-service`
4. 不要让 `exam-service` 直接读取向量库或 `gk_*` 表
5. 不要让 `ocr-service` 承担高层推荐语义
6. 不要为了“未来全学科”把数学域设计稀释掉

## 13. Phase 1 最小闭环

如果按最小闭环实现，第一阶段必须打通：

1. 整卷上传
2. OCR + 分题
3. 草稿整卷渲染
4. 草稿修正保存
5. 单题 / 整卷分析
6. 分析预览确认
7. 正式发布到 `gk_*`
8. Qdrant 建索引
9. 单题拍照检索
10. 相似题推荐 + RAG 理由
11. 高考题物化到 `question-core-service`

这就是在你当前仓库架构下，最贴合业务、边界清晰且能持续扩展的最终方案。
