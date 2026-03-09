/**
 * stores/examParse.ts — Exam Parse state management.
 *
 * Manages exam parse tasks, parsed questions, focus/editing state,
 * session logs, and local persistence.
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { examParseApi } from '@/api/examParse'
import type {
  ExamParseTask,
  ExamParseQuestion,
  ExamParseConfirmStatus
} from '@/api/types'
import { useNotificationStore } from './notification'
import { useAuthStore } from './auth'

// ──────────────────── Types ────────────────────

export type FocusStage =
  | 'PREVIEW'
  | 'EDITING_STEM'
  | 'EDITING_ANSWER'
  | 'READY'
  | 'CONFIRMED'
  | 'SKIPPED'
  | 'ERROR'

export interface FocusData {
  focusStage: FocusStage
  mainTags?: Array<{
    categoryCode: string
    categoryName: string
    tagCode: string
    tagName: string
  }>
  secondaryTags?: string[]
  difficulty?: number | null
}

export interface EpLogEntry {
  ts: string
  msg: string
}

// ──────────────────── Helpers ────────────────────

function timeStr(): string {
  const d = new Date()
  return [d.getHours(), d.getMinutes(), d.getSeconds()]
    .map((n) => String(n).padStart(2, '0'))
    .join(':')
}

/** Derive initial focus stage from a question's confirm status. */
function initialFocusStage(q: ExamParseQuestion): FocusStage {
  if (q.confirmStatus === 'CONFIRMED') return 'CONFIRMED'
  if (q.confirmStatus === 'SKIPPED') return 'SKIPPED'
  if (q.parseError) return 'ERROR'
  return 'PREVIEW'
}

function focusKey(taskUuid: string, seqNo: number): string {
  return `${taskUuid}:${seqNo}`
}

// ──────────────────── Store ────────────────────

export const useExamParseStore = defineStore('examParse', () => {
  const notif = useNotificationStore()

  // ── State ──

  const tasks = ref(new Map<string, ExamParseTask>())
  const questions = ref(new Map<string, ExamParseQuestion[]>())
  const questionFocus = ref<Record<string, FocusData>>({})
  const activeTaskUuid = ref('')
  const activeSeqNo = ref(0)
  const logs = ref<EpLogEntry[]>([])
  const maxLogs = 200

  // ── Getters ──

  /** All tasks sorted by creation time (newest first). */
  const sortedTasks = computed(() =>
    Array.from(tasks.value.values()).sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    )
  )

  /** Active task object. */
  const activeTask = computed(() =>
    activeTaskUuid.value ? tasks.value.get(activeTaskUuid.value) ?? null : null
  )

  /** Questions for the active task. */
  const activeQuestions = computed(() =>
    activeTaskUuid.value
      ? questions.value.get(activeTaskUuid.value) ?? []
      : []
  )

  /** Currently focused question. */
  const activeQuestion = computed(() =>
    activeQuestions.value.find((q) => q.seqNo === activeSeqNo.value) ?? null
  )

  /** Focus data for the active question. */
  const activeFocus = computed((): FocusData | null => {
    if (!activeTaskUuid.value || !activeSeqNo.value) return null
    return questionFocus.value[focusKey(activeTaskUuid.value, activeSeqNo.value)] ?? null
  })

  /** Recent logs (max 50 for display). */
  const recentLogs = computed(() => logs.value.slice(0, 50))

  // ── Internal logging ──

  function epLog(msg: string): void {
    logs.value.unshift({ ts: timeStr(), msg })
    if (logs.value.length > maxLogs) logs.value.length = maxLogs
    notif.log(`[试卷解析] ${msg}`)
  }

  // ── Actions — Task CRUD ──

  /** Create a new exam parse task via file upload. */
  async function createTask(
    token: string,
    filePaths: string[],
    hasAnswerHint: boolean
  ): Promise<string> {
    const res = await examParseApi.createTask(token, filePaths, hasAnswerHint)
    const task: ExamParseTask = {
      id: null,
      taskUuid: res.taskUuid,
      ownerUser: '',
      status: res.status,
      progress: 0,
      fileCount: res.fileCount,
      totalPages: null,
      questionCount: null,
      hasAnswerHint,
      errorMsg: null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }
    tasks.value.set(res.taskUuid, task)
    activeTaskUuid.value = res.taskUuid
    activeSeqNo.value = 0
    epLog(`创建解析任务 ${res.taskUuid.slice(0, 8)}，文件数: ${res.fileCount}`)
    return res.taskUuid
  }

  /** Refresh all tasks from backend. */
  async function refreshTaskList(token: string): Promise<void> {
    const list = await examParseApi.listTasks(token)
    tasks.value.clear()
    for (const t of list) {
      tasks.value.set(t.taskUuid, t)
    }
  }

  /** Load questions for a task from backend (with retry). */
  async function refreshQuestions(
    token: string,
    taskUuid: string,
    retries = 3,
    delayMs = 800
  ): Promise<void> {
    let lastErr: unknown
    for (let attempt = 0; attempt <= retries; attempt++) {
      try {
        const detail = await examParseApi.getTask(token, taskUuid)
        // Update task
        tasks.value.set(taskUuid, detail.task)
        // Update questions
        questions.value.set(taskUuid, detail.questions)
        // Initialize focus data for new questions
        for (const q of detail.questions) {
          const key = focusKey(taskUuid, q.seqNo)
          if (!questionFocus.value[key]) {
            questionFocus.value[key] = { focusStage: initialFocusStage(q) }
          }
        }
        return // success
      } catch (err) {
        lastErr = err
        if (attempt < retries) {
          await new Promise((r) => setTimeout(r, delayMs))
        }
      }
    }
    throw lastErr
  }

  /** Delete a task. */
  async function deleteTask(
    token: string,
    taskUuid: string
  ): Promise<void> {
    await examParseApi.deleteTask(token, taskUuid)
    tasks.value.delete(taskUuid)
    questions.value.delete(taskUuid)
    // Clean up focus data for this task
    for (const key of Object.keys(questionFocus.value)) {
      if (key.startsWith(taskUuid + ':')) {
        delete questionFocus.value[key]
      }
    }
    if (activeTaskUuid.value === taskUuid) {
      activeTaskUuid.value = ''
      activeSeqNo.value = 0
    }
    epLog(`删除任务 ${taskUuid.slice(0, 8)}`)
  }

  // ── Actions — Question operations ──

  /** Update a single parsed question's fields (stem/answer XML, type, etc). */
  async function updateQuestion(
    token: string,
    taskUuid: string,
    seqNo: number,
    fields: Record<string, string>
  ): Promise<void> {
    const updated = await examParseApi.updateQuestion(token, taskUuid, seqNo, fields)
    // Replace in local list
    const list = questions.value.get(taskUuid)
    if (list) {
      const idx = list.findIndex((q) => q.seqNo === seqNo)
      if (idx >= 0) list[idx] = updated
    }
    epLog(`更新第${seqNo}题`)
  }

  /** Confirm all pending questions in a task. */
  async function confirmAll(
    token: string,
    taskUuid: string
  ): Promise<number> {
    const res = await examParseApi.confirmAll(token, taskUuid)
    // Refresh to get updated confirm statuses
    await refreshQuestions(token, taskUuid)
    // Update all focus data to CONFIRMED
    const list = questions.value.get(taskUuid) ?? []
    for (const q of list) {
      if (q.confirmStatus === 'CONFIRMED') {
        questionFocus.value[focusKey(taskUuid, q.seqNo)] = {
          ...questionFocus.value[focusKey(taskUuid, q.seqNo)],
          focusStage: 'CONFIRMED'
        }
      }
    }
    epLog(`全部确认 ${res.confirmedCount} 题`)
    return res.confirmedCount
  }

  /** Confirm a single question. */
  async function confirmSingle(
    token: string,
    taskUuid: string,
    seqNo: number
  ): Promise<void> {
    await examParseApi.confirmQuestion(token, taskUuid, seqNo)
    const key = focusKey(taskUuid, seqNo)
    questionFocus.value[key] = {
      ...questionFocus.value[key],
      focusStage: 'CONFIRMED'
    }
    // Also update the question object's confirm status
    const list = questions.value.get(taskUuid)
    if (list) {
      const q = list.find((q) => q.seqNo === seqNo)
      if (q) q.confirmStatus = 'CONFIRMED'
    }
    // Auto-advance to next unconfirmed question
    advanceToNextPending(taskUuid, seqNo)
    epLog(`确认第${seqNo}题`)
  }

  /** Skip a question. */
  async function skipQuestion(
    token: string,
    taskUuid: string,
    seqNo: number
  ): Promise<void> {
    await examParseApi.skipQuestion(token, taskUuid, seqNo)
    const key = focusKey(taskUuid, seqNo)
    questionFocus.value[key] = {
      ...questionFocus.value[key],
      focusStage: 'SKIPPED'
    }
    const list = questions.value.get(taskUuid)
    if (list) {
      const q = list.find((q) => q.seqNo === seqNo)
      if (q) q.confirmStatus = 'SKIPPED'
    }
    epLog(`跳过第${seqNo}题`)
  }

  /** Un-skip a question. */
  async function unskipQuestion(
    token: string,
    taskUuid: string,
    seqNo: number
  ): Promise<void> {
    await examParseApi.unskipQuestion(token, taskUuid, seqNo)
    const key = focusKey(taskUuid, seqNo)
    questionFocus.value[key] = {
      ...questionFocus.value[key],
      focusStage: 'PREVIEW'
    }
    const list = questions.value.get(taskUuid)
    if (list) {
      const q = list.find((q) => q.seqNo === seqNo)
      if (q) q.confirmStatus = 'PENDING'
    }
    epLog(`恢复第${seqNo}题`)
  }

  // ── Actions — Focus & Navigation ──

  /** Select a task. */
  function selectTask(taskUuid: string): void {
    activeTaskUuid.value = taskUuid
    activeSeqNo.value = 0
  }

  /** Select a question within the active task. */
  function selectQuestion(seqNo: number): void {
    activeSeqNo.value = seqNo
  }

  /** Set focus stage for a question. */
  function setFocusStage(
    taskUuid: string,
    seqNo: number,
    stage: FocusStage
  ): void {
    const key = focusKey(taskUuid, seqNo)
    if (!questionFocus.value[key]) {
      questionFocus.value[key] = { focusStage: stage }
    } else {
      questionFocus.value[key].focusStage = stage
    }
  }

  /** Get focus data for a specific question. */
  function getFocus(taskUuid: string, seqNo: number): FocusData | null {
    return questionFocus.value[focusKey(taskUuid, seqNo)] ?? null
  }

  /** Advance to next pending question after confirm. */
  function advanceToNextPending(taskUuid: string, currentSeqNo: number): void {
    const list = questions.value.get(taskUuid) ?? []
    const next = list.find(
      (q) => q.seqNo > currentSeqNo && q.confirmStatus === 'PENDING'
    )
    if (next) {
      activeSeqNo.value = next.seqNo
    }
  }

  /** Navigate to previous question. */
  function prevQuestion(): void {
    const list = activeQuestions.value
    const idx = list.findIndex((q) => q.seqNo === activeSeqNo.value)
    if (idx > 0) activeSeqNo.value = list[idx - 1].seqNo
  }

  /** Navigate to next question. */
  function nextQuestion(): void {
    const list = activeQuestions.value
    const idx = list.findIndex((q) => q.seqNo === activeSeqNo.value)
    if (idx >= 0 && idx < list.length - 1) activeSeqNo.value = list[idx + 1].seqNo
  }

  // ── Actions — WebSocket events ──

  /** Handle a WS question result event (a new question parsed). */
  function handleWsQuestionResult(
    taskUuid: string,
    question: ExamParseQuestion
  ): void {
    const list = questions.value.get(taskUuid) ?? []
    const existingIdx = list.findIndex((q) => q.seqNo === question.seqNo)
    if (existingIdx >= 0) {
      list[existingIdx] = question
    } else {
      list.push(question)
      list.sort((a, b) => a.seqNo - b.seqNo)
    }
    questions.value.set(taskUuid, list)

    // Initialize focus data
    const key = focusKey(taskUuid, question.seqNo)
    if (!questionFocus.value[key]) {
      questionFocus.value[key] = { focusStage: initialFocusStage(question) }
    }

    // Update task question count
    const task = tasks.value.get(taskUuid)
    if (task) {
      task.questionCount = list.length
    }

    // Auto-select first question for the active task
    if (activeTaskUuid.value === taskUuid && activeSeqNo.value === 0) {
      activeSeqNo.value = question.seqNo
    }

    epLog(`收到解析结果: 第${question.seqNo}题`)
  }

  /** Handle a WS task completed event. */
  async function handleWsTaskCompleted(
    taskUuid: string,
    status: string,
    questionCount?: number,
    errorMsg?: string
  ): Promise<void> {
    const task = tasks.value.get(taskUuid)
    if (task) {
      task.status = status as ExamParseTask['status']
      if (questionCount !== undefined) task.questionCount = questionCount
      if (errorMsg) task.errorMsg = errorMsg
    }
    epLog(`任务完成 ${taskUuid.slice(0, 8)} → ${status}`)

    // Auto-load questions on success (including partial success)
    if (status === 'SUCCESS' || status === 'PARTIAL_FAILED' || status === 'COMPLETED') {
      try {
        const auth = useAuthStore()
        if (auth.token) {
          await refreshQuestions(auth.token, taskUuid)
          // Auto-select first question if this is the active task
          if (activeTaskUuid.value === taskUuid && activeSeqNo.value === 0) {
            const qs = questions.value.get(taskUuid) ?? []
            if (qs.length > 0) {
              activeSeqNo.value = qs[0].seqNo
            }
          }
        }
      } catch (err) {
        epLog(`自动加载题目失败: ${err}`)
      }
    }
  }

  /** Handle task progress update from WS. */
  function handleWsProgress(taskUuid: string, progress: number): void {
    const task = tasks.value.get(taskUuid)
    if (task) task.progress = progress
  }

  // ── Persistence ──

  function storageKey(username: string): string {
    return `qforge.examparse.${username}`
  }

  function saveLocalState(username: string): void {
    const data = {
      tasks: Array.from(tasks.value.entries()),
      questions: Array.from(questions.value.entries()),
      questionFocus: questionFocus.value,
      activeTaskUuid: activeTaskUuid.value,
      activeSeqNo: activeSeqNo.value,
      logs: logs.value
    }
    try {
      localStorage.setItem(storageKey(username), JSON.stringify(data))
    } catch {
      // localStorage full — ignore
    }
  }

  function loadLocalState(username: string): void {
    const raw = localStorage.getItem(storageKey(username))
    if (!raw) return
    try {
      const data = JSON.parse(raw)
      if (data.tasks) tasks.value = new Map(data.tasks)
      if (data.questions) questions.value = new Map(data.questions)
      if (data.questionFocus) questionFocus.value = data.questionFocus
      if (data.activeTaskUuid) activeTaskUuid.value = data.activeTaskUuid
      if (data.activeSeqNo) activeSeqNo.value = data.activeSeqNo
      if (data.logs) logs.value = data.logs
    } catch {
      // Corrupted data — ignore
    }
  }

  // ── Reset ──

  function $reset(): void {
    tasks.value = new Map()
    questions.value = new Map()
    questionFocus.value = {}
    activeTaskUuid.value = ''
    activeSeqNo.value = 0
    logs.value = []
  }

  return {
    // state
    tasks,
    questions,
    questionFocus,
    activeTaskUuid,
    activeSeqNo,
    logs,
    // getters
    sortedTasks,
    activeTask,
    activeQuestions,
    activeQuestion,
    activeFocus,
    recentLogs,
    // actions — task
    createTask,
    refreshTaskList,
    refreshQuestions,
    deleteTask,
    // actions — question
    updateQuestion,
    confirmAll,
    confirmSingle,
    skipQuestion,
    unskipQuestion,
    // actions — focus / navigation
    selectTask,
    selectQuestion,
    setFocusStage,
    getFocus,
    prevQuestion,
    nextQuestion,
    // actions — WS events
    handleWsQuestionResult,
    handleWsTaskCompleted,
    handleWsProgress,
    // persistence
    saveLocalState,
    loadLocalState,
    // logging
    epLog,
    $reset
  }
})
