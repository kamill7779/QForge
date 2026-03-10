# Gaokao 审阅问题 第二轮修复计划

日期：2026-03-11
前置文档：`plan/2026-03-10-10-gaokao-review-remediation-plan.md`
跟踪文档：`problem.md`

## 背景

第一轮修复 7 项后，对剩余 11 项暂缓问题重新评审，筛选出 6 项适合立即修复的条目。优先级排序依据：安全风险（Prompt 注入）> 正确性（JSON 解析、异常分类）> 健壮性（Bean Validation、filters 映射）> 资源安全（流式 IO）。

## 修复清单

### 1. Prompt 注入防护（安全 — P0）

- 范围：`AiAnalysisServiceImpl`、`RagServiceImpl`
- 措施：
  - 用户数据 XML fenced block 包裹：`<input><![CDATA[...]]></input>`
  - system prompt 增加显式边界指令
  - `RagServiceImpl` 链式放大路径同步封堵

### 2. IngestServiceImpl 流式化（资源安全）

- 范围：`IngestServiceImpl`
- 措施：
  - `calculateSha256()` → `DigestInputStream` 流式 digest
  - `encodeFileAsBase64()` → `Base64.Encoder.wrap(OutputStream)` 流式编码
  - 新建 `IngestServiceImplTest`（4 个测试：SHA-256 正确性、Base64 正确性、1MB SHA-256、1MB Base64）

### 3. Bean Validation（正确性）

- 范围：`CreateQuestionFromGaokaoRequest`、`InternalQuestionController`、`internal-api-contract` pom
- 措施：
  - DTO 关键字段加 `@NotBlank`
  - 控制器入参加 `@Valid`
  - pom 加 `jakarta.validation-api` 依赖

### 4. parseJsonTokens ObjectMapper 化（正确性）

- 范围：`MaterializationServiceImpl`
- 措施：
  - 注入 `ObjectMapper`
  - `parseJsonTokens()` 改为 `objectMapper.readValue(rawJson, TypeReference<List<String>>)`
  - 异常 JSON 记 warn + 返回空列表

### 5. AI JSON 错误分类（可观测性）

- 范围：`AiAnalysisServiceImpl.requestJsonAnalysis()`
- 措施：
  - 单独捕获 `JacksonException`，截断响应落 warn 日志
  - 空响应单独记 info 日志
  - 其它异常保持原有处理

### 6. searchSimilar filters 映射（功能完整性）

- 范围：`VectorServiceImpl`
- 措施：
  - `filters` 非空时映射为 Qdrant `filter.must` 条件数组

## 仍暂缓（5 项）

| # | 条目 | 暂缓原因 |
|---|------|----------|
| 1 | OCR Base64 大小限制 | 阈值需结合实际链路确定 |
| 2 | 发布前状态校验 | 缺完整状态机，需前后端联动 |
| 3 | OCR DTO 收敛 | 纯重构，无紧迫性 |
| 4 | Feign 容错 | 需统一设计重试/熔断策略 |
| 5 | 发布链路完整性校验 | 需新增独立 service + 前端配合 |

## 验证计划

```bash
mvn -pl services/gaokao-corpus-service,services/gaokao-analysis-service,libs/internal-api-contract,services/question-service -am test
```

涉及模块：
- `gaokao-corpus-service`：IngestServiceImplTest（新建）+ 既有测试
- `gaokao-analysis-service`：既有测试（VectorServiceImplTest 等）
- `internal-api-contract`：编译验证
- `question-service`：编译验证（@Valid 注解）
