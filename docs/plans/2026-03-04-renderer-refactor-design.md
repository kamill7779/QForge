# Frontend `renderer.js` 拆分重构设计（第一版）

**日期**: 2026-03-04  
**基线提交**: `78985ba`（当前答案 OCR 与资产链路 checkpoint）

## 1. 问题概述

当前 `frontend/src/renderer.js` 同时承担：
- 状态定义与状态流转
- OCR/WS 协议处理
- 图片引用规则与数据修复
- 渲染、编辑器、事件绑定
- API 请求与缓存

导致问题：
- 文件体积过大，认知负担高
- 函数耦合强，不易单测
- 同类逻辑分散（尤其图片与 WS）
- 变更风险大，回归成本高

## 2. 重构目标

本轮目标（不过度重构）：
- 把“可独立演进”的逻辑先抽出去，`renderer.js` 回归装配层。
- 先拆出协议与规则层，再逐步拆视图层。

不在本轮做：
- 全量框架迁移（Vue/React）
- 全部函数一次性切分

## 3. 目标目录结构（阶段化）

第一阶段（本次执行）：
- `frontend/src/runtime/image-runtime.js`
- `frontend/src/runtime/ocr-runtime.js`
- `frontend/src/renderer.js`（薄化）

第二阶段（后续）：
- `frontend/src/view/*`（列表、详情、bank 视图分拆）
- `frontend/src/editor/*`（stem/answer 编辑器）
- `frontend/src/store/*`（状态读写与序列化）

## 4. 第一阶段拆分边界

### 4.1 image-runtime
职责：
- `fig-N` 到 `a{task8}-img-N` 的映射兼容
- stem/answer 图片解析
- 插图引用生成（`img-N`、`a{seed}-img-N`）
- base64 -> data URL

要求：
- 纯规则优先；与页面渲染解耦
- 仅通过 `state` 注入需要写回的数据能力

### 4.2 ocr-runtime
职责：
- WebSocket 生命周期管理
- OCR 消息（成功/失败）归一化写入 `state.ocrTasks`
- stem/answer 任务结果回填策略
- OCR 成功后的资产刷新触发

要求：
- 协议处理集中，`renderer.js` 不再承载超长 `upsertWs/connectWs`
- 通过依赖注入绑定 `state/log/saveWorkspace/renderAll/fetchAssets`

## 5. 代码规范目标

- 单文件单职责，函数长度尽量 < 80 行
- 纯函数优先，副作用统一放在 runtime/service 层
- 业务规则函数必须可单测（Node test）

## 6. 验证策略

- 现有回归：
  - `node --test frontend/test/*.test.js`
  - 后端 OCR/Question 关键测试保持通过
- 手工冒烟：
  - 录题页发起答案 OCR -> WS 推送 -> 预览
  - 题库页答案图片编辑与保存

## 7. 下一步建议（可继续执行）

1. 拆 `renderBank*` 与 `renderDetail*` 到 view 层
2. 拆 `bind()` 事件绑定到独立 `events/*.js`
3. 给 `ocr-runtime` 增加更多协议错误分支测试
