# Gaokao 分题与题干渲染修复计划

日期：2026-03-11
前置文档：
- docs/2026-03-09-05-gaokao-math-business-flow.md
- docs/plan/2026-03-11-11-gaokao-round2-remediation-plan.md
- docs/plan/2026-03-11-13-exam-parse-gaokao-corpus-horizontal-scale-remediation-plan.md

## 1. 问题结论

当前 gaokao 录题链路存在三层叠加问题，不是单一提示词缺陷：

1. `gaokao-corpus-service` 目前没有真正做“整卷分题”，只是按 OCR 页生成草稿题。
2. `ocr-service` 给 gaokao 链路暴露的 `/internal/ocr/recognize` 只返回整页纯文本，`layoutJson` / `formulaJson` 目前固定为 `[]`，无法提供版面级分题依据。
3. `gaokao-web` 的题干 XML 解析与渲染能力明显弱于 `client` / `web`，对双重编码 XML、CDATA 包裹文本、复杂标签和图片占位符都缺少兼容。

因此，用户当前看到的“第 1 题其实是整卷文本”“题干 XML 中包着整段 CDATA”“预览区直接出现 `<div align=...>` 原文”等现象，与当前代码事实一致，不是偶发数据脏值。

## 2. 已确认事实

### 2.1 gaokao 录入并未进入真正分题阶段

`gaokao-corpus-service` 的 `IngestServiceImpl.triggerOcrSplit()` 当前只是逐个源文件调用 `ocr-service /internal/ocr/recognize`，把结果写入 `gk_ingest_ocr_page.full_text / layout_json / formula_json`，随后直接把 session 状态置为 `SPLIT_READY`。

这一步没有：

- 多页 OCR 聚合
- LLM 分题
- 题号识别
- 题型识别
- 答案配对
- 跨页题拼接

### 2.2 草稿初始化是“每页一题”

`gaokao-corpus-service` 的 `DraftServiceImpl.bootstrapDraftPaper()` 当前对每个 `GkIngestOcrPage` 直接创建一个 `GkDraftQuestion`：

- `questionNo = index + 1`
- `stemText = normalizeOcrText(fullText)`
- `stemXml = buildStemXml(fullText)`

这意味着整页 OCR 结果会被直接当作一道题。

### 2.3 当前 stemXml 只是把整页文本塞进 CDATA

`DraftServiceImpl.buildStemXml()` 当前实现为：

- 空文本返回 `<stem><p/></stem>`
- 非空文本返回 `<stem><p><![CDATA[...]]></p></stem>`

如果 OCR 文本本身包含 HTML/Markdown 风格片段，例如 `<div align="center">`、`# 标题`、图片 bbox 标记等，这些内容会作为普通文本进入 CDATA，前端预览时自然会看到原始标签文本，而不是结构化题干。

### 2.4 OCR 契约没有把版面信息真正交给 gaokao

`ocr-service` 的 `OcrRecognizeController` 当前返回：

- `fullText = glmOcrClient.recognizeText(...)`
- `layoutJson = "[]"`
- `formulaJson = "[]"`

`gaokao-corpus-service` 虽然保存了 `layoutJson` / `formulaJson`，但现在写进去的就是空数组，占位而非真实版面结果。

### 2.5 question / exam-parse 已经有一套更成熟的分题链路

`ocr-service` 在 `exam-parse-service` 链路中已经具备：

- 多页 OCR 聚合
- `ExamSplitLlmClient` 的长系统提示词
- `ExamParseOutputParser` 结构化解析
- `ExamQuestionXmlGenerator` 生成 stem/answer XML
- 图片裁剪与占位符映射

这套链路已经处理了：

- 章节标题过滤
- 题型识别
- seq 连续编号
- 答案存在性约束
- 图片 ref 保留
- 跨页拼接

gaokao 当前没有复用这条成熟路径。

### 2.6 gaokao-web 的渲染器明显弱于 web / client

`gaokao-web` 当前的 `stemXml.ts` 与 `useLatexRender.ts` 只支持：

- 简单 `<stem>/<answer>` 根节点解析
- 按段落取 `textContent`
- 失败时直接输出原始文本

缺失能力包括：

- 非法 XML 的再规范化
- LaTeX 中 `< > &` 的兜底转义
- 双重编码 XML 检测与重解析
- `<image>` `<choices>` `<blanks>` `<table>` `<answer-area>` 等复杂节点渲染
- 图片 resolver 与 renderKey 重渲染机制

而 `web` / `client` 已经具备上述兼容能力。

### 2.7 gaokao-analysis 的 AI 提示词与 JSON 容错也偏弱

`gaokao-analysis-service` 的 `AiAnalysisServiceImpl` 当前：

- system prompt 比 `question-service -> ocr-service.AiAnalysisTaskConsumer` 简陋
- 没有显式结果定界符
- 没有针对空响应、非 JSON、推理内容混入的多层 fallback
- `TextCleansingServiceImpl.convertToXml()` 只会生成 `<stem>纯转义文本</stem>`，不是可渲染段落结构

这会放大上游“整页一题”输入带来的问题，并导致分析结果里的 `stemXml` 继续偏粗糙。

## 3. 根因判断

### P0 根因

1. gaokao 录题链路缺少“整卷 OCR 后分题”这一核心环节。
2. gaokao 仍在消费过于原始的 OCR 契约，无法获得足够的结构化版面信息。

### P1 根因

1. gaokao-web 没有复用已验证的 XML 渲染器与清洗逻辑。
2. gaokao-analysis 的提示词与响应解析没有吸收 question 链路的稳健做法。

## 4. 修复目标

### 4.1 业务目标

1. 上传一套高考卷后，草稿工作台应生成“题目级”草稿，而不是“页面级”草稿。
2. 草稿预览应优先展示结构化题干，不再把整段原始 HTML/Markdown/CData 文本直接暴露给用户。
3. AI 分析应建立在单题粒度数据之上，并复用 question 链路已验证的提示词与 JSON 容错策略。

### 4.2 技术目标

1. gaokao 分题链路复用或抽取 `exam-parse` 成熟能力，避免维护第二套弱版本 prompt。
2. gaokao-web 统一到 `web/client` 级别的 XML 渲染与兼容策略。
3. 保持 `gaokao-corpus-service` 仍是草稿与正式语料归属方，不把业务编排错误塞进 `ocr-service` 或前端。

## 5. 分阶段计划

## Phase 1：补齐 gaokao 真正分题链路

### 5.1 目标

把“逐页 OCR -> 每页一题”替换为“多页 OCR -> LLM 分题 -> 草稿题列表”。

### 5.2 方案

优先复用 `ocr-service` 现有 `exam-parse` 组件，而不是在 `gaokao-corpus-service` 再拼一套新的 prompt + parser。

建议做法：

1. 在 `ocr-service` 增加 gaokao 专用内部分题入口。
2. 该入口复用：
   - `MultiPageOcrAggregator`
   - `ExamSplitLlmClient`
   - `ExamParseOutputParser`
3. 在 prompt 上做 gaokao 定制增强，而不是另起炉灶：
   - 明确高考试卷首页说明、注意事项、试卷标题不应作为题目
   - 明确选择题、填空题、解答题的大题标题只作 section 提示
   - 明确题号可能来自 `1.`、`1．`、`第1题`、`（1）`、`17.`、`18.` 等混合格式
   - 明确整卷常见“答案另页/参考答案”区域要与题号配对，不能把整段答案吞并到前面题干
   - 明确对 “如图”“见图”“第X题图” 的占位策略
   - 明确数学公式、bbox 标记、页眉页脚、扫描水印的处理规则
4. `gaokao-corpus-service` 改为接收“解析后的题目列表”而不是单页 `fullText`。
5. `DraftServiceImpl.bootstrapDraftPaper()` 改为按 parsed question 创建 `GkDraftQuestion`。

### 5.3 数据落点

建议新增一层“split result DTO”，至少包含：

- seq
- questionTypeCode
- sourcePages
- rawStemText
- stemXml
- rawAnswerText
- answerXml
- stemImageRefs
- answerImageRefs
- parseError
- errorMsg

### 5.4 风险

1. gaokao 与 exam-parse 的业务语义不同，不能直接复用“确认入题库”部分，只能复用 OCR 聚合、prompt、parser、XML 生成。
2. 如果仍继续使用 `/internal/ocr/recognize`，则无法从根上解决分题问题。

## Phase 2：增强 gaokao AI 分析提示词与响应解析

### 5.5 目标

让 gaokao-analysis 的单题分析质量与稳健性向 question 链路对齐。

### 5.6 方案

参考 `ocr-service.AiAnalysisTaskConsumer`，增强 `gaokao-analysis-service.AiAnalysisServiceImpl`：

1. 使用明确结果定界符，例如 `##RESULT_START## / ##RESULT_END##`。
2. 约束模型只输出 JSON object，不输出 Markdown。
3. 对 JSON 解析增加三层容错：
   - delimiter 抽取
   - code fence 剥离
   - 非 JSON 响应 fallback
4. 把题干输入边界固定在 `<input><![CDATA[...]]></input>` 中，继续阻断 prompt 注入。
5. 统一 `stemXml` 输出格式，至少落成 `<stem version="1"><p>...</p></stem>`，不要再返回裸 `<stem>...</stem>` 文本块。

### 5.7 注意

AI 分析增强不能替代分题本身。若输入仍是整页文本，提示词再强也只是“更稳定地分析错对象”。

## Phase 3：统一 gaokao-web 题干 XML 渲染能力

### 5.8 目标

让 gaokao-web 预览行为与 `client` / `web` 一致，至少对现有历史数据做到可读、可兜底。

### 5.9 方案

直接移植或抽取以下成熟实现到 gaokao-web：

1. `web/src/lib/stemXml.ts`
2. `web/src/composables/useLatexRender.ts`

最低要求：

1. 支持非法 XML 再规范化。
2. 支持 LaTeX 中 `< > &` 容错。
3. 支持双重编码 XML 重解析。
4. 支持 `<image>` `<choices>` `<blanks>` `<table>` `<answer-area>` 节点。
5. 解析失败时不要直接回显整段原始 XML，而是做 strip tags 后的文本降级。

### 5.10 兼容历史数据

对已有 `<![CDATA[<div ...>...]]>` 这类数据，前端可先做降级兼容：

1. 若 `<p>` 的文本看起来是 HTML/Markdown 原文，则按纯文本显示并保留换行。
2. 若文本内部看起来是完整 `<stem>` 或 `<answer>`，尝试二次解析。

但这只是兼容层，不应替代服务端修复。

## Phase 4：把 OCR 契约补成真实结构化输出

### 5.11 目标

让 gaokao 链路拿到真实 `layoutJson` / `formulaJson`，为后续更稳定的分题和图片映射打基础。

### 5.12 方案

1. `ocr-service.OcrRecognizeController` 不再把 `layoutJson` / `formulaJson` 固定为 `[]`。
2. 若当前 GLM OCR 能拿到版面结构，则按统一 DTO 映射输出。
3. 若现阶段仍拿不到结构化版面，也应在契约上区分“确实为空”与“当前未实现”。

## 6. 实施顺序建议

1. 先做 Phase 1。否则草稿粒度就是错的，后续所有分析和渲染都在错误对象上工作。
2. 再做 Phase 3。先把现有与历史数据尽量渲染正确，便于人工验收分题质量。
3. 再做 Phase 2。等单题粒度稳定后再增强分析 prompt，收益最高。
4. 最后做 Phase 4。它会进一步提升质量，但不是解除当前主阻塞的最短路径。

## 7. 本地 Docker 验证方案

用户已说明 Docker 与基础设施已启动，因此本地验证按以下路径执行：

### 7.1 后端验证

1. 选取一份当前会失败的高考卷样本。
2. 调用上传 + `ocr-split`。
3. 验证 `gk_draft_question` 数量应接近真实题数，而不是页数。
4. 抽查题号、题型、题干前几题是否正确拆分。
5. 验证含答案样本时，答案不会被吞入题干，也不会凭空生成。

### 7.2 前端验证

1. 打开 gaokao 工作台。
2. 确认预览区不再直接出现整段 `<div align=...>` 原文。
3. 确认公式仍能正常 KaTeX 渲染。
4. 确认复杂题型节点、表格、图片占位符有可接受展示。

### 7.3 AI 分析验证

1. 对单题触发分析。
2. 确认返回 JSON 可稳定解析。
3. 确认 `stemXml` / `answerXml` 为可渲染结构，不是裸文本包一层根节点。

## 8. 测试建议

建议新增以下测试：

### 8.1 `ocr-service`

1. 分题 prompt 回归测试：首页说明 + 多页题目 + 答案尾页样本。
2. `ExamSplitOutputParser` 针对高考题号格式的样本测试。

### 8.2 `gaokao-corpus-service`

1. `bootstrapDraftPaper` 改为按 split result 建题后的映射测试。
2. 旧的“每页一题”路径删除或改造后的回归测试。

### 8.3 `gaokao-web`

1. `stemXml.ts` 容错测试。
2. `useLatexRender.ts` 双重编码 XML / CDATA / 非法 XML 降级测试。

## 9. 当前不建议做的事

1. 不要只在 `gaokao-analysis-service` 上继续堆 prompt，试图让 AI 从整页文本直接完成所有修复。
2. 不要只在 gaokao-web 做字符串替换式渲染修补，把整页文本伪装成“看起来像单题”。
3. 不要把 `gaokao-corpus-service` 的分题职责挪到前端。

## 10. 建议的下一步实现包

建议按以下最小可交付包推进：

### 包 A

- `ocr-service`：新增 gaokao 专用整卷分题内部接口
- `gaokao-corpus-service`：接入 split result，替换“每页一题”
- 定向后端测试

### 包 B

- `gaokao-web`：移植 `web/client` 的 XML 解析与渲染器
- 前端定向用例 + 本地手工验收

### 包 C

- `gaokao-analysis-service`：增强 prompt、JSON 解析、`stemXml` 输出结构
- 单题分析回归测试
