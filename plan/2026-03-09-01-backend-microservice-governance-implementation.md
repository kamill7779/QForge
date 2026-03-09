# 后端微服务拆分治理实施记录

## 基线

- 基线快照 commit:
  - `32853ad chore: snapshot full workspace before backend governance repair`
- 本轮按要求未执行测试或编译验证。

## 已实施内容

### 1. 配置治理

- 新增并对齐了 Nacos 参考配置:
  - `backend/configs/question-core-service.yml`
  - `backend/configs/exam-service.yml`
  - `backend/configs/exam-parse-service.yml`
- 删除了已过时的 `backend/configs/question-service.yml`。
- 更新了:
  - `backend/configs/README.md`
  - `backend/configs/NACOS_CONFIG_GUIDE.md`

### 2. 配置模型化

- `auth-service`
  - 引入 `SecurityProperties`
  - 收敛 JWT 与 swagger 相关配置
- `gateway-service`
  - 引入 `SecurityProperties`
  - 收敛 JWT 与 swagger 相关配置
- `exam-service`
  - 引入 `QForgeExamProperties`
  - 引入 `QForgeCacheProperties`
- `question-core-service`
  - 引入 `QForgeCacheProperties`

### 3. question-core 解析确认链路修复

- 新增 `ParsedQuestionCreateService`
  - 将 `InternalQuestionController` 中的 controller 直接写库逻辑下沉到应用服务
- 新增 `QuestionTagAssignmentService`
  - 统一题目标签分配逻辑
  - 兼容解析确认时的两类输入:
    - `mainTagsJson` 的对象数组
    - `secondaryTagsJson` 的字符串数组或扁平 tag token 数组
- 修复了 `exam-parse -> question-core` 确认入库后标签没有真正落库的问题
- 解析确认时会补齐缺失主类目的默认标签，而不再只依赖读取时兜底

### 4. question-core Redis 与查询优化

- 新增 `QuestionSummaryQueryService`
  - 为内部 `QuestionSummaryDTO` 增加 Redis 缓存
  - `exam-service` 通过 Feign 获取摘要时会命中该缓存
- `TagQueryServiceImpl`
  - 增加标签目录缓存
  - 将按分类逐个查 tag 的 N+1 改为按分类批量查
- `QuestionRepository`
  - 增加来源去重查询，替换原先按全量题目扫描再 `distinct`
- `QuestionCommandServiceImpl`
  - 在题干、答案、标签、难度、来源、完成、删除、AI 应用等路径补充摘要缓存失效
  - 在题干更新时补充图片缓存失效
  - 图片查询在 DB 回源后会回写短 TTL Redis 缓存

### 5. question-core 热配置边界修复

- 新增 `DynamicOriginHandshakeInterceptor`
  - WebSocket Origin 不再在启动时固化
  - 新握手会按当前 `qforge.business.ws-allowed-origins` 实时判定
- `WebSocketConfig`
  - 改为动态握手拦截器 + 注册层放行

### 6. exam-service Redis 与查询优化

- 新增 `ExamCacheService`
- `QuestionTypeService`
  - 题型列表缓存
  - 新增/修改/删除后失效
- `QuestionBasketService`
  - 试题篮 UUID 列表缓存
  - 试题篮详情缓存
  - 加入/移除/切换/清空后失效
- `ExamPaperService`
  - `listPapers()` 改为批量加载 section/question 后内存聚合
  - 替换原先按试卷循环的 section/question N+1
- `ExamSectionRepository`
  - 新增 `findByPaperIds(...)`

### 7. exam-service 默认值配置化

- `ExamPaperService`
  - 创建试卷默认时长不再硬编码 `120`
  - 章节默认分值不再硬编码 `5.0`
  - 试题篮建卷默认值也走配置

### 8. libs 共用边界收敛

- `common-contract`
  - 新增 `RedisChannelNames`
- `exam-parse-service` / `question-core-service`
  - 不再各自复制 `qforge:ws:push` 常量

### 9. 依赖清理

- 删除未使用的 `common-contract` 依赖:
  - `auth-service`
  - `gateway-service`
  - `exam-service`

### 10. 文档刷新

- 更新:
  - `backend/ARCHITECTURE.md`
  - `backend/README.md`
  - `backend/services/README.md`

## 仍未在本轮落地的事项

- 未补自动化测试
- 未执行编译/启动验证
- `persist-service` 仍缺少更强的职责说明与测试护栏
- `internal-api-contract` / `common-contract` 仍缺少系统性的序列化兼容测试
- `ocr-service` 的 API key / client timeout 仍属于外化但重启生效的配置

## 后续建议执行顺序

1. 先补 `question-core` 与 `exam-service` 的关键路径测试。
2. 再补 `persist-service` 和两个 contract library 的兼容测试。
3. 最后做一次真实环境 Nacos/Redis 验证，确认热配置边界与缓存失效行为。
