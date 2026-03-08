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

  // ── State ──
  const allQuestions = ref<QuestionEntry[]>([])
  const loading = ref(false)
  const filterKeyword = ref('')

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
  const questions = computed(() => {
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

  const totalCount = computed(() => allQuestions.value.length)
  const filteredCount = computed(() => questions.value.length)
  const hasMore = computed(() => false) // all data loaded at once

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

  /** Fetch all questions from backend. */
  async function fetchQuestions(): Promise<void> {
    if (!auth.token) return
    if (loading.value) return
    loading.value = true
    try {
      const list = await questionApi.list(auth.token)
      allQuestions.value = (Array.isArray(list) ? list : []).map((q: QuestionOverviewResponse) => ({
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
      }))
      // Refresh source options
      fetchSources()
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
    fetchSources()
  }

  /** No-op: backend returns all questions at once. */
  async function loadMore(): Promise<void> {}

  /** Client-side keyword filter. */
  async function searchQuestions(keyword: string): Promise<void> {
    if (!allQuestions.value.length) await fetchQuestions()
    filterKeyword.value = keyword.toLowerCase()
  }

  function clearSearch(): void {
    filterKeyword.value = ''
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
  }

  /** Clear all filters. */
  function clearAllFilters(): void {
    filterKeyword.value = ''
    filterGrade.value = ''
    filterKnowledge.value = ''
    filterDifficulty.value = ''
    filterSource.value = ''
  }

  function $reset(): void {
    allQuestions.value = []
    loading.value = false
    filterKeyword.value = ''
    filterGrade.value = ''
    filterKnowledge.value = ''
    filterDifficulty.value = ''
    filterSource.value = ''
    sourceOptions.value = []
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
    loadMore,
    searchQuestions,
    clearSearch,
    setDimensionFilter,
    clearAllFilters,
    $reset
  }
})
