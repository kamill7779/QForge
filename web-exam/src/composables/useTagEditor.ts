/**
 * composables/useTagEditor.ts — Tag editing composable.
 */

import { ref, computed } from 'vue'
import { useTagStore } from '@/stores/tag'
import type { QuestionMainTagResponse } from '@/api/types'

export interface TagPayload {
  tags: string[]
}

export interface UseTagEditorOptions {
  readonly?: boolean
}

export function useTagEditor(options: UseTagEditorOptions = {}) {
  const tagStore = useTagStore()

  const catalog = computed(() => tagStore.catalog)
  const mainCategories = computed(() => tagStore.mainCategories)
  const mainTagSelection = ref<Record<string, string>>({})
  const secondaryTags = ref<string[]>([])
  const secondaryInput = ref('')

  function initFromQuestion(existingMainTags: QuestionMainTagResponse[], existingSecondaryTags: string[]): void {
    mainTagSelection.value = {}
    for (const t of existingMainTags) {
      mainTagSelection.value[t.categoryCode] = t.tagCode
    }
    secondaryTags.value = [...existingSecondaryTags]
  }

  function setMainTag(categoryCode: string, tagCode: string): void {
    if (options.readonly) return
    mainTagSelection.value[categoryCode] = tagCode
  }

  function clearMainTag(categoryCode: string): void {
    if (options.readonly) return
    delete mainTagSelection.value[categoryCode]
  }

  function addSecondaryTag(tag?: string): void {
    if (options.readonly) return
    const t = (tag || secondaryInput.value).trim()
    if (!t) return
    if (!secondaryTags.value.includes(t)) secondaryTags.value.push(t)
    secondaryInput.value = ''
  }

  function removeSecondaryTag(index: number): void {
    if (options.readonly) return
    secondaryTags.value.splice(index, 1)
  }

  function addFromInput(): void {
    const input = secondaryInput.value.trim()
    if (!input) return
    const parts = input.split(/[,，\s]+/).filter(Boolean)
    for (const p of parts) {
      if (!secondaryTags.value.includes(p)) secondaryTags.value.push(p)
    }
    secondaryInput.value = ''
  }

  function toPayload(): TagPayload {
    const tags: string[] = []
    for (const code of Object.values(mainTagSelection.value)) {
      if (code) tags.push(code)
    }
    for (const t of secondaryTags.value) {
      if (t) tags.push(t)
    }
    return { tags }
  }

  return {
    catalog, mainCategories, mainTagSelection, secondaryTags, secondaryInput,
    initFromQuestion, setMainTag, clearMainTag, addSecondaryTag, removeSecondaryTag,
    addFromInput, toPayload
  }
}
