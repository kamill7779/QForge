# Web 端组卷平台设计文档

**日期:** 2026-03-06  
**状态:** 设计稿  
**关联:**
- [OCR 题库 MVP 设计文档](2026-02-28-ocr-question-bank-mvp-design.md)
- [用户私有配额与题目上限设计](2026-03-04-user-private-quota-and-question-limit-design.md)
- [当前数据库 Schema](../current-database-schema.md)

---

## 1. 设计目标

### 1.1 核心场景

开放一个 **Web 端**（浏览器直接访问），面向教师和教研员，提供 **组卷** 功能：

1. **浏览题库** — 用户可查看自己的私人题库，以及平台公开的公共题库
2. **搜索与筛选** — 按年级、知识点、难度、关键词多维筛选题目
3. **组合试卷** — 从题库中挑选题目，编排为一份完整试卷（含大题分组、题号、分值分配）
4. **预览与导出** — 在线预览试卷排版效果，导出为 PDF / Word 文件
5. **试卷管理** — 保存、编辑、删除已组试卷，支持试卷模板复用

> **Web 端不包含题目录入功能。** 所有题目均通过 Electron 桌面端录入，Web 端只读消费题库数据。

### 1.2 与 Electron 桌面端的分工

| 功能 | Electron 桌面端 | Web 端 |
|------|----------------|--------|
| 截图 OCR 识别 | ✅ | ❌ |
| PDF 批量导入 | ✅ | ❌ |
| 题目录入与编辑 | ✅ | ❌ |
| AI 分析与推荐 | ✅ | ❌ |
| 浏览题库 | ✅（BankView） | ✅ |
| 组卷 | ❌ | ✅ |
| 试卷导出 | ❌ | ✅ |
| 用户认证 | ✅（共享 token） | ✅（共享 token） |

### 1.3 设计约束

| 约束 | 说明 |
|------|------|
| 共享后端 | Web 端复用现有 gateway / question-service / auth-service，不新建后端服务 |
| 共享认证 | 使用同一套 JWT token 体系，Electron 端与 Web 端登录互通 |
| 只读题库 | Web 端对 `q_question` 表只有 SELECT 权限，不可修改题目内容 |
| 渐进增强 | 先实现核心组卷流程，后续可扩展在线考试、智能推题等 |

---

## 2. 系统架构

### 2.1 整体拓扑

```
┌─────────────────┐     ┌──────────────┐
│  Electron 桌面端  │     │   Web 浏览器   │
│  (录入 + 题库)   │     │  (组卷平台)    │
└───────┬─────────┘     └──────┬───────┘
        │                      │
        └──────────┬───────────┘
                   ▼
          ┌────────────────┐
          │  gateway-service │  ← Nginx / Caddy 反向代理
          │  :8080           │
          └────────┬────────┘
                   │
        ┌──────────┼──────────┐
        ▼          ▼          ▼
   auth-service  question-  exam-service (NEW)
   :8088         service     :8093
                 :8089
```

### 2.2 新增模块

| 模块 | 职责 |
|------|------|
| **exam-service** | 试卷 CRUD、组卷逻辑、分值计算、试卷导出 |
| **web-frontend** | Vue 3 SPA，独立于 Electron 前端，部署到 CDN / Nginx |
| **export-worker** | (可选) 后台 PDF/Word 渲染队列，避免阻塞主线程 |

---

## 3. 数据模型

### 3.1 新增数据库表

```sql
-- ═════════════════════════════════════════════
-- 试卷主表
-- ═════════════════════════════════════════════
CREATE TABLE q_exam (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_uuid       VARCHAR(36)  NOT NULL UNIQUE,
    owner_user      VARCHAR(64)  NOT NULL,
    title           VARCHAR(200) NOT NULL DEFAULT '',
    description     TEXT,
    total_score     DECIMAL(5,1) NOT NULL DEFAULT 0.0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                    COMMENT 'DRAFT | PUBLISHED | ARCHIVED',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_owner (owner_user),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═════════════════════════════════════════════
-- 试卷大题（Section / Part）
-- ═════════════════════════════════════════════
CREATE TABLE q_exam_section (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_id         BIGINT       NOT NULL,
    title           VARCHAR(100) NOT NULL DEFAULT '' COMMENT '如: 一、选择题',
    sort_order      INT          NOT NULL DEFAULT 0,
    score_per_item  DECIMAL(4,1) COMMENT '本大题每小题默认分值（可覆盖）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (exam_id) REFERENCES q_exam(id) ON DELETE CASCADE,
    INDEX idx_exam (exam_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═════════════════════════════════════════════
-- 试卷题目条目（试卷 ↔ 题目 关联）
-- ═════════════════════════════════════════════
CREATE TABLE q_exam_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    section_id      BIGINT       NOT NULL,
    question_id     BIGINT       NOT NULL COMMENT '引用 q_question.id',
    sort_order      INT          NOT NULL DEFAULT 0,
    score           DECIMAL(4,1) NOT NULL DEFAULT 0.0 COMMENT '该题在本试卷中的分值',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (section_id) REFERENCES q_exam_section(id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES q_question(id) ON DELETE RESTRICT,
    INDEX idx_section (section_id),
    INDEX idx_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═════════════════════════════════════════════
-- 公共题库标记（将私有题目发布为公共）
-- ═════════════════════════════════════════════
CREATE TABLE q_public_question (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id     BIGINT       NOT NULL UNIQUE,
    published_by    VARCHAR(64)  NOT NULL,
    published_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (question_id) REFERENCES q_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.2 ER 关系

```
q_exam  1 ──── N  q_exam_section  1 ──── N  q_exam_item
                                                  │
                                                  N
                                                  │
                                              q_question (existing)
                                                  │
                                                  1
                                                  │
                                            q_public_question (optional)
```

---

## 4. API 设计

### 4.1 题库浏览（只读）

复用现有 question-service API，Web 端通过 gateway 访问：

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/questions` | 获取当前用户私人题库 |
| GET | `/api/questions/public` | **新增** — 获取公共题库列表（分页） |
| GET | `/api/questions/{uuid}` | **新增** — 获取题目详情（含题干 + 答案） |
| GET | `/api/questions/{uuid}/assets` | 获取题目图片资源 |

### 4.2 组卷 CRUD（exam-service）

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/exams` | 创建试卷草稿 |
| GET | `/api/exams` | 获取当前用户所有试卷 |
| GET | `/api/exams/{uuid}` | 获取试卷详情（含分组 + 题目） |
| PUT | `/api/exams/{uuid}` | 更新试卷标题、描述 |
| DELETE | `/api/exams/{uuid}` | 删除试卷 |
| POST | `/api/exams/{uuid}/publish` | 发布试卷（锁定内容） |

### 4.3 试卷分组与题目管理

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/exams/{uuid}/sections` | 添加大题分组 |
| PUT | `/api/exams/{uuid}/sections/{sectionId}` | 修改分组标题 / 排序 |
| DELETE | `/api/exams/{uuid}/sections/{sectionId}` | 删除分组 |
| POST | `/api/exams/{uuid}/sections/{sectionId}/items` | 向分组添加题目 |
| PUT | `/api/exams/{uuid}/sections/{sectionId}/items/{itemId}` | 修改分值 / 排序 |
| DELETE | `/api/exams/{uuid}/sections/{sectionId}/items/{itemId}` | 从分组移除题目 |

### 4.4 导出

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/exams/{uuid}/export` | 触发导出（返回任务 ID） |
| GET | `/api/exams/{uuid}/export/{taskId}` | 查询导出状态 / 下载文件 |

---

## 5. Web 前端架构

### 5.1 技术栈

| 技术 | 选型 | 理由 |
|------|------|------|
| 框架 | Vue 3 + TypeScript | 与 Electron 前端一致，降低维护成本 |
| 构建 | Vite | 零配置，HMR 极快 |
| 状态管理 | Pinia | 与现有 store 模式一致 |
| UI 组件库 | Element Plus 或 Naive UI | 成熟的 PC 端组件库，提供 Table/Drawer/Dialog |
| LaTeX 渲染 | KaTeX | 复用 Electron 端同一套渲染逻辑 |
| 路由 | Vue Router 4 | SPA 路由 |
| HTTP 客户端 | Axios | 统一请求拦截与 token 注入 |

### 5.2 页面结构

```
/login              → 登录页
/dashboard          → 首页（概览统计）
/bank               → 题库浏览（私人 + 公共）
/bank/:uuid         → 题目详情
/exams              → 我的试卷列表
/exams/new          → 新建试卷（组卷工作台）
/exams/:uuid        → 编辑试卷
/exams/:uuid/preview → 试卷预览
```

### 5.3 组卷工作台交互设计

```
┌──────────────────────────────────────────────────────┐
│  ← 返回试卷列表          试卷标题: [____]    [保存]    │
├─────────────────────┬────────────────────────────────┤
│                     │                                │
│   题库面板            │   试卷编排区                     │
│   ┌───────────┐     │                                │
│   │ 搜索/筛选    │     │   一、选择题 (每题5分)           │
│   ├───────────┤     │   ┌──────────────────────┐    │
│   │ 题目列表    │     │   │ 1. 已知函数 f(x)=...  │    │
│   │ [+] 添加   │ ──→ │   │    分值: [5] 分       │    │
│   │ [+] 添加   │     │   └──────────────────────┘    │
│   │ ...       │     │   ┌──────────────────────┐    │
│   └───────────┘     │   │ 2. 求解方程...         │    │
│                     │   │    分值: [5] 分       │    │
│                     │   └──────────────────────┘    │
│                     │                                │
│                     │   二、解答题 (每题15分)           │
│                     │   ┌──────────────────────┐    │
│                     │   │ 3. 证明...             │    │
│                     │   │    分值: [15] 分      │    │
│                     │   └──────────────────────┘    │
│                     │                                │
│                     │   ─────────────────────────── │
│                     │   总分: 130 分                  │
├─────────────────────┴────────────────────────────────┤
│  [预览试卷]   [导出 PDF]   [导出 Word]                  │
└──────────────────────────────────────────────────────┘
```

**核心交互：**
- 左侧题库面板可搜索、筛选，点击 `[+]` 将题目添加到右侧试卷中
- 右侧试卷编排区支持拖拽排序、大题分组管理、分值编辑
- 实时计算总分，标注分值异常（如与同类题不一致）
- 支持从公共题库和私人题库混合选题

---

## 6. 公共题库机制

### 6.1 发布流程

1. 管理员 / 教研组长在 Electron 端将优质题目标记为"公开"
2. 后端在 `q_public_question` 表插入一条记录
3. Web 端查询公共题库时，JOIN `q_public_question` 获取已公开题目

### 6.2 权限模型

| 角色 | 私人题库 | 公共题库 | 组卷 | 发布公共题 |
|------|---------|---------|------|-----------|
| 普通教师 | ✅ 只看自己的 | ✅ 只读 | ✅ | ❌ |
| 教研组长 | ✅ 只看自己的 | ✅ 只读 | ✅ | ✅ |
| 管理员 | ✅ 看所有人 | ✅ 读写 | ✅ | ✅ |

### 6.3 题目引用保护

- `q_exam_item.question_id` → `q_question.id` 使用 `ON DELETE RESTRICT`
- 当一道题被至少一份试卷引用时，**不可删除该题目**
- 删除题目前需先从所有引用它的试卷中移除

---

## 7. 部署方案

### 7.1 开发阶段

```yaml
# docker-compose.web.yml
services:
  web-frontend:
    build: ./web-frontend
    ports:
      - "3000:80"
    depends_on:
      - gateway-service

  exam-service:
    build:
      context: ./backend
      args:
        SERVICE_NAME: exam-service
    ports:
      - "8093:8093"
    depends_on:
      mysql:
        condition: service_healthy
      nacos:
        condition: service_healthy
```

### 7.2 生产阶段

```
                          ┌──────────┐
      浏览器 ──────────── → │  Nginx   │
                          │ :443     │
                          └────┬─────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
         /api/*           /web/*            /ws/*
              │                │                │
              ▼                ▼                ▼
        gateway-service   静态文件 (CDN)    WebSocket
        :8080             Vue SPA
```

- Web 前端构建为纯静态文件，部署到 Nginx / CDN
- 所有 API 请求通过 Nginx 转发到 gateway-service
- CORS 在 gateway 层统一配置

---

## 8. 实施路线图

### Phase 1 — 基础组卷（2~3 周）

- [ ] 创建 `exam-service` 项目骨架（Spring Boot + MyBatis-Plus）
- [ ] 实现 `q_exam`, `q_exam_section`, `q_exam_item` 表 + CRUD API
- [ ] 创建 `web-frontend` Vue 3 项目
- [ ] 实现登录页、题库浏览页（复用现有 question API）
- [ ] 实现组卷工作台（分组 + 添加题目 + 分值编辑）
- [ ] 实现试卷预览页面（LaTeX 渲染）

### Phase 2 — 导出与公共题库（1~2 周）

- [ ] 实现 PDF 导出（基于 Puppeteer / WeasyPrint）
- [ ] 实现 Word 导出（基于 docx4j / python-docx）
- [ ] 新增 `q_public_question` 表 + 公共题库查询 API
- [ ] Electron 端增加"发布到公共题库"按钮
- [ ] Web 端增加公共题库标签页

### Phase 3 — 增强功能（持续迭代）

- [ ] 智能推题：根据已选题目推荐互补的题目（知识点覆盖、难度分布）
- [ ] 试卷模板：保存常用试卷结构为模板，一键套用
- [ ] 组卷约束：设定知识点覆盖率、难度分布目标，自动推荐
- [ ] 试卷统计：分值分布图、难度曲线、知识点覆盖热力图
- [ ] 在线考试：学生作答 + 自动批改（选择题）+ 教师批改（主观题）

---

## 9. 共享认证方案

### 9.1 Token 互通

Electron 端和 Web 端使用 **同一套 auth-service**，JWT token 格式一致：

```json
{
  "sub": "teacher001",
  "roles": ["TEACHER"],
  "iss": "qforge-auth",
  "exp": 1741200000
}
```

- Electron 端：token 存储在 `localStorage`，通过 IPC 传递给 renderer
- Web 端：token 存储在 `localStorage` 或 `httpOnly cookie`
- Gateway 统一校验 `X-Auth-User` header（如现有机制）

### 9.2 跨端登录

用户只需注册一个账号，在 Electron 端和 Web 端均可登录使用，数据完全互通。

---

## 10. 风险与决策点

| 风险 | 影响 | 缓解方案 |
|------|------|---------|
| Web 端 LaTeX 渲染性能 | 大量公式时页面卡顿 | 虚拟滚动 + 懒渲染 + Web Worker 预处理 |
| 并发组卷冲突 | 两人同时编辑同一试卷 | 乐观锁（version 字段）+ 提示冲突 |
| 题目被删除后试卷引用断裂 | 试卷展示异常 | FK RESTRICT 阻止删除 + 软删除标记 |
| PDF 导出排版一致性 | 不同浏览器渲染差异 | 服务端 Puppeteer 统一渲染 |
| 公共题库审核成本 | 低质量题目进入公共库 | 分级审核 + 举报机制 |
