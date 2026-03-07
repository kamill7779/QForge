# QForge 前端 Vue 3 重构方案

> 日期: 2026-03-07  
> 状态: 设计阶段  
> 范围: 前端完全重构（Vanilla JS → Vue 3 + TypeScript + Electron）

---

## 一、现状分析

### 1.1 代码规模

| 文件 | 行数 | 职责 |
|------|------|------|
| `renderer.js` | 486（压缩行，展开约 2000+） | 全局控制器：登录、CRUD、编辑、渲染、事件 |
| `exam-parse-runtime.js` | 1348 | 试卷解析独立子系统 |
| `stem-xml.js` | 357 | XML 解析/序列化纯函数库 |
| `ocr-runtime.js` | 143 | WebSocket + OCR 事件运行时 |
| `image-runtime.js` | 129 | 图片引用管理 |
| `answer-runtime.js` | 113 | 答案提交数据准备 |
| `styles.css` | 2513 | 全局样式（无模块化） |
| `index.html` | 474 | 手写静态 HTML |
| `electron/main.js` | 440 | Electron 主进程 |
| **合计** | **~6400** | |

### 1.2 核心问题

#### A. 代码重复（最严重）

| 重复模式 | 出现位置 | 次数 |
|----------|---------|------|
| 结构化编辑器（block 收集/渲染/事件） | renderer.js(entry + bank) + exam-parse-runtime | ×3 |
| addStemBlock / addAnswerBlock | renderer.js(×2) + exam-parse-runtime | ×3 |
| 标签胶囊编辑器 (capsule + input + 空格添加) | renderer.js(stem/entry/bank) + exam-parse-runtime | ×4 |
| 主标签选择器 (select per category) | renderer.js(×3) + exam-parse-runtime | ×4 |
| 难度滑块 (range + badge + value) | renderer.js(×2) + exam-parse-runtime | ×3 |
| AI 分析状态面板 | renderer.js(entry + bank) | ×2 |
| 答案标签页切换 | renderer.js(completed + bank) | ×2 |
| ref 种子/构建函数 | image-runtime + answer-runtime | ×2 (3函数完全相同) |
| XML 解析 stem/answer 对称函数 | stem-xml.js 内部 | ×4对 |

#### B. 架构缺陷

1. **God File**: `renderer.js` 兼任路由、控制器、API、状态管理、DOM 渲染
2. **巨型全局 state**: 单一 `state` 对象 20+ 字段，无模块化拆分
3. **无响应式**: 状态变更 → 手动 `saveWorkspace()` + `renderAll()` 全量重绘
4. **IIFE 模块系统**: `window.*` 全局挂载，无 ESM import/export
5. **DOM 紧耦合**: 业务逻辑与 DOM 操作深度缠绕
6. **难度计算不一致**: renderer.js 五级(0.15/0.30/0.50/0.70)，EP 三级(0.3/0.7)

#### C. 可重用 UI 组件清单

| 组件 | 当前实例数 | 说明 |
|------|-----------|------|
| StemStructuredEditor | 3 | block 拖拽编辑器（段落/选项/填空/答题区/配图） |
| AnswerStructuredEditor | 4 | 答案 block 编辑器（段落/配图） |
| LatexPreview | 6+ | KaTeX 渲染面板 |
| TagCapsuleEditor | 4 | 副标签胶囊 + 输入框 |
| MainTagSelector | 4 | 主标签级联选择器 |
| DifficultySlider | 3 | 难度 P-value 滑块 |
| AiStatusPanel | 2 | AI 任务进度 + 应用推荐 |
| AnswerTabNav | 2 | 多答案标签页切换 |
| FileUploadArea | 1 | 拖拽上传区（EP） |
| BubbleNav | 1 | 气泡导航（EP） |

---

## 二、技术选型

### 2.1 框架选择：Vue 3 + Composition API

| 维度 | Vue 3 | React | 选择理由 |
|------|-------|-------|---------|
| 学习曲线 | 低 | 中 | 项目规模中等，Vue SFC 更直觉 |
| 模板可读性 | HTML-like 模板 | JSX | 与现有 HTML 结构更接近，迁移成本低 |
| 状态管理 | Pinia（官方） | Redux/Zustand | Pinia 更轻量 |
| 响应式 | 内置 `ref`/`reactive` | 需 useState/useReducer | Vue 的自动依赖追踪更适合替代当前手动 render |
| TypeScript | 一流支持 | 一流支持 | 平手 |
| Electron 生态 | electron-vite 成熟 | 同等 | 平手 |

### 2.2 技术栈

```
Vue 3.5+        — 核心框架 (Composition API + <script setup>)
TypeScript 5.x  — 类型安全
Pinia 2.x       — 状态管理
Vue Router 4.x  — 路由（Tab 视图切换）
Vite 6.x        — 构建工具
electron-vite    — Electron + Vite 集成
KaTeX            — 数学渲染
@vueuse/core     — 实用组合式函数（localStorage, debounce, etc.）
Tailwind CSS 4   — 原子化样式（可选，也可用 CSS Modules）
Vitest           — 单元测试
Playwright       — E2E 测试（可选）
```

---

## 三、目录结构设计

```
frontend-vue/
├── electron/
│   ├── main.ts                    # Electron 主进程（精简）
│   ├── preload.ts                 # contextBridge（保持不变）
│   └── screenshot/                # 截图窗口
│       ├── index.html
│       ├── screenshot.ts
│       └── screenshot.css
├── src/
│   ├── main.ts                    # Vue 入口
│   ├── App.vue                    # 根组件
│   ├── env.d.ts                   # 类型声明
│   │
│   ├── api/                       # === API 层 ===
│   │   ├── client.ts              # 统一 HTTP 客户端（封装 window.qforge.api）
│   │   ├── ws.ts                  # WebSocket 客户端（带自动重连）
│   │   ├── auth.ts                # 登录/登出/凭据
│   │   ├── question.ts            # 题目 CRUD API
│   │   ├── examParse.ts           # 试卷解析 API
│   │   ├── tag.ts                 # 标签 API
│   │   └── types.ts               # 所有 Request/Response 类型
│   │
│   ├── stores/                    # === Pinia Stores ===
│   │   ├── auth.ts                # 登录状态 + token
│   │   ├── question.ts            # 题目列表 + 选中项 + 阶段筛选
│   │   ├── examParse.ts           # 试卷解析任务 + 题目 + 焦点状态
│   │   ├── tag.ts                 # 标签目录缓存
│   │   └── notification.ts        # 全局消息/日志
│   │
│   ├── composables/               # === 组合式函数 ===
│   │   ├── useStemEditor.ts       # 结构化编辑器逻辑（block CRUD、拖拽）
│   │   ├── useAnswerEditor.ts     # 答案编辑器逻辑
│   │   ├── useLatexRender.ts      # KaTeX 渲染钩子
│   │   ├── useImageResolver.ts    # 图片引用解析
│   │   ├── useTagEditor.ts        # 标签编辑逻辑（主标签选择 + 副标签胶囊）
│   │   ├── useDifficulty.ts       # 难度 P-value 计算（统一五级）
│   │   ├── useWebSocket.ts        # WS 连接管理 + 事件分发
│   │   ├── useScreenshot.ts       # 截图工作流
│   │   └── useLocalStorage.ts     # 持久化辅助
│   │
│   ├── components/                # === 可复用组件 ===
│   │   ├── common/
│   │   │   ├── AppHeader.vue      # 顶部导航栏
│   │   │   ├── StatusBar.vue      # 底部状态栏
│   │   │   ├── ConfirmDialog.vue  # 通用确认弹窗
│   │   │   └── Toast.vue          # 消息提示
│   │   │
│   │   ├── editor/
│   │   │   ├── StemEditor.vue     # 结构化题干编辑器 ★核心复用
│   │   │   ├── AnswerEditor.vue   # 结构化答案编辑器 ★核心复用
│   │   │   ├── EditorBlock.vue    # 单个 block（段落/选项/填空/图片）
│   │   │   ├── ChoiceBlock.vue    # 选项 block
│   │   │   ├── BlankBlock.vue     # 填空 block
│   │   │   └── ImageBlock.vue     # 配图 block
│   │   │
│   │   ├── preview/
│   │   │   ├── LatexPreview.vue   # LaTeX 渲染预览面板 ★核心复用
│   │   │   ├── StemPreview.vue    # 题干预览（含图片解析）
│   │   │   └── AnswerPreview.vue  # 答案预览
│   │   │
│   │   ├── tag/
│   │   │   ├── MainTagSelector.vue    # 主标签选择器 ★核心复用
│   │   │   ├── TagCapsuleEditor.vue   # 副标签胶囊编辑 ★核心复用
│   │   │   └── TagSection.vue         # 标签区域组合
│   │   │
│   │   ├── difficulty/
│   │   │   └── DifficultySlider.vue   # 难度滑块 ★核心复用
│   │   │
│   │   ├── ai/
│   │   │   └── AiAnalysisPanel.vue    # AI 分析状态面板 ★核心复用
│   │   │
│   │   └── question/
│   │       ├── AnswerTabNav.vue       # 答案标签页切换
│   │       ├── QuestionCard.vue       # 题目列表卡片
│   │       └── QuestionDetail.vue     # 题目详情头部
│   │
│   ├── views/                     # === 页面视图 ===
│   │   ├── LoginView.vue          # 登录页
│   │   ├── EntryView.vue          # 录题中心
│   │   │   ├── EntryList.vue      # 左侧任务列表
│   │   │   ├── StemWorkCard.vue   # 题干确认区
│   │   │   ├── AnswerWorkCard.vue # 答案录入区
│   │   │   └── CompletedCard.vue  # 完成预览区
│   │   ├── BankView.vue           # 题库
│   │   │   ├── BankFilter.vue     # 左侧筛选
│   │   │   ├── BankList.vue       # 题目列表
│   │   │   └── BankDetail.vue     # 题目详情+编辑
│   │   └── ExamParseView.vue      # 试卷解析
│   │       ├── UploadPanel.vue    # 上传面板
│   │       ├── TaskList.vue       # 任务列表
│   │       ├── BubbleNav.vue      # 气泡导航
│   │       ├── QuestionFocus.vue  # 焦点视图
│   │       └── ParseLog.vue       # 解析日志
│   │
│   ├── lib/                       # === 纯函数工具库 ===
│   │   ├── stemXml.ts             # XML 解析/序列化（参数化，消除 stem/answer 重复）
│   │   ├── imageRef.ts            # 图片引用工具（合并 image-runtime + answer-runtime）
│   │   ├── difficulty.ts          # 难度等级计算（统一五级标准）
│   │   ├── escapeHtml.ts          # HTML/XML 转义
│   │   └── questionType.ts        # 题型映射
│   │
│   └── styles/                    # === 样式 ===
│       ├── variables.css          # CSS 变量 / 设计 tokens
│       ├── base.css               # 重置 + 基础排版
│       └── components/            # 组件级样式（或用 <style scoped>）
│
├── test/
│   ├── unit/
│   │   ├── lib/                   # 纯函数测试
│   │   │   ├── stemXml.test.ts
│   │   │   ├── imageRef.test.ts
│   │   │   └── difficulty.test.ts
│   │   ├── stores/                # Store 测试
│   │   └── composables/           # 组合式函数测试
│   └── e2e/                       # E2E 测试（可选）
│
├── index.html                     # Vite HTML 入口
├── vite.config.ts
├── tsconfig.json
├── electron.vite.config.ts
└── package.json
```

---

## 四、模块拆分详解

### 4.1 API 层 (`src/api/`)

**设计原则**: 所有后端交互集中在 api 层，返回强类型数据，与 UI 完全解耦。

```typescript
// api/client.ts — 统一 HTTP 客户端
import type { ApiResponse } from './types'

export async function apiRequest<T>(
  path: string, 
  method: string = 'GET', 
  body?: unknown
): Promise<T> {
  const authStore = useAuthStore()
  const res = await window.qforge.api.request(path, method, authStore.token, body)
  if (res.status >= 400) throw new ApiError(res.status, res.body)
  return res.body as T
}

export async function apiUpload<T>(
  path: string,
  filePaths: string[],
  fields?: Record<string, string>
): Promise<T> {
  const authStore = useAuthStore()
  return window.qforge.api.uploadMultipart(path, authStore.token, filePaths, fields)
}
```

```typescript
// api/question.ts — 28 个 API 端点的封装
export const questionApi = {
  list: () => apiRequest<QuestionOverview[]>('/api/questions'),
  create: (body?: CreateQuestionReq) => apiRequest<QuestionStatus>('/api/questions', 'POST', body),
  delete: (uuid: string) => apiRequest<void>(`/api/questions/${uuid}`, 'DELETE'),
  updateStem: (uuid: string, body: UpdateStemReq) => apiRequest<QuestionStatus>(`/api/questions/${uuid}/stem`, 'PUT', body),
  addAnswer: (uuid: string, body: CreateAnswerReq) => apiRequest<AddAnswerRes>(`/api/questions/${uuid}/answers`, 'POST', body),
  updateAnswer: (uuid: string, ansUuid: string, body: UpdateAnswerReq) => apiRequest<QuestionStatus>(`/api/questions/${uuid}/answers/${ansUuid}`, 'PUT', body),
  deleteAnswer: (uuid: string, ansUuid: string) => apiRequest<void>(`/api/questions/${uuid}/answers/${ansUuid}`, 'DELETE'),
  complete: (uuid: string) => apiRequest<QuestionStatus>(`/api/questions/${uuid}/complete`, 'POST'),
  submitOcr: (uuid: string, body: OcrSubmitReq) => apiRequest<OcrAccepted>(`/api/questions/${uuid}/ocr-tasks`, 'POST', body),
  getAssets: (uuid: string) => apiRequest<QuestionAsset[]>(`/api/questions/${uuid}/assets`),
  updateTags: (uuid: string, body: UpdateTagsReq) => apiRequest<QuestionStatus>(`/api/questions/${uuid}/tags`, 'PUT', body),
  updateDifficulty: (uuid: string, body: UpdateDifficultyReq) => apiRequest<QuestionStatus>(`/api/questions/${uuid}/difficulty`, 'PUT', body),
  aiAnalysis: (uuid: string) => apiRequest<AiAccepted>(`/api/questions/${uuid}/ai-analysis`, 'POST'),
  applyAi: (uuid: string, taskUuid: string, body: ApplyAiReq) => apiRequest<QuestionStatus>(`/api/questions/${uuid}/ai-tasks/${taskUuid}/apply`, 'PUT', body),
}
```

### 4.2 Store 层 (`src/stores/`)

**设计原则**: 按业务域拆分 Store，每个 Store 只管一个领域。

```typescript
// stores/question.ts
export const useQuestionStore = defineStore('question', () => {
  const questions = ref<Map<string, QuestionEntry>>(new Map())
  const selectedUuid = ref<string | null>(null)
  const stageFilter = ref<'PENDING_STEM' | 'PENDING_ANSWER'>('PENDING_STEM')
  
  const selected = computed(() => selectedUuid.value ? questions.value.get(selectedUuid.value) : null)
  const filteredList = computed(() => /* filter by stageFilter */)

  async function syncAll() { /* fetch from API + merge */ }
  async function createQuestion() { /* ... */ }
  async function confirmStem(uuid: string, xml: string, tags: Tags) { /* ... */ }
  // ...
  
  return { questions, selectedUuid, selected, filteredList, syncAll, createQuestion, confirmStem }
})
```

```typescript
// stores/examParse.ts  
export const useExamParseStore = defineStore('examParse', () => {
  const tasks = ref<Map<string, ExamParseTask>>(new Map())
  const activeTaskUuid = ref<string | null>(null)
  const questions = ref<Map<number, ExamParseQuestion>>(new Map())
  const activeSeqNo = ref<number>(1)
  const focusStage = ref<FocusStage>('PREVIEW')
  const logs = ref<LogEntry[]>([])

  const activeTask = computed(() => /* ... */)
  const currentQuestion = computed(() => /* ... */)
  const nextPendingSeqNo = computed(() => /* 自动跳转下一道 */)

  async function createTask(files: File[], hasAnswer: boolean) { /* ... */ }
  async function confirmSingle(seqNo: number) { /* ... */ }
  async function skipQuestion(seqNo: number) { /* ... */ }
  // ...
})
```

### 4.3 组合式函数 (`src/composables/`)

**核心复用逻辑通过 composable 提取，取代原代码中的 3-4 次复制。**

```typescript
// composables/useStemEditor.ts
// 消除 renderer.js collectBlocksFromEditor ×2 + EP collectBlocksFromEpEditor ×1 的重复
export function useStemEditor(options: {
  initialXml?: string
  imageResolver?: (ref: string) => string
  onChange?: (xml: string) => void
}) {
  const blocks = ref<Block[]>([])
  
  function initFromXml(xml: string) { blocks.value = xmlToBlocks(xml) }
  function addBlock(type: BlockType, afterIndex?: number) { /* ... */ }
  function removeBlock(index: number) { /* ... */ }
  function moveBlock(from: number, to: number) { /* ... */ }
  function toXml(): string { return blocksToXml(blocks.value) }
  
  watch(blocks, () => options.onChange?.(toXml()), { deep: true })
  
  return { blocks, initFromXml, addBlock, removeBlock, moveBlock, toXml }
}
```

```typescript
// composables/useTagEditor.ts
// 消除 4 处标签编辑逻辑重复
export function useTagEditor(options?: { readonly?: boolean }) {
  const catalog = computed(() => useTagStore().catalog)
  const mainTags = ref<{ category: string; value: string }[]>([])
  const secondaryTags = ref<string[]>([])
  
  function addSecondaryTag(tag: string) { /* ... */ }
  function removeSecondaryTag(index: number) { /* ... */ }
  function setMainTag(category: string, value: string) { /* ... */ }
  function toPayload(): TagPayload { /* ... */ }
  
  return { catalog, mainTags, secondaryTags, addSecondaryTag, removeSecondaryTag, setMainTag, toPayload }
}
```

```typescript
// composables/useDifficulty.ts
// 统一难度计算 — 修复 EP 与主界面不一致的 Bug
import { difficultyLevel, type DifficultyLevel } from '@/lib/difficulty'

export function useDifficulty(initialValue?: number) {
  const pValue = ref(initialValue ?? 50)
  const level = computed<DifficultyLevel>(() => difficultyLevel(pValue.value))
  const label = computed(() => level.value.label)
  const cssClass = computed(() => level.value.cssClass)
  
  return { pValue, level, label, cssClass }
}
```

### 4.4 纯函数库 (`src/lib/`)

```typescript
// lib/stemXml.ts — 参数化消除 stem/answer 4 对重复函数
export function parseXmlDocument(input: string, rootTag: 'stem' | 'answer'): Document | null { /* ... */ }
export function toXmlPayload(input: string, rootTag: 'stem' | 'answer'): string { /* ... */ }
export function toEditorText(input: string, rootTag: 'stem' | 'answer'): string { /* ... */ }
export function isValidXml(xml: string, rootTag: 'stem' | 'answer'): boolean { /* ... */ }

// 向后兼容别名
export const parseStemXml = (input: string) => parseXmlDocument(input, 'stem')
export const parseAnswerXml = (input: string) => parseXmlDocument(input, 'answer')
```

```typescript
// lib/imageRef.ts — 合并 image-runtime + answer-runtime 的重复函数
export function refSeed(source: string): string { /* 8字符种子 */ }
export function buildScopedRef(source: string, index: number): string { /* ... */ }
export function parseRefIndex(ref: string): number { /* ... */ }
export function nextFigureRef(existingRefs: string[]): string { /* ... */ }
```

```typescript
// lib/difficulty.ts — 统一五级标准
export type DifficultyLevel = { key: string; label: string; cssClass: string }

export function difficultyLevel(pValue: number): DifficultyLevel {
  const p = pValue / 100
  if (p >= 0.70) return { key: 'easy', label: '简单', cssClass: 'd-easy' }
  if (p >= 0.50) return { key: 'medium-easy', label: '中等偏易', cssClass: 'd-medium-easy' }
  if (p >= 0.30) return { key: 'medium', label: '中等', cssClass: 'd-medium' }
  if (p >= 0.15) return { key: 'hard', label: '较难', cssClass: 'd-hard' }
  return { key: 'very-hard', label: '极难', cssClass: 'd-very-hard' }
}
```

### 4.5 核心组件设计

#### StemEditor.vue — 结构化题干编辑器（消除 ×3 重复）

```vue
<template>
  <div class="stem-editor">
    <!-- 工具栏 -->
    <div class="editor-toolbar">
      <button @click="addBlock('p')">+ 段落</button>
      <button @click="addBlock('choices')">+ 选项</button>
      <button @click="addBlock('blanks')">+ 填空</button>
      <button @click="addBlock('answer-area')">+ 答题区</button>
      <button @click="addBlock('image')">+ 配图</button>
      <button v-if="screenshotEnabled" @click="insertScreenshot">截图插图</button>
    </div>
    
    <!-- Block 列表（可拖拽） -->
    <TransitionGroup tag="div" name="block-list" class="block-list">
      <EditorBlock 
        v-for="(block, idx) in editor.blocks.value" 
        :key="block.id"
        :block="block"
        :index="idx"
        :image-resolver="imageResolver"
        :readonly="readonly"
        @update="editor.updateBlock(idx, $event)"
        @remove="editor.removeBlock(idx)"
        @move-up="editor.moveBlock(idx, idx - 1)"
        @move-down="editor.moveBlock(idx, idx + 1)"
      />
    </TransitionGroup>
  </div>
</template>

<script setup lang="ts">
import { useStemEditor } from '@/composables/useStemEditor'
import EditorBlock from './EditorBlock.vue'

const props = defineProps<{
  modelValue: string    // XML 字符串
  imageResolver?: (ref: string) => string
  readonly?: boolean
  screenshotEnabled?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [xml: string] }>()

const editor = useStemEditor({
  initialXml: props.modelValue,
  onChange: (xml) => emit('update:modelValue', xml)
})

watch(() => props.modelValue, (xml) => editor.initFromXml(xml))
</script>
```

#### LatexPreview.vue — LaTeX 渲染面板（消除 ×6 重复）

```vue
<template>
  <div ref="containerRef" class="latex-preview" :class="{ compact }">
    <span v-if="!xml" class="empty-note">{{ placeholder }}</span>
  </div>
</template>

<script setup lang="ts">
import { useLatexRender } from '@/composables/useLatexRender'

const props = defineProps<{
  xml: string
  placeholder?: string
  compact?: boolean
  imageResolver?: (ref: string) => string
  mode?: 'stem' | 'answer'
}>()

const containerRef = ref<HTMLElement>()
const { render } = useLatexRender()

watchEffect(() => {
  if (containerRef.value && props.xml) {
    render(containerRef.value, props.xml, {
      imageResolver: props.imageResolver,
      mode: props.mode
    })
  }
})
</script>
```

#### TagSection.vue — 标签编辑区（消除 ×4 重复）

```vue
<template>
  <div class="tag-section">
    <MainTagSelector 
      v-model="tags.mainTags.value" 
      :catalog="tags.catalog.value"
      :disabled="readonly"
    />
    <TagCapsuleEditor 
      v-model="tags.secondaryTags.value"
      :disabled="readonly"
      placeholder="输入标签，按空格添加"
    />
    <slot name="actions" />
  </div>
</template>

<script setup lang="ts">
import { useTagEditor } from '@/composables/useTagEditor'

const props = defineProps<{ readonly?: boolean }>()
const tags = useTagEditor({ readonly: props.readonly })

defineExpose({ toPayload: tags.toPayload })
</script>
```

---

## 五、视图映射

### 5.1 路由设计

```typescript
// router/index.ts
const routes = [
  { path: '/login', component: LoginView },
  { 
    path: '/', 
    component: AppShell,
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/entry' },
      { path: 'entry', component: EntryView },
      { path: 'bank', component: BankView },
      { path: 'exam-parse', component: ExamParseView },
    ]
  }
]
```

### 5.2 视图 → 组件映射

```
现有 index.html                    →  Vue 组件

#login-screen                      →  LoginView.vue
#app-shell > header                →  AppHeader.vue
#app-shell > footer                →  StatusBar.vue

--- Tab: 录题中心 ---
#entry-view > aside                →  EntryView.vue > EntryList.vue
  创建任务区                        →  CreateTaskPanel.vue (小组件)
  任务筛选+列表                     →  EntryList.vue + QuestionCard.vue
  系统消息                          →  EventLog.vue
#entry-view > main
  任务详情头                        →  QuestionDetail.vue
  题干确认区                        →  StemWorkCard.vue
    = StemEditor + LatexPreview + TagSection + MainTagSelector
  答案录入区                        →  AnswerWorkCard.vue
    = AnswerEditor + LatexPreview + TagSection + DifficultySlider + AiAnalysisPanel
  完成预览                          →  CompletedCard.vue
    = LatexPreview(stem) + AnswerTabNav + LatexPreview(answer)

--- Tab: 题库 ---
#bank-view > aside                 →  BankView.vue > BankFilter.vue + BankList.vue
#bank-view > main                  →  BankDetail.vue
    = LatexPreview(stem) + StemEditor(编辑模式)
    + AnswerTabNav + LatexPreview(answer) + AnswerEditor(编辑模式)
    + TagSection + DifficultySlider + AiAnalysisPanel

--- Tab: 试卷解析 ---
#exam-parse-view > aside           →  ExamParseView.vue
  上传面板                          →  UploadPanel.vue + FileUploadArea.vue
  任务列表                          →  TaskList.vue
  解析日志                          →  ParseLog.vue
#exam-parse-view > main
  空状态 / 气泡导航 + 焦点视图     →  BubbleNav.vue + QuestionFocus.vue
    焦点状态栏                      →  FocusStatusBar.vue
    预览模式                        →  LatexPreview(stem) + LatexPreview(answer)
    编辑模式                        →  StemEditor / AnswerEditor
    标签难度                        →  TagSection + DifficultySlider
```

---

## 六、迁移策略

### 6.1 分阶段迁移（推荐）

整体迁移不是一次性推翻重来，而是**由内向外、先工具后视图**。

#### Phase 0: 项目脚手架（0.5 天）

- [ ] 初始化 `frontend-vue/` 项目（electron-vite + Vue 3 + TypeScript + Pinia）
- [ ] 配置 Vite、ESLint、Prettier、Vitest
- [ ] 复制 Electron 主进程 + preload（仅做 TS 迁移）
- [ ] 验证 Electron 窗口可启动

#### Phase 1: 纯函数库 + 单元测试（1 天）

- [ ] 迁移 `stem-xml.js` → `lib/stemXml.ts`（参数化合并 stem/answer 对称函数）
- [ ] 迁移 `image-runtime.js` + `answer-runtime.js` → `lib/imageRef.ts`（合并重复）
- [ ] 新建 `lib/difficulty.ts`（统一五级标准）
- [ ] 新建 `lib/questionType.ts`（题型映射）
- [ ] 迁移现有 4 个测试文件 → Vitest
- [ ] 确保 100% 通过

#### Phase 2: API 层 + Store 层（1 天）

- [ ] 实现 `api/client.ts`（封装 window.qforge bridge）
- [ ] 实现 `api/auth.ts`, `api/question.ts`, `api/examParse.ts`, `api/tag.ts`
- [ ] 定义 `api/types.ts`（所有 Request/Response 类型）
- [ ] 实现 `stores/auth.ts`, `stores/question.ts`, `stores/examParse.ts`, `stores/tag.ts`, `stores/notification.ts`
- [ ] 从 renderer.js 提取 WebSocket 逻辑 → `composables/useWebSocket.ts`

#### Phase 3: 核心可复用组件（2 天）

- [ ] `StemEditor.vue` + `EditorBlock.vue` + sub-blocks
- [ ] `AnswerEditor.vue`
- [ ] `LatexPreview.vue`（含 KaTeX 集成）
- [ ] `MainTagSelector.vue`
- [ ] `TagCapsuleEditor.vue`
- [ ] `DifficultySlider.vue`
- [ ] `AiAnalysisPanel.vue`
- [ ] `AnswerTabNav.vue`
- [ ] 组件 Storybook/独立测试页验证

#### Phase 4: 视图组装 — 录题中心（1.5 天）

- [ ] `LoginView.vue`（简单表单）
- [ ] `AppHeader.vue` + `StatusBar.vue`
- [ ] `EntryView.vue` = `EntryList.vue` + `StemWorkCard.vue` + `AnswerWorkCard.vue` + `CompletedCard.vue`
- [ ] 接入 WebSocket + OCR 实时更新
- [ ] 截图工作流对接

#### Phase 5: 视图组装 — 题库（1 天）

- [ ] `BankView.vue` = `BankFilter.vue` + `BankList.vue` + `BankDetail.vue`
- [ ] 复用 Phase 3 所有组件（StemEditor, AnswerEditor, TagSection, DifficultySlider, AiAnalysisPanel）
- [ ] 与录题中心共享 questionStore

#### Phase 6: 视图组装 — 试卷解析（1.5 天）

- [ ] `ExamParseView.vue` = `UploadPanel.vue` + `TaskList.vue` + `BubbleNav.vue` + `QuestionFocus.vue`
- [ ] 复用 Phase 3 组件（StemEditor, AnswerEditor, TagSection, DifficultySlider）
- [ ] 接入 WebSocket 解析进度
- [ ] 单题确认/跳过/恢复流程

#### Phase 7: 集成测试 + 样式打磨（1 天）

- [ ] 全流程端到端测试（录题→入库→题库浏览→试卷解析）
- [ ] 样式迁移：CSS Variables + 组件 scoped styles
- [ ] 深色模式支持（可选）
- [ ] 响应式布局适配
- [ ] 性能优化（虚拟滚动、懒加载）

#### Phase 8: 切换 + 清理（0.5 天）

- [ ] `frontend-vue/` 替换 `frontend/`
- [ ] 更新 README + ARCHITECTURE.md
- [ ] 清理旧文件

**总计: ~10 天**

### 6.2 Electron 主进程处理

Electron 主进程 (`main.js`) 和 preload 保持基本不变，仅做以下调整：

1. **TypeScript 迁移**: `main.js` → `main.ts`，加入类型标注
2. **重复代码合并**: `requestBackend` + `requestBackendMultipart` 提取共享逻辑
3. **截图窗口**: 保持独立，不受 Vue 迁移影响
4. **加载入口**: `mainWindow.loadFile('index.html')` → `mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL)` (dev) 或 `loadFile('dist/index.html')` (prod)

---

## 七、代码消除预估

| 现有代码 | 行数 | 重构后 | 预计行数 | 净减少 |
|---------|------|--------|---------|-------|
| renderer.js | ~2000 (展开) | 拆分为 views/ + components/ | ~1200 | -40% |
| exam-parse-runtime.js | 1348 | ExamParseView + composables | ~800 | -40% |
| stem-xml.js | 357 | lib/stemXml.ts (参数化) | ~200 | -44% |
| image-runtime + answer-runtime | 242 | lib/imageRef.ts (合并) | ~80 | -67% |
| styles.css | 2513 | scoped styles + variables | ~1800 | -28% |
| **总计** | **~6400** | | **~4080** | **-36%** |

**但更重要的指标是**:
- **重复代码消除**: ~1500 行重复 → 0（通过组件化和 composable）
- **类型安全**: 0% → 100%（TypeScript 覆盖）
- **可测试覆盖**: ~4 测试文件 → 每个 lib/composable/store 均可测试

---

## 八、风险与注意事项

### 8.1 风险

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| KaTeX 在 Vue 中的 DOM 操作兼容性 | 中 | 用 `onMounted` + `nextTick` 封装，避免与 Vue 响应式冲突 |
| 拖拽排序在 Vue 中的实现 | 低 | 使用 `vue-draggable-plus` 或自定义 directive |
| Electron IPC 桥接不变 | 低 | preload.ts 保持一致 API 面 |
| 截图窗口独立于 Vue | 低 | 截图窗口是独立 HTML，不受影响 |
| 旧数据 localStorage 迁移 | 中 | Phase 7 写迁移脚本读取旧格式、写入新 Store |

### 8.2 不迁移的部分

- `electron/main.ts`: 仅 TS 化 + 小幅重构，不改架构
- `electron/screenshot.*`: 独立窗口，保持不变
- 后端 Java 代码: 完全不受影响

---

## 九、决策点

以下问题需要在开始前确认：

1. **样式方案**: Tailwind CSS vs CSS Modules vs scoped CSS？
   - 推荐: scoped `<style>` + CSS Variables（最接近现有风格，学习成本最低）
2. **是否引入 UI 组件库**: 如 Element Plus / Naive UI / Ant Design Vue？
   - 推荐: **不引入**，当前自定义组件已足够，引入库会增加包体积且风格不一致
3. **是否在 Phase 0 就创建独立仓库/目录**？
   - 推荐: 在 `frontend-vue/` 目录平行开发，完成后替换 `frontend/`
4. **打包工具**: electron-vite vs electron-forge + vite？
   - 推荐: `electron-vite`（集成度高、配置简单）
