/**
 * stores/question.ts — Question store for web exam composition.
 *
 * Backend returns a flat array (no server-side pagination).
 * Supports keyword search + multi-dimension filter (grade, knowledge, difficulty, source).
 * Provides a categoryTree computed for the sidebar tree view.
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { questionApi } from '@/api/question'
import { useAuthStore } from '@/stores/auth'
import type {
  QuestionPageResponse,
  QuestionOverviewResponse,
  QuestionMainTagResponse
} from '@/api/types'
import { difficultyLevel } from '@/lib/difficulty'

/** Answer entry (from backend overview). */
export interface AnswerEntry {
  answerUuid: string
  latexText: string
  sortOrder: number
  official: boolean
}

/** Simplified question entry for bank browsing. */
export interface QuestionEntry {
  questionUuid: string
  status: string
  stemText: string
  mainTags: QuestionMainTagResponse[]
  secondaryTags: string[]
  difficulty: number | null
  source: string
  answerCount: number
  answers: AnswerEntry[]
  createdAt: string
  updatedAt: string
}

/** Tree node for sidebar category tree. */
export interface TreeNode {
  key: string
  label: string
  count: number
  children?: TreeNode[]
  /** dimension + value for filtering */
  dimension?: 'grade' | 'knowledge' | 'difficulty' | 'source'
  value?: string
}

export const useQuestionStore = defineStore('question', () => {
  const auth = useAuthStore()
  const BACKEND_PAGE_SIZE = 100
  const UI_PAGE_SIZE = 20

  // ── State ──
  const allQuestions = ref<QuestionEntry[]>([])
  const loading = ref(false)
  const filterKeyword = ref('')
  const serverTotal = ref(0)
  const backendPage = ref(0)
  const serverHasMore = ref(true)
  const visibleCount = ref(UI_PAGE_SIZE)
  const detailCache = ref<Record<string, QuestionEntry>>({})

  // ── Dimension filters (empty = no filter) ──
  const filterGrade = ref('')
  const filterKnowledge = ref('')
  const filterDifficulty = ref('')
  const filterSource = ref('')

  // ── Source options cache ──
  const sourceOptions = ref<string[]>([])

  // ── Category tree (built from current data) ──
  const categoryTree = computed<TreeNode[]>(() => {
    const qs = allQuestions.value
    // Grade
    const gradeMap = new Map<string, { code: string; name: string; count: number }>()
    // Knowledge
    const knowledgeMap = new Map<string, { code: string; name: string; count: number }>()
    // Difficulty
    const diffMap = new Map<string, number>()
    // Source
    const srcMap = new Map<string, number>()

    for (const q of qs) {
      for (const t of q.mainTags) {
        if (t.categoryCode === 'MAIN_GRADE') {
          const key = t.tagCode
          const existing = gradeMap.get(key)
          if (existing) existing.count++
          else gradeMap.set(key, { code: t.tagCode, name: t.tagName, count: 1 })
        } else if (t.categoryCode === 'MAIN_KNOWLEDGE') {
          const key = t.tagCode
          const existing = knowledgeMap.get(key)
          if (existing) existing.count++
          else knowledgeMap.set(key, { code: t.tagCode, name: t.tagName, count: 1 })
        }
      }
      const dl = q.difficulty !== null ? difficultyLevel(q.difficulty).label : '未设置'
      diffMap.set(dl, (diffMap.get(dl) ?? 0) + 1)
      const src = q.source || '未分类'
      srcMap.set(src, (srcMap.get(src) ?? 0) + 1)
    }

    const tree: TreeNode[] = []

    // Grade branch
    if (gradeMap.size > 0) {
      tree.push({
        key: 'grade',
        label: '年级',
        count: qs.length,
        children: [...gradeMap.values()].map((g) => ({
          key: `grade:${g.code}`,
          label: g.name,
          count: g.count,
          dimension: 'grade' as const,
          value: g.code
        }))
      })
    }

    // Knowledge branch
    if (knowledgeMap.size > 0) {
      tree.push({
        key: 'knowledge',
        label: '知识点',
        count: qs.length,
        children: [...knowledgeMap.values()].map((k) => ({
          key: `knowledge:${k.code}`,
          label: k.name,
          count: k.count,
          dimension: 'knowledge' as const,
          value: k.code
        }))
      })
    }

    // Difficulty branch
    if (diffMap.size > 0) {
      tree.push({
        key: 'difficulty',
        label: '难度',
        count: qs.length,
        children: [...diffMap.entries()].map(([label, count]) => ({
          key: `difficulty:${label}`,
          label,
          count,
          dimension: 'difficulty' as const,
          value: label
        }))
      })
    }

    // Source branch
    if (srcMap.size > 0) {
      tree.push({
        key: 'source',
        label: '来源',
        count: qs.length,
        children: [...srcMap.entries()]
          .sort((a, b) => a[0].localeCompare(b[0]))
          .map(([src, count]) => ({
            key: `source:${src}`,
            label: src,
            count,
            dimension: 'source' as const,
            value: src
          }))
      })
    }

    return tree
  })

  // ── Getters ──
  const filteredQuestions = computed(() => {
    let result = allQuestions.value

    // Dimension filters
    if (filterGrade.value) {
      result = result.filter((q) =>
        q.mainTags.some((t) => t.categoryCode === 'MAIN_GRADE' && t.tagCode === filterGrade.value)
      )
    }
    if (filterKnowledge.value) {
      result = result.filter((q) =>
        q.mainTags.some((t) => t.categoryCode === 'MAIN_KNOWLEDGE' && t.tagCode === filterKnowledge.value)
      )
    }
    if (filterDifficulty.value) {
      result = result.filter((q) => {
        const dl = q.difficulty !== null ? difficultyLevel(q.difficulty).label : '未设置'
        return dl === filterDifficulty.value
      })
    }
    if (filterSource.value) {
      result = result.filter((q) => (q.source || '未分类') === filterSource.value)
    }

    // Keyword filter
    if (filterKeyword.value) {
      const kw = filterKeyword.value.toLowerCase()
      result = result.filter((q) =>
        q.stemText.toLowerCase().includes(kw) ||
        q.mainTags.some((t) => t.tagName.toLowerCase().includes(kw)) ||
        q.secondaryTags.some((t) => t.toLowerCase().includes(kw)) ||
        q.source.toLowerCase().includes(kw)
      )
    }

    return result
  })

  const questions = computed(() => filteredQuestions.value.slice(0, visibleCount.value))
  const totalCount = computed(() => serverTotal.value || allQuestions.value.length)
  const filteredCount = computed(() => filteredQuestions.value.length)
  const hasMore = computed(() =>
    visibleCount.value < filteredQuestions.value.length || serverHasMore.value
  )

  /** Active filter summary for display. */
  const activeFilterCount = computed(() => {
    let c = 0
    if (filterGrade.value) c++
    if (filterKnowledge.value) c++
    if (filterDifficulty.value) c++
    if (filterSource.value) c++
    if (filterKeyword.value) c++
    return c
  })

  // ── Actions ──

  function normalizeQuestion(q: QuestionOverviewResponse): QuestionEntry {
    return {
      questionUuid: q.questionUuid,
      status: q.status,
      stemText: q.stemText || '',
      mainTags: q.mainTags || [],
      secondaryTags: q.secondaryTags || [],
      difficulty: q.difficulty ?? null,
      source: q.source || '未分类',
      answerCount: q.answerCount ?? 0,
      answers: (q.answers ?? []).map(a => ({
        answerUuid: a.answerUuid,
        latexText: a.latexText,
        sortOrder: a.sortOrder,
        official: a.official
      })),
      createdAt: q.createdAt || '',
      updatedAt: q.updatedAt || ''
    }
  }

  function mergeQuestions(entries: QuestionEntry[], replace = false): void {
    const merged = replace ? new Map<string, QuestionEntry>() : new Map(
      allQuestions.value.map(item => [item.questionUuid, item] as const)
    )
    for (const entry of entries) {
      merged.set(entry.questionUuid, entry)
    }
    allQuestions.value = [...merged.values()]
  }

  /** Fetch one backend page (100 by default) and let the UI consume it in 20-item slices. */
  async function fetchQuestions(reset = true): Promise<void> {
    if (!auth.token) return
    if (loading.value) return
    if (!reset && !serverHasMore.value) return
    loading.value = true
    try {
      const nextPage = reset ? 1 : backendPage.value + 1
      const res: QuestionPageResponse = await questionApi.listPage(auth.token, nextPage, BACKEND_PAGE_SIZE)
      const entries = (Array.isArray(res.items) ? res.items : []).map(normalizeQuestion)
      backendPage.value = res.page || nextPage
      serverTotal.value = Number.isFinite(res.total) ? res.total : entries.length
      serverHasMore.value = !!res.hasMore
      if (reset) {
        visibleCount.value = UI_PAGE_SIZE
        mergeQuestions(entries, true)
      } else {
        mergeQuestions(entries, false)
      }
      await fetchSources()
    } finally {
      loading.value = false
    }
  }

  /** Fetch distinct source values from backend. */
  async function fetchSources(): Promise<void> {
    if (!auth.token) return
    try {
      const list = await questionApi.listSources(auth.token)
      sourceOptions.value = Array.isArray(list) ? list : []
    } catch {
      // Derive from local data
      const set = new Set(allQuestions.value.map((q) => q.source || '未分类'))
      sourceOptions.value = [...set].sort()
    }
  }

  /** Update source of a question. */
  async function updateSource(uuid: string, source: string): Promise<void> {
    if (!auth.token) return
    await questionApi.updateSource(auth.token, uuid, { source })
    const q = allQuestions.value.find((q) => q.questionUuid === uuid)
    if (q) q.source = source
    const detail = detailCache.value[uuid]
    if (detail) detail.source = source
    await fetchSources()
  }

  async function fetchQuestionDetail(uuid: string): Promise<QuestionEntry | null> {
    if (!auth.token) return null
    const cached = detailCache.value[uuid] ?? allQuestions.value.find(q => q.questionUuid === uuid)
    if (cached?.answers?.length) {
      detailCache.value[uuid] = cached
      return cached
    }
    const detail = normalizeQuestion(await questionApi.detail(auth.token, uuid))
    detailCache.value[uuid] = detail
    mergeQuestions([detail], false)
    return detail
  }

  async function loadMore(): Promise<void> {
    if (visibleCount.value < filteredQuestions.value.length) {
      visibleCount.value += UI_PAGE_SIZE
      return
    }
    if (serverHasMore.value) {
      const before = allQuestions.value.length
      await fetchQuestions(false)
      if (allQuestions.value.length > before || visibleCount.value < filteredQuestions.value.length) {
        visibleCount.value += UI_PAGE_SIZE
      }
    }
  }

  /** Client-side keyword filter. */
  async function searchQuestions(keyword: string): Promise<void> {
    if (!allQuestions.value.length) await fetchQuestions()
    filterKeyword.value = keyword.toLowerCase()
    visibleCount.value = UI_PAGE_SIZE
  }

  function clearSearch(): void {
    filterKeyword.value = ''
    visibleCount.value = UI_PAGE_SIZE
  }

  /** Set a dimension filter from tree node click. */
  function setDimensionFilter(dimension: string, value: string): void {
    switch (dimension) {
      case 'grade':
        filterGrade.value = filterGrade.value === value ? '' : value
        break
      case 'knowledge':
        filterKnowledge.value = filterKnowledge.value === value ? '' : value
        break
      case 'difficulty':
        filterDifficulty.value = filterDifficulty.value === value ? '' : value
        break
      case 'source':
        filterSource.value = filterSource.value === value ? '' : value
        break
    }
    visibleCount.value = UI_PAGE_SIZE
  }

  /** Clear all filters. */
  function clearAllFilters(): void {
    filterKeyword.value = ''
    filterGrade.value = ''
    filterKnowledge.value = ''
    filterDifficulty.value = ''
    filterSource.value = ''
    visibleCount.value = UI_PAGE_SIZE
  }

  function $reset(): void {
    allQuestions.value = []
    loading.value = false
    serverTotal.value = 0
    backendPage.value = 0
    serverHasMore.value = true
    visibleCount.value = UI_PAGE_SIZE
    filterKeyword.value = ''
    filterGrade.value = ''
    filterKnowledge.value = ''
    filterDifficulty.value = ''
    filterSource.value = ''
    sourceOptions.value = []
    detailCache.value = {}
  }

  return {
    allQuestions,
    questions,
    loading,
    totalCount,
    filteredCount,
    hasMore,
    activeFilterCount,
    filterKeyword,
    filterGrade,
    filterKnowledge,
    filterDifficulty,
    filterSource,
    sourceOptions,
    categoryTree,
    fetchQuestions,
    fetchSources,
    updateSource,
    fetchQuestionDetail,
    loadMore,
    searchQuestions,
    clearSearch,
    setDimensionFilter,
    clearAllFilters,
    $reset
  }
})
