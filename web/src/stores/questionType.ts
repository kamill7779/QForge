/**
 * stores/questionType.ts — Question type configuration store (API-driven).
 *
 * Replaces the hardcoded lib/questionType.ts with a backend-fetched, user-customizable catalog.
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { questionTypeApi } from '@/api/questionType'
import type { QuestionTypeResponse, SaveQuestionTypeRequest } from '@/api/types'

export const useQuestionTypeStore = defineStore('questionType', () => {
  const types = ref<QuestionTypeResponse[]>([])
  const loading = ref(false)

  /** All enabled types keyed by typeCode. */
  const typeMap = computed(() => {
    const map: Record<string, QuestionTypeResponse> = {}
    for (const t of types.value) {
      map[t.typeCode] = t
    }
    return map
  })

  /** Get display label for a type code; falls back to code itself. */
  function labelOf(code: string): string {
    return typeMap.value[code]?.typeLabel ?? code
  }

  /** Entries for dropdowns: [code, label][]. */
  const typeEntries = computed(() =>
    types.value.map((t) => [t.typeCode, t.typeLabel] as [string, string])
  )

  async function fetchTypes(): Promise<void> {
    loading.value = true
    try {
      types.value = await questionTypeApi.list()
    } catch (e) {
      console.error('Failed to fetch question types', e)
    } finally {
      loading.value = false
    }
  }

  async function createCustomType(req: SaveQuestionTypeRequest): Promise<QuestionTypeResponse | null> {
    try {
      const created = await questionTypeApi.create(req)
      types.value.push(created)
      return created
    } catch (e) {
      console.error('Failed to create question type', e)
      return null
    }
  }

  async function updateCustomType(id: number, req: SaveQuestionTypeRequest): Promise<void> {
    try {
      const updated = await questionTypeApi.update(id, req)
      const idx = types.value.findIndex((t) => t.id === id)
      if (idx >= 0) types.value[idx] = updated
    } catch (e) {
      console.error('Failed to update question type', e)
    }
  }

  async function deleteCustomType(id: number): Promise<void> {
    try {
      await questionTypeApi.delete(id)
      types.value = types.value.filter((t) => t.id !== id)
    } catch (e) {
      console.error('Failed to delete question type', e)
    }
  }

  return {
    types,
    loading,
    typeMap,
    typeEntries,
    labelOf,
    fetchTypes,
    createCustomType,
    updateCustomType,
    deleteCustomType
  }
})
