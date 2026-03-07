/**
 * stores/question.ts — Question state management.
 *
 * Manages the question list, selection, CRUD operations, OCR tasks,
 * AI analysis, and local workspace persistence.
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { questionApi } from '@/api/question'
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
  // meta
  updatedAt: number
}

/** AI analysis state per view context. */
export interface AiState {
  pending: Set<string>
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
    answerCount: server.answerCount ?? existing.answerCount,
    answersServerData: (server.answers ?? []).map((a) => ({
      answerUuid: a.answerUuid,
      latexText: a.latexText
    })),
    answersLocal: (server.answers ?? []).map((a) => a.latexText),
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
  const screenshotIntent = ref<'question-ocr' | 'answer-ocr' | 'insert-image'>(
    'question-ocr'
  )

  // Bank view
  const bankSelectedUuid = ref('')
  const bankAnswerIdx = ref(0)

  // AI analysis
  const entryAi = ref<AiState>({
    pending: new Set(),
    lastResult: null,
    taskUuid: null
  })
  const bankAi = ref<AiState>({
    pending: new Set(),
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
      updatedAt: new Date().toISOString()
    }))
    selectedQuestionUuid.value = uuid
    notif.log(`新建题目 ${uuid.slice(0, 8)}`)
    return uuid
  }

  /** Delete a question. */
  async function deleteQuestion(token: string, uuid: string): Promise<void> {
    await questionApi.delete(token, uuid)
    entries.value.delete(uuid)
    if (selectedQuestionUuid.value === uuid) selectedQuestionUuid.value = ''
    if (bankSelectedUuid.value === uuid) bankSelectedUuid.value = ''
    notif.log(`删除题目 ${uuid.slice(0, 8)}`)
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
      entry.status = res.status
    }
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
    if (entry) entry.status = res.status
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
      if (task.bizType === 'QUESTION_STEM') {
        entry.lastOcrStatus = status
      } else {
        entry.lastAnswerOcrStatus = status
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
  }

  // ── Actions — Tags & Difficulty ──

  /** Update tags for a question. */
  async function updateTags(
    token: string,
    uuid: string,
    tags: string[]
  ): Promise<void> {
    await questionApi.updateTags(token, uuid, { tags })
    notif.log(`更新标签 ${uuid.slice(0, 8)}`)
    // Re-sync to get updated tag data
    await syncQuestions(token)
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
  }

  /** Handle AI result from WebSocket. */
  function handleAiResult(result: AiTaskResponse, context: 'entry' | 'bank'): void {
    const ai = context === 'entry' ? entryAi.value : bankAi.value
    ai.pending.delete(result.questionUuid)
    ai.lastResult = result
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
    notif.log(`应用AI推荐 ${uuid.slice(0, 8)}`)
    await syncQuestions(token)
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

  /** Save workspace state to localStorage. */
  function saveWorkspace(username: string): void {
    const data = {
      entries: Array.from(entries.value.entries()),
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
        entries.value = new Map(data.entries)
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
    entryAi.value = { pending: new Set(), lastResult: null, taskUuid: null }
    bankAi.value = { pending: new Set(), lastResult: null, taskUuid: null }
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
