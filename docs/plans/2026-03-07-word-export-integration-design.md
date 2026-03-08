# Word 导出 & 组卷功能集成设计

> **版本**: 2026-03-07 · **状态**: 草案 · **作者**: Copilot

---

## 目录

1. [需求概述](#1-需求概述)
2. [系统架构总览](#2-系统架构总览)
3. [数据库 Schema 设计](#3-数据库-schema-设计)
4. [后端 API 设计](#4-后端-api-设计)
5. [Python Word 生成微服务](#5-python-word-生成微服务)
6. [前端组卷视图设计](#6-前端组卷视图设计)
7. [文件下载流程](#7-文件下载流程)
8. [实施路线图](#8-实施路线图)

---

## 1. 需求概述

### 1.1 用户故事

> 作为录题教师，我希望从题库中选取若干题目，编排成一份包含完整题型分区的试卷，
> 预览排版效果后，一键导出为 Word 文档（`.docx`）。

### 1.2 核心功能

| 编号 | 功能 | 说明 |
|------|------|------|
| F1 | **添加题目** | 通过搜索/浏览题库，将已录入题目（`READY` 状态）添加到试卷中 |
| F2 | **创建题型区** | 每个区块拥有自定义标题（如 "一、单选题"），但不强制区块内全部是某题型 |
| F3 | **调整顺序** | 区块级拖拽排序 + 区块内题目拖拽排序 |
| F4 | **前端预渲染** | 添加题目后即时在前端预览排版效果（复用 KaTeX + stem-xml 渲染管线） |
| F5 | **自动编号** | 每题根据全局顺序自动标注序号（如 1. 2. 3.），区块内连续 |
| F6 | **导出 Word** | 前端将组卷结构提交后端，后端生成 `.docx` 文件返回给客户端下载 |

### 1.3 设计约束

- **题目不复制**：组卷只保存题目 UUID 引用 → 题目内容更新后试卷自动反映最新版本
- **不限制题型**：区块标题是纯文本，与题目的标签/题型无绑定
- **单人试卷**：当前阶段不涉及多人协作，试卷归属 `owner_user`
- **Word 生成选用 Python**：复用已验证的 `python-docx` + `latex2word` 管线（纯 Python，无 Office 依赖）

---

## 2. 系统架构总览

### 2.1 新增组件

```
┌─────────────────────────────────────────────────────────────────┐
│  Electron Frontend (frontend-vue: Vue 3 + TypeScript)            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────────┐ │
│  │ 录题中心  │  │  题 库    │  │ 试卷解析  │  │  组卷中心 (NEW)  │ │
│  │EntryView │  │BankView  │  │ExamParse │  │ ComposeView.vue │ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────────┘ │
│     Pinia store: examCompose.ts (NEW)                            │
└─────────┬───────────────────────────────────────────────────────┘
          │ HTTP (via Electron IPC → main process fetch proxy)
┌─────────▼───────────────────────────────────────────────────────┐
│  gateway-service (:8080)                                         │
│  NEW route: /api/exam-compose/** → lb://question-service         │
│  NEW route: /api/export/**       → lb://export-sidecar           │
└─────────┬──────────────────────────┬────────────────────────────┘
          │                          │
┌─────────▼──────────┐   ┌──────────▼─────────────────────────────┐
│  question-service   │   │  export-sidecar (NEW)                  │
│  (:8089)            │   │  Python FastAPI (:8092)                 │
│  - 组卷 CRUD API    │   │  - 注册 Nacos，gateway lb:// 发现       │
│  - 组装题目数据      │   │  - 调用 question-service 内部 API       │
│  - 新增内部 API:    │   │    获取组卷结构 + 题目完整数据           │
│    /internal/       │   │  - stem-xml parse → docx 渲染           │
│    compose-export   │   │  - 返回 .docx 二进制流                  │
└─────────────────────┘   └────────────────────────────────────────┘
                                     │
                                     │ Nacos 注册 (nacos-sdk-python)
                                     ▼
                               Nacos (:8848)
```

> **微服务数据主权原则**：export-sidecar **不直连 MySQL**。题目数据由
> question-service 独占管理，export-sidecar 通过 HTTP 调用
> question-service 的内部 API 获取渲染所需的完整 JSON 数据。

### 2.2 方案选型说明

**为什么选择 Python sidecar 而不是 Java POI？**

| 考量 | Python sidecar | Java Apache POI |
|------|----------------|-----------------|
| **LaTeX → OMML** | `latex2word` 纯 Python，已验证 | 需 MML2OMML.xsl + Saxon，依赖链复杂 |
| **开发成本** | 复用 `word_export_test.py` 95% 代码 | 完全重写 |
| **维护一致性** | 与 POC 脚本保持一致，调试方便 | 两套渲染逻辑，行为可能分化 |
| **部署** | Docker sidecar，与现有 compose 同栈 | 嵌入 question-service，不侵入 |
| **性能** | 单次生成 < 2s（实测），足够 | POI 相近 |

**结论**：新增一个轻量 Python FastAPI 服务（`export-sidecar`），独立 Docker 容器，**通过 HTTP 调用 question-service** 获取题目数据，按请求生成 `.docx` 返回二进制流。

### 2.3 服务注册（Nacos 集成）

export-sidecar **注册 Nacos**，与现有 Java 微服务采用相同的服务发现机制，gateway 通过 `lb://` 负载均衡路由：

#### 2.3.1 Python 端 Nacos 注册

使用 `nacos-sdk-python`（官方 Python SDK）在 FastAPI 启动时注册实例：

```python
# config.py
import nacos

NACOS_SERVER = os.getenv("NACOS_SERVER_ADDR", "localhost:8848")
SERVICE_NAME = "export-sidecar"
SERVICE_PORT = int(os.getenv("SERVICE_PORT", "8092"))
SERVICE_IP = os.getenv("SERVICE_IP", "")
# question-service 内部地址（Docker 网络内通信，不经 gateway）
QUESTION_SERVICE_URL = os.getenv("QUESTION_SERVICE_URL", "http://question-service:8089")

def get_nacos_client():
    return nacos.NacosClient(NACOS_SERVER, namespace="")
```

```python
# main.py — 启动与关闭钩子
import socket
from config import get_nacos_client, SERVICE_NAME, SERVICE_PORT, SERVICE_IP

_nacos_client = None

@app.on_event("startup")
async def register_nacos():
    global _nacos_client
    _nacos_client = get_nacos_client()
    ip = SERVICE_IP or socket.gethostbyname(socket.gethostname())
    _nacos_client.add_naming_instance(SERVICE_NAME, ip, SERVICE_PORT)
    # 心跳由 nacos-sdk-python 自动发送 (默认 5s 间隔)

@app.on_event("shutdown")
async def deregister_nacos():
    if _nacos_client:
        ip = SERVICE_IP or socket.gethostbyname(socket.gethostname())
        _nacos_client.remove_naming_instance(SERVICE_NAME, ip, SERVICE_PORT)
```

#### 2.3.2 Gateway 路由（lb:// 负载均衡）

```yaml
# gateway-service application.yml 新增
- id: export-sidecar-route
  uri: lb://export-sidecar       # 通过 Nacos 发现，支持多实例
  predicates:
    - Path=/api/export/**
```

#### 2.3.3 question-service 新增内部 API

question-service 新增一组 **内部端点**，供 export-sidecar 在 Docker 网络内直接调用（不经 gateway）：

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/internal/compose-export/{composeUuid}` | 返回组卷完整数据（含区块、题目、答案、资产） |
| `POST` | `/internal/questions-export` | 按 UUID 列表批量返回题目完整数据（含答案、资产） |

这些内部 API **不经过 gateway**（无 `/api/` 前缀），由 export-sidecar 直接通过
`http://question-service:8089/internal/...` 调用。可通过 Spring Security 配置
仅允许内网访问，或校验内部 token。

```java
// InternalExportController.java (question-service)
@RestController
@RequestMapping("/internal")
public class InternalExportController {

    @GetMapping("/compose-export/{composeUuid}")
    public ComposeExportDto getComposeForExport(
            @PathVariable String composeUuid,
            @RequestHeader("X-Auth-User") String user) {
        // 返回完整组卷数据 (sections + items + question stem/answers/assets)
    }

    @PostMapping("/questions-export")
    public List<QuestionExportDto> getQuestionsForExport(
            @RequestBody QuestionUuidsRequest request) {
        // 批量查询题目完整数据
    }
}
```

---

## 3. 数据库 Schema 设计

### 3.1 组卷数据模型

```
q_exam_compose (试卷)
  ├── q_exam_compose_section (题型区/区块) × N
  │     └── q_exam_compose_item (区块内题目引用) × M
```

### 3.2 DDL

```sql
-- =====================================================================
-- 组卷功能 (Exam Compose) — 2026-03-07
-- =====================================================================

CREATE TABLE IF NOT EXISTS q_exam_compose (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    compose_uuid    CHAR(36)      NOT NULL UNIQUE,
    owner_user      VARCHAR(128)  NOT NULL,
    title           VARCHAR(255)  NOT NULL DEFAULT '未命名试卷',
    description     VARCHAR(1024) NULL     COMMENT '试卷描述/备注',
    status          VARCHAR(32)   NOT NULL DEFAULT 'DRAFT'
                    COMMENT 'DRAFT / PUBLISHED',
    total_questions INT           NOT NULL DEFAULT 0
                    COMMENT '冗余统计：总题数',
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ec_owner (owner_user, deleted, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT '组卷 — 试卷主表';

CREATE TABLE IF NOT EXISTS q_exam_compose_section (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    compose_id      BIGINT        NOT NULL,
    section_title   VARCHAR(255)  NOT NULL COMMENT '区块标题，如 "一、单选题"',
    sort_order      INT           NOT NULL DEFAULT 0,
    question_count  INT           NOT NULL DEFAULT 0
                    COMMENT '冗余统计：本区块题数',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ecs_compose (compose_id, sort_order),
    CONSTRAINT fk_ecs_compose
        FOREIGN KEY (compose_id) REFERENCES q_exam_compose(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT '组卷 — 题型区块';

CREATE TABLE IF NOT EXISTS q_exam_compose_item (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    section_id      BIGINT        NOT NULL,
    question_uuid   CHAR(36)      NOT NULL COMMENT '引用 q_question.question_uuid',
    sort_order      INT           NOT NULL DEFAULT 0,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_eci_section (section_id, sort_order),
    CONSTRAINT fk_eci_section
        FOREIGN KEY (section_id) REFERENCES q_exam_compose_section(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT '组卷 — 区块内题目引用';
```

### 3.3 数据关系说明

```
q_exam_compose (1) ──< q_exam_compose_section (N)
                              │
                              └──< q_exam_compose_item (M)
                                         │
                                         └──→ q_question (ref by question_uuid)
```

- **不复制题目内容**：`q_exam_compose_item` 仅存 `question_uuid`，渲染时实时查 `q_question` 及关联表
- **级联删除**：删除试卷 → 级联删除区块 → 级联删除条目（均为 `ON DELETE CASCADE`）
- **冗余统计**：`total_questions` 和 `question_count` 在增减题目时同步更新，避免每次 COUNT

---

## 4. 后端 API 设计

### 4.1 路由规划

Gateway 新增路由前缀：

| 路由前缀 | 目标服务 | 说明 |
|----------|----------|------|
| `/api/exam-compose/**` | question-service | 组卷 CRUD |
| `/api/export/**` | export-sidecar | Word 生成 & 下载 |

### 4.2 question-service 组卷 API

#### 4.2.1 试卷 CRUD

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/exam-compose` | 创建试卷 |
| `GET` | `/api/exam-compose` | 我的试卷列表 |
| `GET` | `/api/exam-compose/{composeUuid}` | 试卷详情（含区块 + 题目完整数据） |
| `PUT` | `/api/exam-compose/{composeUuid}` | 更新试卷标题/描述 |
| `DELETE` | `/api/exam-compose/{composeUuid}` | 删除试卷（逻辑删除） |

#### 4.2.2 区块管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/exam-compose/{composeUuid}/sections` | 添加区块 |
| `PUT` | `/api/exam-compose/{composeUuid}/sections/{sectionId}` | 更新区块标题 |
| `DELETE` | `/api/exam-compose/{composeUuid}/sections/{sectionId}` | 删除区块及其内所有题目引用 |
| `PUT` | `/api/exam-compose/{composeUuid}/sections/reorder` | 调整区块顺序 |

#### 4.2.3 题目引用管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/exam-compose/{composeUuid}/sections/{sectionId}/items` | 添加题目到区块 |
| `DELETE` | `/api/exam-compose/{composeUuid}/sections/{sectionId}/items/{itemId}` | 从区块移除题目 |
| `PUT` | `/api/exam-compose/{composeUuid}/sections/{sectionId}/items/reorder` | 调整题目顺序 |
| `POST` | `/api/exam-compose/{composeUuid}/sections/{sectionId}/items/move` | 将题目移入另一个区块 |

#### 4.2.4 详细请求/响应定义

**创建试卷** `POST /api/exam-compose`

```json
// Request
{
  "title": "2022 年高考数学模拟卷",
  "description": "适用于高三冲刺阶段"
}
// Response 201
{
  "composeUuid": "a1b2c3d4-...",
  "title": "2022 年高考数学模拟卷",
  "status": "DRAFT"
}
```

**试卷详情** `GET /api/exam-compose/{composeUuid}`

> 返回完整的嵌套结构，包含每道题目的渲染所需数据。

```json
{
  "composeUuid": "a1b2c3d4-...",
  "title": "2022 年高考数学模拟卷",
  "description": "...",
  "status": "DRAFT",
  "totalQuestions": 12,
  "sections": [
    {
      "sectionId": 1,
      "sectionTitle": "一、单选题",
      "sortOrder": 0,
      "items": [
        {
          "itemId": 101,
          "sortOrder": 0,
          "questionUuid": "550e8400-...",
          "question": {
            "questionUuid": "550e8400-...",
            "stemText": "<stem>...</stem>",
            "mainTags": [{"categoryCode":"SUBJECT","tagName":"数学"}],
            "secondaryTags": ["高考","选择题"],
            "difficulty": 0.65,
            "answers": [
              {
                "answerUuid": "660e8400-...",
                "latexText": "<answer>...</answer>",
                "sortOrder": 1
              }
            ],
            "assets": [
              {
                "refKey": "img-1",
                "assetType": "INLINE_IMAGE",
                "imageData": "data:image/png;base64,..."
              }
            ]
          }
        }
      ]
    },
    {
      "sectionId": 2,
      "sectionTitle": "二、填空题",
      "sortOrder": 1,
      "items": [ ... ]
    }
  ]
}
```

> **注意**：`question` 内嵌对象中的 `assets.imageData` 可能较大。前端应在初次加载后缓存，不重复拉取。当试卷题量很大时，可考虑改为分区块懒加载（Phase 2 优化）。

**添加题目到区块** `POST /api/exam-compose/{composeUuid}/sections/{sectionId}/items`

```json
// Request
{
  "questionUuids": [
    "550e8400-...",
    "660e8400-..."
  ]
}
// Response 200
{
  "added": 2,
  "sectionQuestionCount": 5,
  "totalQuestions": 12
}
```

**调整区块排序** `PUT /api/exam-compose/{composeUuid}/sections/reorder`

```json
// Request — 传入完整的有序 sectionId 数组
{
  "sectionIds": [2, 1, 3]
}
// Response 200
{ "success": true }
```

**调整题目排序** `PUT /api/exam-compose/{composeUuid}/sections/{sectionId}/items/reorder`

```json
// Request — 传入完整的有序 itemId 数组
{
  "itemIds": [103, 101, 102]
}
// Response 200
{ "success": true }
```

### 4.3 export-sidecar API

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/export/compose/{composeUuid}/word` | 生成并下载试卷 Word 文档 |
| `POST` | `/api/export/questions/word` | 按 UUID 列表导出散题（不含区块结构） |
| `GET`  | `/api/export/health` | 健康检查（Nacos 心跳 + gateway 健康探测） |

#### 4.3.1 导出试卷 Word

`POST /api/export/compose/{composeUuid}/word`

**Header**: `X-Auth-User: {username}` （由网关注入）

**请求体**（可选覆盖参数）：

```json
{
  "includeAnswers": true,
  "pageBreakBetweenQA": true,
  "answerPosition": "AFTER_EACH_QUESTION"
}
```

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `includeAnswers` | `boolean` | `true` | 是否包含答案 |
| `pageBreakBetweenQA` | `boolean` | `true` | 题与答之间是否分页 |
| `answerPosition` | `enum` | `AFTER_ALL` | `AFTER_ALL`（末尾统一答案页）/ `AFTER_EACH_QUESTION`（每题后跟答案） |

**响应**：`200 OK`，`Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document`

```
Content-Disposition: attachment; filename="2022年高考数学模拟卷.docx"
(binary stream)
```

**内部流程**：
1. export-sidecar 调用 question-service 内部 API `GET /internal/compose-export/{uuid}` 获取组卷结构 + 题目完整数据
2. 按区块顺序遍历：渲染区块标题 → 遍历区块内题目 → 解析 stem-xml → 调用 Renderer 管线 → 写入 Document
3. 如需答案页：分页后按相同顺序渲染 answer-xml
4. 返回 `.docx` 二进制流

#### 4.3.2 导出散题 Word

`POST /api/export/questions/word`

```json
{
  "questionUuids": ["550e8400-...", "660e8400-..."],
  "includeAnswers": true
}
```

**响应**：同上，直接返回 `.docx` 二进制流，无区块结构。

---

## 5. Python Word 生成微服务

### 5.1 项目结构

```
backend/export-sidecar/
  ├── Dockerfile
  ├── requirements.txt
  ├── main.py                # FastAPI 入口 + Nacos 注册
  ├── config.py              # Nacos / 应用配置
  ├── client/
  │   ├── __init__.py
  │   └── question_client.py # HTTP 调用 question-service 内部 API
  ├── models/
  │   ├── __init__.py
  │   ├── question.py        # QuestionData, AssetData, AnswerData
  │   └── compose.py         # ComposeData, SectionData, ItemData
  ├── parser/
  │   ├── __init__.py
  │   ├── nodes.py           # AST Node 类型
  │   ├── stem_parser.py     # stem-xml / answer-xml 解析器
  │   └── latex_engine.py    # LaTeX → OMML 引擎
  ├── renderer/
  │   ├── __init__.py
  │   ├── context.py         # RenderContext
  │   ├── paragraph.py       # ParagraphRenderer
  │   ├── image.py           # ImageRenderer (含 Pillow 尺寸计算)
  │   ├── choices.py         # ChoicesRenderer (3-tier 布局)
  │   ├── blanks.py          # BlanksRenderer
  │   ├── answer_area.py     # AnswerAreaRenderer
  │   └── registry.py        # 按 NodeType 注册 renderer
  └── assembler/
      ├── __init__.py
      └── document.py        # DocumentAssembler (build_compose / build_questions)
```

### 5.2 从 word_export_test.py 迁移计划

当前 POC 脚本 `backend/scripts/word_export_test.py` 约 960 行，实现了完整的渲染管线。迁移策略：

| POC 组件 | 迁移目标 | 改动 |
|----------|----------|------|
| §1 Data Model (`QuestionData` 等) | `models/question.py` | 无变化 |
| §2 DB Access (`fetch_question`) | `client/question_client.py` | **重写：SQL → HTTP 调用 question-service** |
| §3 Node AST | `parser/nodes.py` | 无变化 |
| §4 XML Parser | `parser/stem_parser.py` | 无变化 |
| §5 LatexEngine | `parser/latex_engine.py` | 无变化 |
| §6 RenderContext | `renderer/context.py` | 无变化 |
| §7 所有 Renderer | `renderer/*.py` | 拆文件，无逻辑变化 |
| §8 DocumentAssembler | `assembler/document.py` | 新增 `build_compose()`，复用 `build_exam()` 核心逻辑 |
| §9 CLI | 移除 | 由 FastAPI endpoint 替代 |

### 5.3 核心 FastAPI 端点（伪代码）

```python
# main.py
from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import StreamingResponse
from db.compose_repo import fetch_compose_full
from db.question_repo import fetch_questions_by_uuids
from assembler.document import DocumentAssembler
import io, urllib.parse

app = FastAPI(title="QForge Export Sidecar")

@app.post("/api/export/compose/{compose_uuid}/word")
async def export_compose_word(
    compose_uuid: str,
    x_auth_user: str = Header(..., alias="X-Auth-User"),
    body: ExportOptions = ExportOptions()
):
    # 1. 调用 question-service 内部 API 获取组卷完整数据
    compose = await question_client.fetch_compose_for_export(
        compose_uuid, x_auth_user
    )
    if not compose:
        raise HTTPException(404, "试卷不存在")

    # 2. 组装 Document
    assembler = DocumentAssembler()
    buf = io.BytesIO()
    assembler.build_compose(compose, buf, body)
    buf.seek(0)

    # 3. 返回二进制流
    filename = urllib.parse.quote(f"{compose.title}.docx")
    return StreamingResponse(
        buf,
        media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        headers={
            "Content-Disposition": f"attachment; filename*=UTF-8''{filename}"
        }
    )

@app.get("/api/export/health")
async def health():
    return {"status": "ok"}
```

### 5.4 `build_compose()` 方法设计

基于 POC 的 `build_exam()` 扩展：

```python
def build_compose(self, compose, questions_map, output, options):
    doc = Document()
    self._setup_styles(doc)
    ctx = RenderContext(doc)

    # 试卷标题 (居中, 二号字)
    title_para = doc.add_paragraph()
    title_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = title_para.add_run(compose.title)
    run.font.size = Pt(22)
    run.bold = True

    if compose.description:
        desc_para = doc.add_paragraph(compose.description)
        desc_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
        desc_para.runs[0].font.size = Pt(10.5)
        desc_para.runs[0].font.color.rgb = RGBColor(0x66, 0x66, 0x66)

    doc.add_paragraph()  # 空行

    global_seq = 0  # 全局题号

    for section in compose.sections:
        # 区块标题 (加粗, 四号字, 如 "一、单选题")
        sec_para = doc.add_paragraph()
        run = sec_para.add_run(section.section_title)
        run.bold = True
        run.font.size = Pt(14)

        for item in section.items:
            global_seq += 1
            qdata = questions_map.get(item.question_uuid)
            if not qdata:
                continue  # 题目已删除，跳过

            # 渲染题号前缀
            prefix = f"{global_seq}. "
            # 解析 stem-xml → AST → 调用 renderer 管线
            self._render_question_with_prefix(ctx, qdata, prefix)

    # 答案页
    if options.include_answers:
        doc.add_page_break()
        ans_title = doc.add_paragraph()
        run = ans_title.add_run("参考答案")
        run.bold = True
        run.font.size = Pt(16)
        ans_title.alignment = WD_ALIGN_PARAGRAPH.CENTER

        global_seq = 0
        for section in compose.sections:
            sec_para = doc.add_paragraph()
            run = sec_para.add_run(section.section_title)
            run.bold = True

            for item in section.items:
                global_seq += 1
                qdata = questions_map.get(item.question_uuid)
                if not qdata or not qdata.answers:
                    continue
                self._render_answer_with_prefix(ctx, qdata, f"{global_seq}. ")

    doc.save(output)
```

### 5.5 Docker 部署

```dockerfile
# backend/export-sidecar/Dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8092
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8092"]
```

```txt
# requirements.txt
fastapi==0.115.0
uvicorn[standard]==0.30.0
python-docx==1.2.0
latex2word==1.1
latex2mathml==3.77.0
lxml==5.3.2
Pillow==12.1.0
httpx==0.28.1                  # HTTP 调用 question-service 内部 API
nacos-sdk-python==1.0.0        # Nacos 服务注册
```

docker-compose.yml 新增：

```yaml
export-sidecar:
  build:
    context: ./export-sidecar
    dockerfile: Dockerfile
  container_name: qforge-export-sidecar
  ports:
    - "8092:8092"
  environment:
    - NACOS_SERVER_ADDR=nacos:8848
    - SERVICE_PORT=8092
    - QUESTION_SERVICE_URL=http://question-service:8089
  depends_on:
    - nacos
    - question-service
  networks:
    - qforge-net
```

---

## 6. 前端组卷视图设计 (Vue 3 + TypeScript)

> **重要说明**：本项目主力前端为 `frontend-vue/`（Vue 3.5 + TypeScript + Pinia +
> Vue Router + electron-vite），**不是** `frontend/`（旧版 vanilla JS demo）。
> 以下所有前端设计均基于 Vue 3 组件化架构。

### 6.1 视图架构

遵循现有 Vue Router 子路由模式（`AppShell.vue` 内的 tab 切换），新增第 4 个路由：

```typescript
// router/index.ts — 新增路由
{
  path: '/compose',
  name: 'compose',
  component: () => import('../views/ComposeView.vue')
}
```

`AppShell.vue` nav bar 新增"组卷中心"tab，router-link 指向 `/compose`。

### 6.2 视图布局

```
┌──────────────────────────────────────────────────────────────────────┐
│  组卷中心                                                             │
├──────────────┬───────────────────────────────────────────────────────┤
│  试卷列表     │  试卷编辑区                                            │
│  (左栏 280px) │  (右栏 flex:1)                                        │
│              │                                                       │
│ [+ 新建试卷]  │  试卷标题: [___________________] [导出 Word]            │
│              │  描述:     [___________________]                       │
│ ┌──────────┐ │                                                       │
│ │ 高考模拟  │ │  ┌──────────────────────────────────────────────┐     │
│ │ 2022-03   │ │  │ [一、单选题]                      [+ 添加题目]│     │
│ ├──────────┤ │  │   1. 已知集合 M={x|...}              [×] [≡] │     │
│ │ 期末测试  │ │  │   2. 设函数 f(x)=...                 [×] [≡] │     │
│ │ 2022-02   │ │  ├──────────────────────────────────────────────┤     │
│ └──────────┘ │  │ [二、填空题]                      [+ 添加题目]│     │
│              │  │   3. 若 $\sin\alpha=...$            [×] [≡]  │     │
│              │  ├──────────────────────────────────────────────┤     │
│              │  │           [+ 添加区块]                        │     │
│              │  └──────────────────────────────────────────────┘     │
│              │                                                       │
│              │  ─ ─ ─ 题目预览区 ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─      │
│              │  点击某题后显示完整渲染预览（含 KaTeX + 图片）           │
│              │                                                       │
├──────────────┴───────────────────────────────────────────────────────┤
│  ───────── 题库搜索面板 (可展开收起) ─────────                        │
│  [搜索关键词____] [年级 ▼] [知识点 ▼] [难度 ▼]                       │
│  搜索结果:  □ 题目A  □ 题目B  □ 题目C   [添加选中到当前区块]          │
└──────────────────────────────────────────────────────────────────────┘
```

### 6.3 新增文件清单

```
frontend-vue/src/renderer/src/
  ├── views/
  │   └── ComposeView.vue          # 组卷中心主视图 (NEW)
  ├── stores/
  │   └── examCompose.ts           # 组卷 Pinia store (NEW)
  ├── api/
  │   └── examCompose.ts           # 组卷 API 函数 (NEW)
  ├── components/
  │   ├── ComposeList.vue          # 左栏试卷列表 (NEW)
  │   ├── ComposeEditor.vue        # 右栏编辑区 (NEW)
  │   ├── ComposeSection.vue       # 单个区块编辑 (NEW)
  │   ├── ComposeItem.vue          # 区块内题目行 (NEW)
  │   ├── ComposeQuestionPicker.vue # 题库搜索添加面板 (NEW)
  │   └── LatexPreview.vue         # (已有，复用)
  └── composables/
      └── useQuestionSearch.ts     # 题库搜索 composable (NEW，从 BankView 提取)
```

### 6.4 Pinia Store 设计

新建 `stores/examCompose.ts`：

```typescript
// stores/examCompose.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as composeApi from '../api/examCompose'

export const useExamComposeStore = defineStore('examCompose', () => {
  // === State ===
  const composeList = ref<ComposeSummary[]>([])
  const activeComposeUuid = ref<string | null>(null)
  const composeDetail = ref<ComposeDetail | null>(null)
  const previewQuestionUuid = ref<string | null>(null)
  const loading = ref(false)
  const exporting = ref(false)

  // === Getters ===
  const activeCompose = computed(() =>
    composeList.value.find(c => c.composeUuid === activeComposeUuid.value)
  )

  // === Actions ===
  async function refreshList() { ... }
  async function createCompose(title: string) { ... }
  async function selectCompose(uuid: string) { ... }
  async function deleteCompose(uuid: string) { ... }
  async function updateTitle(uuid: string, title: string) { ... }

  // 区块操作
  async function addSection(title: string) { ... }
  async function updateSectionTitle(sectionId: number, title: string) { ... }
  async function deleteSection(sectionId: number) { ... }
  async function reorderSections(sectionIds: number[]) { ... }

  // 题目操作
  async function addItems(sectionId: number, questionUuids: string[]) { ... }
  async function removeItem(sectionId: number, itemId: number) { ... }
  async function reorderItems(sectionId: number, itemIds: number[]) { ... }

  // 导出
  async function exportWord() { ... }

  return {
    composeList, activeComposeUuid, composeDetail,
    previewQuestionUuid, loading, exporting, activeCompose,
    refreshList, createCompose, selectCompose, deleteCompose,
    updateTitle, addSection, updateSectionTitle, deleteSection,
    reorderSections, addItems, removeItem, reorderItems, exportWord
  }
})
```

### 6.5 API 层

新建 `api/examCompose.ts`，遵循现有 `api/question.ts` 的模式：

```typescript
// api/examCompose.ts
import { apiRequest } from './client'

export const listComposes = () =>
  apiRequest<ComposeSummary[]>('/api/exam-compose', 'GET')

export const getCompose = (uuid: string) =>
  apiRequest<ComposeDetail>(`/api/exam-compose/${uuid}`, 'GET')

export const createCompose = (body: { title: string; description?: string }) =>
  apiRequest<ComposeSummary>('/api/exam-compose', 'POST', body)

export const deleteCompose = (uuid: string) =>
  apiRequest<void>(`/api/exam-compose/${uuid}`, 'DELETE')

// ... sections, items, reorder 等 CRUD 函数
```

### 6.6 组件复用策略

| 现有组件/模块 | 复用方式 | 用于 |
|-------------|----------|------|
| `LatexPreview.vue` | 直接 `<LatexPreview>` | 题目预览渲染 |
| `useLatexRender.ts` | composable 调用 | KaTeX 渲染 |
| `stemXml.ts` | import 解析函数 | XML → 渲染树 |
| `imageRef.ts` | import 图片解析 | base64 图片引用 |
| `TagSection.vue` | 参考，但组卷中不需编辑标签 | — |
| `BankView.vue` 筛选逻辑 | 提取为 `useQuestionSearch.ts` composable | 题库搜索面板 |

### 6.7 拖拽排序

使用 `vuedraggable`（或 `@vueuse/integrations` 的拖拽）实现区块级 + 题目级拖拽：

```vue
<!-- ComposeEditor.vue 片段 -->
<draggable v-model="sections" item-key="sectionId"
           handle=".drag-handle" @end="onSectionReorder">
  <template #item="{ element: section }">
    <ComposeSection :section="section" />
  </template>
</draggable>
```

```vue
<!-- ComposeSection.vue 片段 -->
<draggable v-model="section.items" item-key="itemId"
           group="items" @end="onItemReorder">
  <template #item="{ element: item }">
    <ComposeItem :item="item" :seq="globalSeq(item)" />
  </template>
</draggable>
```

> **缓存策略**：题目 `assets.imageData` 可能较大。store 中使用 `Map<questionUuid, QuestionData>`
> 缓存已加载题目，避免切换区块时重复拉取。

---

## 7. 文件下载流程

### 7.1 端到端流程

```
Vue ComposeView          Electron main (index.ts)       Gateway        export-sidecar
     │                            │                        │                 │
     │ store.exportWord()         │                        │                 │
     │  → qforge.export           │                        │                 │
     │    .downloadFile()         │                        │                 │
     │───────────────────────────>│                        │                 │
     │  ipcRenderer.invoke(       │ fetch() + Bearer token │                 │
     │   'api:download-file')     │───────────────────────>│ validate JWT    │
     │                            │                        │────────────────>│
     │                            │                        │ X-Auth-User     │
     │                            │                        │<────────────────│
     │                            │                        │  200 + .docx    │
     │                            │<───────────────────────│                 │
     │                            │                        │                 │
     │                            │  dialog.showSaveDialog │                 │
     │                            │  → 用户选路径           │                 │
     │                            │  fs.writeFileSync()    │                 │
     │  { canceled, filePath }    │                        │                 │
     │<───────────────────────────│                        │                 │
     │  显示成功通知               │                        │                 │
```

### 7.2 Electron 主进程改动 (src/main/index.ts)

现有的 `api:request` IPC handler 只处理 JSON 响应。需要新增一个二进制下载 handler：

```typescript
// src/main/index.ts 新增
import { dialog } from 'electron'
import * as fs from 'fs'

ipcMain.handle('api:download-file', async (_event, { path, method, token, body }) => {
  const url = `${API_BASE_URL}${path}`
  const opts: RequestInit = {
    method: method || 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  }
  if (body) opts.body = JSON.stringify(body)

  const resp = await fetch(url, opts)
  if (!resp.ok) {
    const text = await resp.text()
    throw new Error(`Export failed: ${resp.status} ${text}`)
  }

  // 从 Content-Disposition 提取文件名
  const cd = resp.headers.get('content-disposition') || ''
  let filename = 'export.docx'
  const match = cd.match(/filename\*=UTF-8''(.+)/i) || cd.match(/filename="?(.+?)"?$/i)
  if (match) filename = decodeURIComponent(match[1])

  // 让用户选择保存路径
  const { canceled, filePath } = await dialog.showSaveDialog(mainWindow!, {
    defaultPath: filename,
    filters: [{ name: 'Word Documents', extensions: ['docx'] }],
  })
  if (canceled || !filePath) return { canceled: true }

  // 写文件
  const buffer = Buffer.from(await resp.arrayBuffer())
  fs.writeFileSync(filePath, buffer)

  return { canceled: false, filePath }
})
```

### 7.3 Preload 改动 (src/preload/index.ts)

```typescript
// src/preload/index.ts — 在 api 对象中新增 export 命名空间
const api = {
  // ... 现有 config, auth, api, credentials, screenshot ...
  export: {
    downloadFile: (path: string, method: string, token: string, body?: unknown) =>
      ipcRenderer.invoke('api:download-file', { path, method, token, body }),
  },
}
```

### 7.4 前端调用 (store action)

```typescript
// stores/examCompose.ts — exportWord action
async function exportWord() {
  if (!activeComposeUuid.value) return
  exporting.value = true
  try {
    const result = await window.qforge.export.downloadFile(
      `/api/export/compose/${activeComposeUuid.value}/word`,
      'POST',
      useAuthStore().token,
      { includeAnswers: true, answerPosition: 'AFTER_ALL' }
    )
    if (!result.canceled) {
      useNotificationStore().success(`试卷已导出: ${result.filePath}`)
    }
  } catch (err: any) {
    useNotificationStore().error(`导出失败: ${err.message}`)
  } finally {
    exporting.value = false
  }
}
```

---

## 8. 实施路线图

### Phase 1 — 后端基础（预计 2 天）

| 序号 | 任务 | 产出 |
|------|------|------|
| 1.1 | 执行 DDL，创建 3 张组卷表 | `init-schema.sql` 更新 |
| 1.2 | question-service 新增 Entity / Repository / DTO | `entity/`, `repository/`, `dto/` |
| 1.3 | 新增 `ExamComposeCommandService` + `ExamComposeController` | 对外 CRUD API 完成 |
| 1.4 | 新增 `InternalExportController` | 内部 API（供 export-sidecar 调用） |
| 1.5 | gateway 新增路由 `/api/exam-compose/**` + `/api/export/**` | `application.yml` 更新 |
| 1.6 | 接口联调测试 | curl / Postman 验证 |

### Phase 2 — Python export-sidecar + Nacos 集成（预计 2 天）

| 序号 | 任务 | 产出 |
|------|------|------|
| 2.1 | 从 `word_export_test.py` 拆分项目结构 | `export-sidecar/` 目录 |
| 2.2 | 实现 Nacos 注册（`nacos-sdk-python`）| `config.py` + `main.py` startup/shutdown |
| 2.3 | 实现 `question_client.py` HTTP 调用 question-service 内部 API | `client/question_client.py` |
| 2.4 | 实现 `build_compose()` 组卷渲染逻辑 | `assembler/document.py` |
| 2.5 | 实现 FastAPI 端点 (`/api/export/compose/{uuid}/word`) | `main.py` |
| 2.6 | Dockerfile + docker-compose 集成（端口 8092） | 容器化部署 |
| 2.7 | E2E 测试（curl 下载 .docx，Nacos 注册验证，Word 打开验证） | 验收通过 |

### Phase 3 — 前端组卷视图 Vue 3（预计 3 天）

| 序号 | 任务 | 产出 |
|------|------|------|
| 3.1 | `ComposeView.vue` 主视图 + Vue Router 子路由 `/compose` | 视图骨架 |
| 3.2 | `examCompose.ts` Pinia store + `api/examCompose.ts` API 层 | 状态管理 |
| 3.3 | `ComposeList.vue` 左栏试卷列表组件 | 列表 CRUD |
| 3.4 | `ComposeEditor.vue` + `ComposeSection.vue` + `ComposeItem.vue` | 编辑区组件 |
| 3.5 | 题目预览（复用 `LatexPreview.vue` + `useLatexRender.ts`） | 即时预览 |
| 3.6 | 拖拽排序（`vuedraggable`，区块级 + 题目级） | 交互完成 |
| 3.7 | `ComposeQuestionPicker.vue` 题库搜索面板 + `useQuestionSearch.ts` composable | 添加题目 |
| 3.8 | `AppShell.vue` 新增 tab + 样式 | 集成完成 |

### Phase 4 — 文件下载流程（预计 1 天）

| 序号 | 任务 | 产出 |
|------|------|------|
| 4.1 | `src/main/index.ts` 新增 `api:download-file` IPC handler | 二进制下载 |
| 4.2 | `src/preload/index.ts` 暴露 `export.downloadFile` | Bridge API |
| 4.3 | store `exportWord` action + UI 按钮状态管理 | 一键导出 |
| 4.4 | E2E 全链路测试 | 验收通过 |

### Phase 5 — 打磨优化（持续）

| 序号 | 任务 |
|------|------|
| 5.1 | 大试卷懒加载（分区块按需拉取题目数据） |
| 5.2 | 题目去重校验（同一题不重复添加） |
| 5.3 | 试卷模板（预设区块结构一键生成） |
| 5.4 | 导出选项面板（答案位置、纸张大小、页眉页脚等） |
| 5.5 | 试卷分享（`visibility` 字段，PRIVATE → PUBLIC） |

---

## 附录 A — 现有代码复用清单

| 现有代码 | 复用方式 | 用于 |
|----------|----------|------|
| `word_export_test.py` 全部 Renderer | 迁移到 export-sidecar | Word 渲染管线 |
| `LatexPreview.vue` | 直接 `<LatexPreview>` 组件 | 前端题目预览 |
| `useLatexRender.ts` composable | import 调用 | KaTeX 渲染 |
| `stemXml.ts` | import 解析函数 | 前端 XML 解析 |
| `imageRef.ts` | import 图片解析 | 前端图片引用 |
| `BankView.vue` 筛选逻辑 | 提取为 `useQuestionSearch.ts` composable | 组卷添加题目面板 |
| `ExamParseController` API 模式 | 参考结构 | 新 Controller |

## 附录 B — 安全考量

| 关注点 | 措施 |
|--------|------|
| 数据权限 | question-service 内部 API 校验 `owner_user = X-Auth-User`，仅允许导出自己的试卷 |
| 内部 API 保护 | `/internal/**` 端点仅允许 Docker 内网访问（不经 gateway，不暴露公网端口） |
| 资源滥用 | 限制单次导出最大题数（如 200 题），超限返回 `413` |
| Gateway 鉴权 | export 路由纳入 `JwtAuthGlobalFilter`，非公开路径 |
| 文件大小 | 单个 `.docx` 限制 50MB（图片较多时可能较大），超限返回 `413` |

## 附录 C — 组卷数据 JSON 交换格式完整示例

```json
{
  "composeUuid": "c1a2b3c4-d5e6-f7a8-b9c0-d1e2f3a4b5c6",
  "title": "2022 年高考数学模拟卷（理科）",
  "description": "适用于高三理科冲刺阶段",
  "status": "DRAFT",
  "totalQuestions": 22,
  "sections": [
    {
      "sectionId": 1,
      "sectionTitle": "一、选择题（本大题共 12 小题，每小题 5 分，共 60 分）",
      "sortOrder": 0,
      "questionCount": 12,
      "items": [
        {
          "itemId": 101,
          "sortOrder": 0,
          "questionUuid": "550e8400-e29b-41d4-a716-446655440000",
          "question": {
            "questionUuid": "550e8400-e29b-41d4-a716-446655440000",
            "stemText": "<stem version=\"1\"><p>已知集合 $M=\\{x|x^2-3x+2\\leq 0\\}$，$N=\\{x|x>1\\}$，则 $M\\cap N=$</p><choices><choice>$\\{2\\}$</choice><choice>$[1,2]$</choice><choice>$(1,2]$</choice><choice>$[2,+\\infty)$</choice></choices></stem>",
            "mainTags": [
              {"categoryCode": "MAIN_GRADE", "tagName": "高三"},
              {"categoryCode": "MAIN_KNOWLEDGE", "tagName": "集合"}
            ],
            "secondaryTags": ["高考", "选择题"],
            "difficulty": 0.85,
            "answers": [
              {
                "answerUuid": "a1a2a3a4-...",
                "latexText": "<answer version=\"1\"><p>C</p></answer>",
                "sortOrder": 1
              }
            ],
            "assets": []
          }
        }
      ]
    },
    {
      "sectionId": 2,
      "sectionTitle": "二、填空题（本大题共 4 小题，每小题 5 分，共 20 分）",
      "sortOrder": 1,
      "questionCount": 4,
      "items": []
    },
    {
      "sectionId": 3,
      "sectionTitle": "三、解答题（本大题共 6 小题，共 70 分）",
      "sortOrder": 2,
      "questionCount": 6,
      "items": []
    }
  ],
  "createdAt": "2026-03-07T10:00:00",
  "updatedAt": "2026-03-07T14:30:00"
}
```
