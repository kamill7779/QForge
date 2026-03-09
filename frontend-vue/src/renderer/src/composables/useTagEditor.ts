/**
 * composables/useTagEditor.ts — Tag editing composable.
 *
 * Eliminates 4× duplication of tag editing logic across views.
 */

import { ref, computed } from 'vue'
import { useTagStore } from '@/stores/tag'
import type { QuestionMainTagResponse } from '@/api/types'

export interface TagPayload {
  /** Flat array of all tag codes for UpdateTagsRequest. */
  tags: string[]
}

export interface UseTagEditorOptions {
  readonly?: boolean
}

/**
 * Composable for managing main tags (category pickers) and secondary tags (capsules).
 */
export function useTagEditor(options: UseTagEditorOptions = {}) {
  const tagStore = useTagStore()

  const catalog = computed(() => tagStore.catalog)
  const mainCategories = computed(() => tagStore.mainCategories)

  /**
   * Main tags: one selected value per category.
   * Format: Record<categoryCode, tagCode>
   */
  const mainTagSelection = ref<Record<string, string>>({})

  /** Secondary tags: free-form string array. */
  const secondaryTags = ref<string[]>([])

  /** Secondary tag input text. */
  const secondaryInput = ref('')

  // ── Initialization ──

  /** Initialize from existing question data. */
  function initFromQuestion(
    existingMainTags: QuestionMainTagResponse[],
    existingSecondaryTags: string[]
  ): void {
    // Main tags: map to per-category selection
    mainTagSelection.value = {}
    for (const t of existingMainTags) {
      mainTagSelection.value[t.categoryCode] = t.tagCode
    }
    // Secondary tags
    secondaryTags.value = [...existingSecondaryTags]
  }

  // ── Main Tag Actions ──

  /** Set a main tag for a specific category. */
  function setMainTag(categoryCode: string, tagCode: string): void {
    if (options.readonly) return
    mainTagSelection.value[categoryCode] = tagCode
  }

  /** Clear a main tag for a specific category. */
  function clearMainTag(categoryCode: string): void {
    if (options.readonly) return
    delete mainTagSelection.value[categoryCode]
  }

  // ── Secondary Tag Actions ──

  /** Add a secondary tag from the input. */
  function addSecondaryTag(tag?: string): void {
    if (options.readonly) return
    const t = (tag || secondaryInput.value).trim()
    if (!t) return
    if (!secondaryTags.value.includes(t)) {
      secondaryTags.value.push(t)
    }
    secondaryInput.value = ''
  }

  /** Remove a secondary tag by index. */
  function removeSecondaryTag(index: number): void {
    if (options.readonly) return
    secondaryTags.value.splice(index, 1)
  }

  /** Parse space/comma-separated input and add multiple tags. */
  function addFromInput(): void {
    const input = secondaryInput.value.trim()
    if (!input) return
    const parts = input.split(/[,，\s]+/).filter(Boolean)
    for (const p of parts) {
      if (!secondaryTags.value.includes(p)) {
        secondaryTags.value.push(p)
      }
    }
    secondaryInput.value = ''
  }

  // ── Payload ──

  /** Build the flat tag code array for API submission. */
  function toPayload(): TagPayload {
    const tags: string[] = []
    // Main tags
    for (const code of Object.values(mainTagSelection.value)) {
      if (code) tags.push(code)
    }
    // Secondary tags as-is
    for (const t of secondaryTags.value) {
      if (t) tags.push(t)
    }
    return { tags }
  }

  return {
    catalog,
    mainCategories,
    mainTagSelection,
    secondaryTags,
    secondaryInput,
    initFromQuestion,
    setMainTag,
    clearMainTag,
    addSecondaryTag,
    removeSecondaryTag,
    addFromInput,
    toPayload
  }
}
