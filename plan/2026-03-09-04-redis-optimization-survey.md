# Redis 优化调查

## 已实施缓存

### `question-core-service`

| Key | 用途 | TTL | 失效方式 |
| --- | --- | --- | --- |
| `qforge:tag-catalog:v1` | 标签目录 | 21600s | TTL 自然过期 |
| `qforge:question-summary:{user}:{questionUuid}` | 内部题目摘要 | 600s | 题干/答案/标签/难度/来源/完成/删除/AI 应用时主动失效 |
| `question:assets:{questionUuid}` | 题干图片短缓存 | 30s | OCR 写入、题干编辑或删除时失效 |

### `exam-service`

| Key | 用途 | TTL | 失效方式 |
| --- | --- | --- | --- |
| `qforge:question-types:{user}` | 题型目录 | 1800s | 新增/修改/删除题型时失效 |
| `qforge:basket:uuids:{user}` | 试题篮 UUID 列表 | 600s | 加入/移除/切换/清空时失效 |
| `qforge:basket:items:{user}` | 试题篮展示详情 | 600s | 加入/移除/切换/清空时失效 |

## 为什么这些路径适合 Redis

- 标签目录
  - 读多写极少
  - 结果小且稳定
- 题目摘要
  - `exam-service` 频繁按 UUID 批量读取
  - 内容远小于完整题目详情
- 题型目录
  - 典型的用户级低频写高频读
- 试题篮
  - 用户级热点状态
  - 失效边界清晰

## 为什么这些路径暂不建议首批上 Redis

- `batch-full` 完整题目导出数据
  - 对象大
  - 组合结果依赖导出上下文
  - 容易把 Redis 变成大对象仓库
- 试卷详情整页快照
  - 组卷编辑过程频繁变化
  - 失效点太多
- 来源列表
  - 用 `DISTINCT` 查询即可解决
  - 不值得额外引入缓存一致性成本

## 本轮发现但未继续推进的 Redis 方向

1. `exam-service` 试卷详情可考虑局部缓存 section/question 结构，但前提是先定义清楚失效边界。
2. `question-core-service` 的 `listUserQuestions()` 如果后续数据量继续增长，应先拆分页和过滤，再评估缓存。
3. OCR/AI 热状态目前已经在 Redis 中，但若未来 TTL 和事件量继续升高，需要补观测指标。

## 后续建议

1. 先对已上线缓存补测试，再决定是否扩展缓存面。
2. 不要用 Redis 掩盖慢 SQL；优先完成查询层优化后再缓存。
3. 若继续增加缓存键，统一维护 key 命名规范和失效说明。
