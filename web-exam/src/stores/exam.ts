/**
 * stores/exam.ts — Exam composition state management (API-backed).
 *
 * Maintains optimistic local state for instant UI updates,
 * syncs with backend via debounced PUT /api/exam-papers/{uuid}/content.
 */

import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { examPaperApi } from '@/api/examPaper'
import type {
  ExamPaperDetailResponse,
  ExamPaperOverviewResponse,
  SaveExamContentRequest,
  SaveExamContentSectionPayload
} from '@/api/types'
import type { QuestionEntry } from './question'

// ── Types (kept compatible with views) ──

export interface ExamQuestion {
  questionUuid: string
  /** Sequence number within the section. */
  seq: number
  /** Score for this question. */
  score: number
  /** Snapshot of the question data (for offline preview). */
  snapshot: QuestionEntry
  /** Optional teacher note. */
  note?: string
}

export interface ExamSection {
  id: string            // maps to sectionUuid
  title: string
  description: string
  questionTypeCode?: string
  defaultScore?: number
  questions: ExamQuestion[]
}

export interface ExamPaper {
  id: string            // maps to paperUuid
  title: string
  subtitle: string
  description?: string
  /** Total duration in minutes. */
  duration: number
  /** Total score (auto-calculated). */
  totalScore: number
  status: string
  sections: ExamSection[]
  createdAt: string
  updatedAt: string
}

function genId(): string {
  // UUID v4 compatible
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

function nowIso(): string {
  return new Date().toISOString()
}

export const useExamStore = defineStore('exam', () => {
  // ── State ──
  const exams = ref<ExamPaper[]>([])
  const activeExamId = ref<string | null>(null)
  const loading = ref(false)
  const saving = ref(false)

  // debounce timer
  let _saveTimer: ReturnType<typeof setTimeout> | null = null
  const SAVE_DELAY = 800 // ms

  // ── Getters ──
  const activeExam = computed(() =>
    exams.value.find((e) => e.id === activeExamId.value) ?? null
  )

  const examList = computed(() =>
    exams.value.map((e) => ({
      id: e.id,
      title: e.title,
      subtitle: e.subtitle,
      status: e.status,
      totalScore: e.totalScore,
      questionCount: e.sections.reduce((s, sec) => s + sec.questions.length, 0),
      updatedAt: e.updatedAt
    }))
  )

  // ── API operations ──

  /** Fetch exam paper list from backend. */
  async function fetchExams(): Promise<void> {
    loading.value = true
    try {
      const list: ExamPaperOverviewResponse[] = await examPaperApi.list()
      // Convert to local format — overview only (no sections/questions)
      exams.value = list.map((p) => ({
        id: p.paperUuid,
        title: p.title,
        subtitle: p.subtitle ?? '',
        status: p.status,
        duration: p.durationMinutes ?? 120,
        totalScore: p.totalScore,
        sections: [],   // lazy-loaded on detail
        createdAt: p.createdAt,
        updatedAt: p.updatedAt
      }))
    } catch (e) {
      console.error('Failed to fetch exams', e)
    } finally {
      loading.value = false
    }
  }

  /** Load full exam detail (sections + questions + stem). */
  async function loadExamDetail(paperUuid: string): Promise<ExamPaper | null> {
    loading.value = true
    try {
      const detail: ExamPaperDetailResponse = await examPaperApi.detail(paperUuid)
      const paper = detailToLocal(detail)
      // Merge into exams array
      const idx = exams.value.findIndex((e) => e.id === paperUuid)
      if (idx >= 0) {
        exams.value[idx] = paper
      } else {
        exams.value.push(paper)
      }
      return paper
    } catch (e) {
      console.error('Failed to load exam detail', e)
      return null
    } finally {
      loading.value = false
    }
  }

  /** Create a new exam paper via API. */
  async function createExam(title = '未命名试卷'): Promise<ExamPaper> {
    try {
      const detail = await examPaperApi.create({ title })
      const paper = detailToLocal(detail)
      exams.value.push(paper)
      activeExamId.value = paper.id
      return paper
    } catch (e) {
      console.error('Failed to create exam', e)
      // Fallback: create locally (will not persist)
      const paper: ExamPaper = {
        id: genId(),
        title,
        subtitle: '',
        status: 'DRAFT',
        duration: 120,
        totalScore: 0,
        sections: [{
          id: genId(),
          title: '一、选择题',
          description: '',
          questions: []
        }],
        createdAt: nowIso(),
        updatedAt: nowIso()
      }
      exams.value.push(paper)
      activeExamId.value = paper.id
      return paper
    }
  }

  /** Delete an exam paper via API. */
  async function deleteExam(examId: string): Promise<void> {
    try {
      await examPaperApi.delete(examId)
    } catch (e) {
      console.error('Failed to delete exam from server', e)
    }
    const idx = exams.value.findIndex((e) => e.id === examId)
    if (idx >= 0) exams.value.splice(idx, 1)
    if (activeExamId.value === examId) {
      activeExamId.value = exams.value[0]?.id ?? null
    }
  }

  /** Set active exam (and load detail if not already loaded). */
  async function setActiveExam(examId: string): Promise<void> {
    activeExamId.value = examId
    const exam = exams.value.find((e) => e.id === examId)
    // Load detail if sections are empty (not yet loaded)
    if (exam && exam.sections.length === 0) {
      await loadExamDetail(examId)
    }
  }

  // ── Local mutations (trigger debounced save) ──

  /** Update exam metadata. */
  function updateExamMeta(examId: string, meta: Partial<Pick<ExamPaper, 'title' | 'subtitle' | 'duration' | 'description'>>): void {
    const exam = exams.value.find((e) => e.id === examId)
    if (!exam) return
    Object.assign(exam, meta)
    exam.updatedAt = nowIso()
    debouncedSaveMeta(examId)
  }

  function addSection(examId: string, title = '新大题'): void {
    const exam = exams.value.find((e) => e.id === examId)
    if (!exam) return
    exam.sections.push({
      id: genId(),
      title,
      description: '',
      questions: []
    })
    exam.updatedAt = nowIso()
    debouncedSaveContent(examId)
  }

  function removeSection(examId: string, sectionId: string): void {
    const exam = exams.value.find((e) => e.id === examId)
    if (!exam) return
    exam.sections = exam.sections.filter((s) => s.id !== sectionId)
    recalcScores(exam)
    exam.updatedAt = nowIso()
    debouncedSaveContent(examId)
  }

  function updateSection(examId: string, sectionId: string, data: Partial<Pick<ExamSection, 'title' | 'description' | 'questionTypeCode' | 'defaultScore'>>): void {
    const exam = exams.value.find((e) => e.id === examId)
    if (!exam) return
    const sec = exam.sections.find((s) => s.id === sectionId)
    if (!sec) return
    Object.assign(sec, data)
    exam.updatedAt = nowIso()
    debouncedSaveContent(examId)
  }

  function addQuestionToSection(examId: string, sectionId: string, entry: QuestionEntry, score = 5): void {
    const exam = exams.value.find((e) => e.id === examId)
    if (!exam) return
    const sec = exam.sections.find((s) => s.id === sectionId)
    if (!sec) return
    if (sec.questions.some((q) => q.questionUuid === entry.questionUuid)) return

    sec.questions.push({
      questionUuid: entry.questionUuid,
      seq: sec.questions.length + 1,
      score,
      snapshot: { ...entry }
    })
    recalcScores(exam)
    exam.updatedAt = nowIso()
    debouncedSaveContent(examId)
  }

  function removeQuestion(examId: string, sectionId: string, questionUuid: string): void {
    const exam = exams.value.find((e) => e.id === examId)
    if (!exam) return
    const sec = exam.sections.find((s) => s.id === sectionId)
    if (!sec) return
    sec.questions = sec.questions.filter((q) => q.questionUuid !== questionUuid)
    sec.questions.forEach((q, i) => { q.seq = i + 1 })
    recalcScores(exam)
    exam.updatedAt = nowIso()
    debouncedSaveContent(examId)
  }

  function updateQuestionScore(examId: string, sectionId: string, questionUuid: string, score: number): void {
    const exam = exams.value.find((e) => e.id === examId)
    if (!exam) return
    const sec = exam.sections.find((s) => s.id === sectionId)
    if (!sec) return
    const q = sec.questions.find((q) => q.questionUuid === questionUuid)
    if (!q) return
    q.score = Math.max(0, score)
    recalcScores(exam)
    exam.updatedAt = nowIso()
    debouncedSaveContent(examId)
  }

  function moveQuestion(examId: string, sectionId: string, fromIdx: number, toIdx: number): void {
    const exam = exams.value.find((e) => e.id === examId)
    if (!exam) return
    const sec = exam.sections.find((s) => s.id === sectionId)
    if (!sec) return
    if (toIdx < 0 || toIdx >= sec.questions.length) return
    const [item] = sec.questions.splice(fromIdx, 1)
    sec.questions.splice(toIdx, 0, item)
    sec.questions.forEach((q, i) => { q.seq = i + 1 })
    exam.updatedAt = nowIso()
    debouncedSaveContent(examId)
  }

  function isQuestionInExam(questionUuid: string): boolean {
    const exam = activeExam.value
    if (!exam) return false
    return exam.sections.some((s) => s.questions.some((q) => q.questionUuid === questionUuid))
  }

  const selectedQuestionUuids = computed(() => {
    const exam = activeExam.value
    if (!exam) return new Set<string>()
    const uuids = new Set<string>()
    for (const sec of exam.sections) {
      for (const q of sec.questions) {
        uuids.add(q.questionUuid)
      }
    }
    return uuids
  })

  /** Duplicate an exam (creates a copy via API). */
  async function duplicateExam(examId: string): Promise<ExamPaper | null> {
    const src = exams.value.find((e) => e.id === examId)
    if (!src) return null
    try {
      const detail = await examPaperApi.create({ title: src.title + ' (副本)' })
      const copy = detailToLocal(detail)
      // Copy sections + questions
      copy.sections = JSON.parse(JSON.stringify(src.sections))
      for (const sec of copy.sections) { sec.id = genId() }
      copy.totalScore = src.totalScore
      exams.value.push(copy)
      // Save content to backend
      await saveContentNow(copy.id)
      return copy
    } catch (e) {
      console.error('Failed to duplicate exam', e)
      return null
    }
  }

  /** Export active paper to Word. */
  async function exportWord(paperUuid: string, includeAnswers = false): Promise<void> {
    const blob = await examPaperApi.exportWord(paperUuid, {
      includeAnswers,
      answerPosition: 'AFTER_ALL'
    })
    // Trigger download
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'exam-paper.docx'
    a.click()
    URL.revokeObjectURL(url)
  }

  // ── Debounced save helpers ──

  function debouncedSaveContent(examId: string): void {
    if (_saveTimer) clearTimeout(_saveTimer)
    _saveTimer = setTimeout(() => saveContentNow(examId), SAVE_DELAY)
  }

  let _metaTimer: ReturnType<typeof setTimeout> | null = null
  function debouncedSaveMeta(examId: string): void {
    if (_metaTimer) clearTimeout(_metaTimer)
    _metaTimer = setTimeout(async () => {
      const exam = exams.value.find((e) => e.id === examId)
      if (!exam) return
      try {
        await examPaperApi.update(examId, {
          title: exam.title,
          subtitle: exam.subtitle || undefined,
          description: exam.description || undefined,
          durationMinutes: exam.duration
        })
      } catch (e) {
        console.error('Failed to save exam metadata', e)
      }
    }, SAVE_DELAY)
  }

  async function saveContentNow(examId: string): Promise<void> {
    const exam = exams.value.find((e) => e.id === examId)
    if (!exam) return
    saving.value = true
    try {
      const sections: SaveExamContentSectionPayload[] = exam.sections.map((sec) => ({
        sectionUuid: sec.id,
        title: sec.title,
        description: sec.description || undefined,
        questionTypeCode: sec.questionTypeCode || undefined,
        defaultScore: sec.defaultScore,
        questions: sec.questions.map((q) => ({
          questionUuid: q.questionUuid,
          score: q.score,
          note: q.note || undefined
        }))
      }))
      await examPaperApi.saveContent(examId, { sections })
    } catch (e) {
      console.error('Failed to save exam content', e)
    } finally {
      saving.value = false
    }
  }

  // ── Conversion helpers ──

  function detailToLocal(d: ExamPaperDetailResponse): ExamPaper {
    return {
      id: d.paperUuid,
      title: d.title,
      subtitle: d.subtitle ?? '',
      description: d.description ?? undefined,
      duration: d.durationMinutes ?? 120,
      totalScore: d.totalScore,
      status: d.status,
      sections: d.sections.map((s) => ({
        id: s.sectionUuid,
        title: s.title,
        description: s.description ?? '',
        questionTypeCode: s.questionTypeCode ?? undefined,
        defaultScore: s.defaultScore,
        questions: s.questions.map((q, i) => ({
          questionUuid: q.questionUuid,
          seq: i + 1,
          score: q.score,
          note: q.note ?? undefined,
          snapshot: {
            questionUuid: q.questionUuid,
            status: 'READY' as const,
            stemText: q.stemText ?? '',
            mainTags: [],
            secondaryTags: [],
            difficulty: null,
            source: '未分类',
            answerCount: 0,
            createdAt: '',
            updatedAt: ''
          }
        }))
      })),
      createdAt: d.createdAt,
      updatedAt: d.updatedAt
    }
  }

  function recalcScores(exam: ExamPaper): void {
    exam.totalScore = exam.sections.reduce(
      (total, sec) => total + sec.questions.reduce((s, q) => s + q.score, 0),
      0
    )
  }

  function $reset(): void {
    exams.value = []
    activeExamId.value = null
  }

  // ── Backward compat (no-ops / aliases) ──
  function loadFromStorage(): void { /* no-op: data from API now */ }
  function saveToStorage(): void { /* no-op: data to API now */ }

  return {
    exams,
    activeExamId,
    activeExam,
    examList,
    loading,
    saving,
    selectedQuestionUuids,
    fetchExams,
    loadExamDetail,
    createExam,
    deleteExam,
    setActiveExam,
    updateExamMeta,
    addSection,
    removeSection,
    updateSection,
    addQuestionToSection,
    removeQuestion,
    updateQuestionScore,
    moveQuestion,
    isQuestionInExam,
    duplicateExam,
    exportWord,
    loadFromStorage,
    saveToStorage,
    $reset
  }
})

