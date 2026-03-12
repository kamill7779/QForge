/**
 * stores/question.ts — Question state management.
 *
 * Manages the question list, selection, CRUD operations, OCR tasks,
 * AI analysis, and local workspace persistence.
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { questionApi } from '@/api/question'
import { useAuthStore } from '@/stores/auth'
import type {
  QuestionOverviewResponse,
  QuestionMainTagResponse,
  AnswerOverviewResponse,
  UpdateStemRequest,
  CreateAnswerRequest,
  UpdateAnswerRequest,
  OcrBizType,
  OcrTaskStatus,
  AiTaskResponse,
  InlineImageEntry
} from '@/api/types'
import { useNotificationStore } from './notification'
import { useTagStore } from './tag'

// ──────────────────── Types ────────────────────

/** Computed display stage (derived from Entry fields). */
export type QuestionStage = 'PENDING_STEM' | 'PENDING_ANSWER' | 'COMPLETED'

/** Local OCR task tracking. */
export interface OcrTaskEntry {
  taskUuid: string
  bizType: OcrBizType
  questionUuid: string
  status: OcrTaskStatus
  recognizedText?: string
  errorMsg?: string
}

/**
 * Local Entry — merges backend data with local editing state.
 * This is the primary data structure for questions in the store.
 */
export interface QuestionEntry {
  questionUuid: string
  status: 'DRAFT' | 'READY'
  // stem
  stemText: string
  stemDraft: string
  stemConfirmed: boolean
  // tags
  mainTags: QuestionMainTagResponse[]
  secondaryTags: string[]
  secondaryTagsDraft: string
  // difficulty
  difficulty: number | null
  // source
  source: string
  // answers
  answerCount: number
  answerDraft: string
  answersLocal: string[]
  answersServerData: Array<{ answerUuid: string; latexText: string }>
  answerViewIndex: number
  // ocr
  lastOcrTaskUuid: string
  lastOcrStatus: string
  lastAnswerOcrTaskUuid: string
  lastAnswerOcrStatus: string
  // images
  stemImageBase64: string
  inlineImages: Record<string, string>
  answerImages: Record<string, string>
  assetsLoaded: boolean
  /** Monotonic counter — incremented every time image assets are loaded or changed.
   *  Used as LatexPreview renderKey for reliable re-rendering. */
  assetVersion: number
  // meta
  createdAt: number
  updatedAt: number
}

/** AI analysis state per view context. */
export interface AiState {
  pending: Set<string>
  pendingTimers: Map<string, ReturnType<typeof setTimeout>>
  lastResult: AiTaskResponse | null
  taskUuid: string | null
}

// ──────────────────── Helpers ────────────────────

/** Compute display stage from entry state. */
export function stageOf(entry: QuestionEntry): QuestionStage {
  if (entry.status === 'READY') return 'COMPLETED'
  if (!entry.stemConfirmed) return 'PENDING_STEM'
  return 'PENDING_ANSWER'
}

/** Normalize backend response into a local QuestionEntry. */
function normalizeEntry(q: QuestionOverviewResponse): QuestionEntry {
  return {
    questionUuid: q.questionUuid,
    status: q.status,
    stemText: q.stemText ?? '',
    stemDraft: q.stemText ?? '',
    stemConfirmed: !!q.stemText,
    mainTags: q.mainTags ?? [],
    secondaryTags: q.secondaryTags ?? [],
    secondaryTagsDraft: (q.secondaryTags ?? []).join(', '),
    difficulty: q.difficulty,
    source: q.source || '未分类',
    answerCount: q.answerCount ?? 0,
    answerDraft: '',
    answersLocal: (q.answers ?? []).map((a) => a.latexText),
    answersServerData: (q.answers ?? []).map((a) => ({
      answerUuid: a.answerUuid,
      latexText: a.latexText
    })),
    answerViewIndex: 0,
    lastOcrTaskUuid: '',
    lastOcrStatus: '',
    lastAnswerOcrTaskUuid: '',
    lastAnswerOcrStatus: '',
    stemImageBase64: '',
    inlineImages: {},
    answerImages: {},
    assetsLoaded: false,
    assetVersion: 0,
    createdAt: q.createdAt ? new Date(q.createdAt).getTime() : new Date(q.updatedAt).getTime(),
    updatedAt: new Date(q.updatedAt).getTime()
  }
}

/** Merge backend data into an existing local entry, preserving local drafts. */
function mergeEntry(
  existing: QuestionEntry,
  server: QuestionOverviewResponse
): QuestionEntry {
  return {
    ...existing,
    status: server.status,
    stemText: server.stemText ?? existing.stemText,
    mainTags: server.mainTags ?? existing.mainTags,
    secondaryTags: server.secondaryTags ?? existing.secondaryTags,
    difficulty: server.difficulty ?? existing.difficulty,
    source: server.source || existing.source || '未分类',
    answerCount: server.answerCount ?? existing.answerCount,
    answersServerData: (server.answers ?? []).map((a) => ({
      answerUuid: a.answerUuid,
      latexText: a.latexText
    })),
    answersLocal: (server.answers ?? []).map((a) => a.latexText),
    createdAt: server.createdAt ? new Date(server.createdAt).getTime() : existing.createdAt,
    updatedAt: new Date(server.updatedAt).getTime()
  }
}

// ──────────────────── Store ────────────────────

export const useQuestionStore = defineStore('question', () => {
  const notif = useNotificationStore()

  // ── State ──

  const entries = ref(new Map<string, QuestionEntry>())
  const ocrTasks = ref(new Map<string, OcrTaskEntry>())
  const selectedQuestionUuid = ref('')
  const stageFilter = ref<QuestionStage>('PENDING_STEM')
  const screenshotIntent = ref<'question-ocr' | 'answer-ocr' | 'insert-image' | 'answer-insert-image'>(
    'question-ocr'
  )

  /** Lightweight dirty counter — incremented on important mutations so the
   *  AppShell watcher can trigger saves without deep-watching the entire map. */
  const dirtyCounter = ref(0)
  function markDirty(): void { dirtyCounter.value++ }

  // Bank view
  const bankSelectedUuid = ref('')
  const bankAnswerIdx = ref(0)

  // AI analysis
  const entryAi = ref<AiState>({
    pending: new Set(),
    pendingTimers: new Map(),
    lastResult: null,
    taskUuid: null
  })
  const bankAi = ref<AiState>({
    pending: new Set(),
    pendingTimers: new Map(),
    lastResult: null,
    taskUuid: null
  })

  // ── Getters ──

  /** All entries as sorted array (newest first). */
  const sortedEntries = computed(() =>
    Array.from(entries.value.values()).sort(
      (a, b) => b.updatedAt - a.updatedAt
    )
  )

  /** Entries filtered by current stage filter. */
  const filteredEntries = computed(() =>
    sortedEntries.value.filter((e) => stageOf(e) === stageFilter.value)
  )

  /** Currently selected entry (entry view). */
  const selectedEntry = computed(() =>
    selectedQuestionUuid.value
      ? entries.value.get(selectedQuestionUuid.value) ?? null
      : null
  )

  /** Currently selected entry (bank view). */
  const bankSelectedEntry = computed(() =>
    bankSelectedUuid.value
      ? entries.value.get(bankSelectedUuid.value) ?? null
      : null
  )

  /** Stage summary counts. */
  const stageCounts = computed(() => {
    const counts = { PENDING_STEM: 0, PENDING_ANSWER: 0, COMPLETED: 0 }
    for (const e of entries.value.values()) {
      counts[stageOf(e)]++
    }
    return counts
  })

  // ── Actions — Sync ──

  /** Fetch all questions from backend and merge with local state. */
  async function syncQuestions(token: string): Promise<void> {
    const list = await questionApi.list(token)
    const serverUuids = new Set<string>()

    for (const q of list) {
      serverUuids.add(q.questionUuid)
      const existing = entries.value.get(q.questionUuid)
      if (existing) {
        entries.value.set(q.questionUuid, mergeEntry(existing, q))
      } else {
        entries.value.set(q.questionUuid, normalizeEntry(q))
      }
    }

    // Remove entries that no longer exist on server
    for (const uuid of entries.value.keys()) {
      if (!serverUuids.has(uuid)) {
        entries.value.delete(uuid)
      }
    }
  }

  // ── Actions — CRUD ──

  /** Create a new question. */
  async function createQuestion(
    token: string,
    stemText?: string
  ): Promise<string> {
    const res = await questionApi.create(token, stemText ? { stemText } : undefined)
    const uuid = res.questionUuid
    entries.value.set(uuid, normalizeEntry({
      questionUuid: uuid,
      status: res.status,
      stemText: stemText ?? null,
      mainTags: [],
      secondaryTags: [],
      difficulty: null,
      answerCount: 0,
      answers: [],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }))
    selectedQuestionUuid.value = uuid
    markDirty()
    notif.log(`新建题目 ${uuid.slice(0, 8)}`)
    return uuid
  }

  /** Delete a question. */
  async function deleteQuestion(token: string, uuid: string): Promise<void> {
    await questionApi.delete(token, uuid)
    entries.value.delete(uuid)
    if (selectedQuestionUuid.value === uuid) selectedQuestionUuid.value = ''
    if (bankSelectedUuid.value === uuid) bankSelectedUuid.value = ''
    markDirty()
    notif.log(`删除题目 ${uuid.slice(0, 8)}`)
  }

  /** Batch delete questions. */
  async function batchDeleteQuestions(
    token: string,
    uuids: string[]
  ): Promise<number> {
    const { deleted } = await questionApi.batchDelete(token, uuids)
    for (const uuid of uuids) {
      entries.value.delete(uuid)
      if (selectedQuestionUuid.value === uuid) selectedQuestionUuid.value = ''
      if (bankSelectedUuid.value === uuid) bankSelectedUuid.value = ''
    }
    markDirty()
    notif.log(`批量删除 ${deleted} 道题目`)
    return deleted
  }

  /** Confirm stem (save stem XML to backend). */
  async function confirmStem(
    token: string,
    uuid: string,
    body: UpdateStemRequest
  ): Promise<void> {
    const res = await questionApi.updateStem(token, uuid, body)
    const entry = entries.value.get(uuid)
    if (entry) {
      entry.stemText = body.stemXml
      entry.stemDraft = body.stemXml
      entry.stemConfirmed = true
      // Preserve READY status — don't downgrade to DRAFT after stem update
      if (res.status === 'READY' || entry.status === 'READY') {
        entry.status = 'READY'
      } else {
        entry.status = res.status
      }
    }
    markDirty()
    notif.log(`确认题干 ${uuid.slice(0, 8)}`)
  }

  /** Add an answer to a question. */
  async function addAnswer(
    token: string,
    uuid: string,
    body: CreateAnswerRequest
  ): Promise<string> {
    const res = await questionApi.addAnswer(token, uuid, body)
    const entry = entries.value.get(uuid)
    if (entry) {
      entry.answersLocal.push(body.latexText)
      entry.answersServerData.push({
        answerUuid: res.answerUuid,
        latexText: body.latexText
      })
      entry.answerCount++
      entry.status = res.status
    }
    markDirty()
    notif.log(`添加答案 ${uuid.slice(0, 8)}`)
    return res.answerUuid
  }

  /** Update an existing answer. */
  async function updateAnswer(
    token: string,
    uuid: string,
    answerUuid: string,
    body: UpdateAnswerRequest
  ): Promise<void> {
    await questionApi.updateAnswer(token, uuid, answerUuid, body)
    const entry = entries.value.get(uuid)
    if (entry) {
      const idx = entry.answersServerData.findIndex(
        (a) => a.answerUuid === answerUuid
      )
      if (idx >= 0) {
        entry.answersServerData[idx].latexText = body.latexText
        entry.answersLocal[idx] = body.latexText
      }
    }
    notif.log(`更新答案 ${uuid.slice(0, 8)}`)
  }

  /** Delete an answer. */
  async function deleteAnswer(
    token: string,
    uuid: string,
    answerUuid: string
  ): Promise<void> {
    await questionApi.deleteAnswer(token, uuid, answerUuid)
    const entry = entries.value.get(uuid)
    if (entry) {
      const idx = entry.answersServerData.findIndex(
        (a) => a.answerUuid === answerUuid
      )
      if (idx >= 0) {
        entry.answersServerData.splice(idx, 1)
        entry.answersLocal.splice(idx, 1)
      }
      entry.answerCount = Math.max(0, entry.answerCount - 1)
    }
    notif.log(`删除答案 ${uuid.slice(0, 8)}`)
  }

  /** Mark question as complete (DRAFT → READY). */
  async function completeQuestion(
    token: string,
    uuid: string
  ): Promise<void> {
    const res = await questionApi.complete(token, uuid)
    const entry = entries.value.get(uuid)
    if (entry) {
      entry.status = res.status
    }
    if (selectedQuestionUuid.value === uuid && entry && stageOf(entry) !== stageFilter.value) {
      const nextVisible = filteredEntries.value[0]
      selectedQuestionUuid.value = nextVisible?.questionUuid ?? ''
    }
    markDirty()
    notif.log(`完成录入 ${uuid.slice(0, 8)}`)
  }

  // ── Actions — OCR ──

  /** Submit an OCR task for a question. */
  async function submitOcr(
    token: string,
    uuid: string,
    bizType: OcrBizType,
    imageBase64: string
  ): Promise<string> {
    const res = await questionApi.submitOcr(token, uuid, {
      bizType,
      imageBase64
    })
    const task: OcrTaskEntry = {
      taskUuid: res.taskUuid,
      bizType,
      questionUuid: uuid,
      status: 'PENDING'
    }
    ocrTasks.value.set(res.taskUuid, task)

    const entry = entries.value.get(uuid)
    if (entry) {
      if (bizType === 'QUESTION_STEM') {
        entry.lastOcrTaskUuid = res.taskUuid
        entry.lastOcrStatus = 'PENDING'
      } else {
        entry.lastAnswerOcrTaskUuid = res.taskUuid
        entry.lastAnswerOcrStatus = 'PENDING'
      }
    }

    notif.log(`提交OCR: ${bizType} ${uuid.slice(0, 8)}`)
    return res.taskUuid
  }

  /** Update OCR task state (called from WebSocket handler). */
  function updateOcrTask(
    taskUuid: string,
    status: OcrTaskStatus,
    recognizedText?: string,
    errorMsg?: string
  ): void {
    const task = ocrTasks.value.get(taskUuid)
    if (!task) return
    task.status = status
    if (recognizedText !== undefined) task.recognizedText = recognizedText
    if (errorMsg !== undefined) task.errorMsg = errorMsg

    const entry = entries.value.get(task.questionUuid)
    if (entry) {
      let shouldLoadAssets = false
      if (task.bizType === 'QUESTION_STEM') {
        entry.lastOcrStatus = status
        // Auto-backfill stemDraft when OCR succeeds and draft is empty
        if (status === 'SUCCESS' && recognizedText && !entry.stemDraft) {
          entry.stemDraft = recognizedText
          shouldLoadAssets = true
          notif.log(`OCR识别完成，已自动填充题干`)
          markDirty()
        }
        if (status === 'FAILED') {
          notif.log(`题干OCR识别失败${errorMsg ? ': ' + errorMsg : ''}`)
        }
      } else {
        entry.lastAnswerOcrStatus = status
        // Auto-backfill answerDraft when OCR succeeds and draft is empty
        if (status === 'SUCCESS' && recognizedText && !entry.answerDraft) {
          entry.answerDraft = recognizedText
          shouldLoadAssets = true
          notif.log(`OCR识别完成，已自动填充答案`)
          markDirty()
        }
        if (status === 'FAILED') {
          notif.log(`答案OCR识别失败${errorMsg ? ': ' + errorMsg : ''}`)
        }
      }

      // Auto-load image assets after OCR success so inline images resolve
      if (shouldLoadAssets || (status === 'SUCCESS' && !entry.assetsLoaded)) {
        const authStore = useAuthStore()
        if (authStore.token) {
          fetchAssets(authStore.token, task.questionUuid).catch(() => {
            // Non-critical: assets can be loaded manually when user selects question
          })
        }
      }
    }
  }

  // ── Actions — Assets ──

  /** Load question assets (images). */
  async function fetchAssets(token: string, uuid: string): Promise<void> {
    const assets = await questionApi.getAssets(token, uuid)
    const entry = entries.value.get(uuid)
    if (!entry) return

    for (const a of assets) {
      // Determine if ref key belongs to stem or answer by convention
      entry.inlineImages[a.refKey] = a.imageData
    }
    entry.assetsLoaded = true
    entry.assetVersion = (entry.assetVersion ?? 0) + 1
  }

  // ── Actions — Tags & Difficulty ──

  /** Update tags for a question. */
  async function updateTags(
    token: string,
    uuid: string,
    tags: string[]
  ): Promise<void> {
    await questionApi.updateTags(token, uuid, { tags })
    // Local update: classify tags using catalog instead of full syncQuestions
    const entry = entries.value.get(uuid)
    if (entry) {
      const tagStore = useTagStore()
      const knownCodes = new Map<string, { categoryCode: string; categoryName: string; tagName: string }>()
      for (const cat of tagStore.mainCategories) {
        for (const opt of cat.options) {
          knownCodes.set(opt.tagCode, {
            categoryCode: cat.categoryCode,
            categoryName: cat.categoryName,
            tagName: opt.tagName
          })
        }
      }
      const mainTags: QuestionMainTagResponse[] = []
      const secondaryTags: string[] = []
      for (const t of tags) {
        const info = knownCodes.get(t)
        if (info) {
          mainTags.push({ categoryCode: info.categoryCode, categoryName: info.categoryName, tagCode: t, tagName: info.tagName })
        } else {
          secondaryTags.push(t)
        }
      }
      entry.mainTags = mainTags
      entry.secondaryTags = secondaryTags
      markDirty()
    }
    notif.log(`更新标签 ${uuid.slice(0, 8)}`)
  }

  /** Update difficulty for a question. */
  async function updateDifficulty(
    token: string,
    uuid: string,
    difficulty: number
  ): Promise<void> {
    await questionApi.updateDifficulty(token, uuid, { difficulty })
    const entry = entries.value.get(uuid)
    if (entry) entry.difficulty = difficulty
    notif.log(`更新难度 ${uuid.slice(0, 8)} → ${difficulty}`)
  }

  // ── Actions — AI ──

  /** AI pending timeout (30s). */
  const AI_PENDING_TIMEOUT = 180_000

  /** Request AI analysis for a question. */
  async function requestAiAnalysis(
    token: string,
    uuid: string,
    context: 'entry' | 'bank'
  ): Promise<void> {
    const res = await questionApi.aiAnalysis(token, uuid)
    const ai = context === 'entry' ? entryAi.value : bankAi.value
    ai.pending.add(uuid)
    ai.taskUuid = res.taskUuid
    notif.log(`AI分析请求 ${uuid.slice(0, 8)}`)

    // Auto-timeout: clear pending after 30s if WS result never arrives
    const timer = setTimeout(() => {
      if (ai.pending.has(uuid)) {
        ai.pending.delete(uuid)
        ai.pendingTimers.delete(uuid)
        ai.lastResult = {
          taskUuid: res.taskUuid,
          questionUuid: uuid,
          status: 'FAILED',
          suggestedTags: null,
          suggestedDifficulty: null,
          reasoning: null,
          errorMessage: 'AI分析超时（3分钟），请重试',
          appliedAt: null,
          createdAt: new Date().toISOString()
        }
        notif.log('AI分析超时，请重试')
      }
    }, AI_PENDING_TIMEOUT)
    ai.pendingTimers.set(uuid, timer)
  }

  /** Handle AI result from WebSocket. */
  function handleAiResult(result: AiTaskResponse, context: 'entry' | 'bank'): void {
    const ai = context === 'entry' ? entryAi.value : bankAi.value
    // Clear timeout timer
    const timer = ai.pendingTimers.get(result.questionUuid)
    if (timer) {
      clearTimeout(timer)
      ai.pendingTimers.delete(result.questionUuid)
    }
    ai.pending.delete(result.questionUuid)
    ai.lastResult = result
  }

  /** Cancel a pending AI analysis (user-initiated). */
  function cancelAiAnalysis(uuid: string, context: 'entry' | 'bank'): void {
    const ai = context === 'entry' ? entryAi.value : bankAi.value
    const timer = ai.pendingTimers.get(uuid)
    if (timer) {
      clearTimeout(timer)
      ai.pendingTimers.delete(uuid)
    }
    ai.pending.delete(uuid)
    ai.lastResult = null
    ai.taskUuid = null
    notif.log('已取消AI分析')
  }

  /** Apply AI recommendation. */
  async function applyAiRecommendation(
    token: string,
    uuid: string,
    taskUuid: string,
    tags?: string[],
    difficulty?: number
  ): Promise<void> {
    await questionApi.applyAi(token, uuid, taskUuid, { tags, difficulty })
    // Local update: difficulty can be set immediately.
    // Tags are complex (mainTags structure) — we defer to a light sync.
    const entry = entries.value.get(uuid)
    if (entry) {
      if (difficulty !== undefined) {
        entry.difficulty = difficulty
      }
      markDirty()
    }
    notif.log(`应用AI推荐 ${uuid.slice(0, 8)}`)
    // Clear the AI result panel so the button disappears and can't be re-applied
    if (entryAi.value.lastResult?.taskUuid === taskUuid) entryAi.value.lastResult = null
    if (bankAi.value.lastResult?.taskUuid === taskUuid) bankAi.value.lastResult = null
    // Tags were updated server-side; do a targeted local refresh
    if (tags && tags.length && entry) {
      const tagStore = useTagStore()
      const knownCodes = new Map<string, { categoryCode: string; categoryName: string; tagName: string }>()
      for (const cat of tagStore.mainCategories) {
        for (const opt of cat.options) {
          knownCodes.set(opt.tagCode, {
            categoryCode: cat.categoryCode,
            categoryName: cat.categoryName,
            tagName: opt.tagName
          })
        }
      }
      const mainTags: QuestionMainTagResponse[] = []
      const secondaryTags: string[] = []
      for (const t of tags) {
        const info = knownCodes.get(t)
        if (info) {
          mainTags.push({ categoryCode: info.categoryCode, categoryName: info.categoryName, tagCode: t, tagName: info.tagName })
        } else {
          secondaryTags.push(t)
        }
      }
      entry.mainTags = mainTags
      entry.secondaryTags = secondaryTags
    }
  }

  // ── Actions — Selection ──

  function selectQuestion(uuid: string): void {
    selectedQuestionUuid.value = uuid
  }

  function selectBankQuestion(uuid: string): void {
    bankSelectedUuid.value = uuid
    bankAnswerIdx.value = 0
  }

  function setStageFilter(stage: QuestionStage): void {
    stageFilter.value = stage
  }

  // ── Workspace Persistence ──

  /** Storage key scoped to user. */
  function storageKey(username: string): string {
    return `qforge.workspace.${username}`
  }

  /** Save workspace state to localStorage (excluding heavy image data). */
  function saveWorkspace(username: string): void {
    // Strip base64 image data to prevent main-thread blocking from large JSON.stringify
    const lightEntries = Array.from(entries.value.entries()).map(([k, v]) => [
      k,
      {
        ...v,
        stemImageBase64: '',
        inlineImages: {},
        answerImages: {},
        assetsLoaded: false,
        assetVersion: 0
      }
    ])
    const data = {
      entries: lightEntries,
      ocrTasks: Array.from(ocrTasks.value.entries()),
      selectedQuestionUuid: selectedQuestionUuid.value,
      bankSelectedUuid: bankSelectedUuid.value
    }
    try {
      localStorage.setItem(storageKey(username), JSON.stringify(data))
    } catch {
      // localStorage full — ignore
    }
  }

  /** Load workspace state from localStorage. */
  function loadWorkspace(username: string): void {
    const raw = localStorage.getItem(storageKey(username))
    if (!raw) return
    try {
      const data = JSON.parse(raw)
      if (data.entries) {
        // Defensive: ensure all entries have required fields
        const restored = new Map<string, QuestionEntry>(data.entries)
        for (const [key, entry] of restored) {
          if (!entry.stemText) entry.stemText = ''
          if (!entry.stemDraft) entry.stemDraft = ''
          if (!entry.inlineImages) entry.inlineImages = {}
          if (!entry.answerImages) entry.answerImages = {}
          if (!entry.mainTags) entry.mainTags = []
          if (!entry.secondaryTags) entry.secondaryTags = []
          if (!entry.answersLocal) entry.answersLocal = []
          if (!entry.answersServerData) entry.answersServerData = []
          if (!entry.createdAt) entry.createdAt = entry.updatedAt || 0
          if (entry.assetVersion == null) entry.assetVersion = 0
          if (!entry.source) entry.source = '未分类'
        }
        entries.value = restored
      }
      if (data.ocrTasks) {
        ocrTasks.value = new Map(data.ocrTasks)
      }
      if (data.selectedQuestionUuid) {
        selectedQuestionUuid.value = data.selectedQuestionUuid
      }
      if (data.bankSelectedUuid) {
        bankSelectedUuid.value = data.bankSelectedUuid
      }
    } catch {
      // Corrupted data — ignore
    }
  }

  // ── Reset ──

  function $reset(): void {
    entries.value = new Map()
    ocrTasks.value = new Map()
    selectedQuestionUuid.value = ''
    stageFilter.value = 'PENDING_STEM'
    screenshotIntent.value = 'question-ocr'
    bankSelectedUuid.value = ''
    bankAnswerIdx.value = 0
    entryAi.value = { pending: new Set(), pendingTimers: new Map(), lastResult: null, taskUuid: null }
    bankAi.value = { pending: new Set(), pendingTimers: new Map(), lastResult: null, taskUuid: null }
  }

  return {
    // state
    entries,
    ocrTasks,
    selectedQuestionUuid,
    stageFilter,
    screenshotIntent,
    bankSelectedUuid,
    bankAnswerIdx,
    entryAi,
    bankAi,
    dirtyCounter,
    // getters
    sortedEntries,
    filteredEntries,
    selectedEntry,
    bankSelectedEntry,
    stageCounts,
    // actions — sync
    syncQuestions,
    // actions — CRUD
    createQuestion,
    deleteQuestion,
    batchDeleteQuestions,
    confirmStem,
    addAnswer,
    updateAnswer,
    deleteAnswer,
    completeQuestion,
    // actions — OCR
    submitOcr,
    updateOcrTask,
    // actions — assets
    fetchAssets,
    // actions — tags / difficulty
    updateTags,
    updateDifficulty,
    // actions — AI
    requestAiAnalysis,
    handleAiResult,
    applyAiRecommendation,
    cancelAiAnalysis,
    // actions — selection
    selectQuestion,
    selectBankQuestion,
    setStageFilter,
    // persistence
    saveWorkspace,
    loadWorkspace,
    // helpers
    stageOf,
    $reset
  }
})

// Re-export stageOf for use outside the store
export { stageOf as computeStage }
