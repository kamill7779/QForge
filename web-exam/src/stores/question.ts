/**
 * stores/question.ts — Simplified question store for web exam composition.
 *
 * Backend returns a flat array (no server-side pagination).
 * Search is client-side filtering on stemText / tag names.
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { questionApi } from '@/api/question'
import { useAuthStore } from '@/stores/auth'
import type {
  QuestionOverviewResponse,
  QuestionMainTagResponse
} from '@/api/types'

/** Simplified question entry for bank browsing. */
export interface QuestionEntry {
  questionUuid: string
  status: string
  stemText: string
  mainTags: QuestionMainTagResponse[]
  secondaryTags: string[]
  difficulty: number | null
  answerCount: number
  createdAt: string
  updatedAt: string
}

export const useQuestionStore = defineStore('question', () => {
  const auth = useAuthStore()

  // ── State ──
  const allQuestions = ref<QuestionEntry[]>([])
  const loading = ref(false)
  const filterKeyword = ref('')

  // ── Getters ──
  const questions = computed(() => {
    if (!filterKeyword.value) return allQuestions.value
    const kw = filterKeyword.value.toLowerCase()
    return allQuestions.value.filter(q =>
      q.stemText.toLowerCase().includes(kw) ||
      q.mainTags.some(t => t.tagName.toLowerCase().includes(kw)) ||
      q.secondaryTags.some(t => t.toLowerCase().includes(kw))
    )
  })

  const totalCount = computed(() => allQuestions.value.length)
  const hasMore = computed(() => false) // all data loaded at once

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
        answerCount: q.answerCount ?? 0,
        createdAt: q.createdAt || '',
        updatedAt: q.updatedAt || ''
      }))
    } finally {
      loading.value = false
    }
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

  function $reset(): void {
    allQuestions.value = []
    loading.value = false
    filterKeyword.value = ''
  }

  return {
    questions,
    loading,
    totalCount,
    hasMore,
    fetchQuestions,
    loadMore,
    searchQuestions,
    clearSearch,
    $reset
  }
})
