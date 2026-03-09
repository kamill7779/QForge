# Web / Client 边界与 Export-sidecar 说明

更新时间：2026-03-09

## 1. 边界结论

### 1.1 Web 应负责的能力

- 题库浏览
- 题目详情查看
- 试题篮管理
- 试卷创建与编辑
- 试卷预览
- 试卷导出

### 1.2 Client 应负责的能力

- OCR 识别
- AI 识别工作流
- 试卷解析
- 解析结果确认入库

## 2. 为什么不把 OCR / 试卷解析继续补到 Web

1. `client` 已经有完整工作流和更合适的交互形态。
2. `web` 当前更大的价值在题库、组卷、导出和教师日常管理场景。
3. 把 OCR / 解析在两个前端里都补齐，会让契约、WS 事件和状态管理再次分叉。

## 3. export-sidecar 的实际工作方式

当前实际链路不是：

- `web -> export-sidecar`
- 也不是 `export-sidecar -> question-core-service`

当前真实链路是：

1. `web` 调用 `POST /api/exam-papers/{paperUuid}/export/word`
2. `exam-service` 读取试卷结构
3. `exam-service` 调用 `question-core-service /internal/questions/batch-full`
4. `exam-service` 组装完整导出 payload
5. `exam-service` 调用 `export-sidecar /internal/export/questions/word`
6. `export-sidecar` 只负责 docx 渲染并返回二进制

补充事实：Docker 开发环境下，如果 `export-sidecar` 启动时尚未成功注册到 Nacos，`exam-service` 允许通过显式配置的内部直连地址回退到 `http://export-sidecar:8092`，避免把导出能力绑死在单次注册成功上。

## 4. export-sidecar 是否适合微服务体系

结论：适合，但定位应当非常明确。

它适合作为：

- 内部渲染服务
- 无状态 sidecar / worker
- 通过 Nacos 注册发现
- 由 `exam-service` 编排调用

它不适合作为：

- 面向浏览器直接暴露的业务服务
- 拥有题目主数据读写职责的服务
- 负责鉴权、用户归属校验的服务

## 5. 推荐处置

1. 保留 `export-sidecar`，继续作为内部 docx 渲染器。
2. 继续由 `exam-service` 作为唯一导出编排入口。
3. `web` 只调用 `exam-service`，不直连 sidecar。
4. sidecar 的重试、超时、错误翻译统一放在 `exam-service`。
5. sidecar 侧不要继续长出“查题”“鉴权”“拼装业务结构”的职责。
