/**
 * composables/useDifficulty.ts — Difficulty reactive helpers.
 *
 * Wraps lib/difficulty.ts with reactive refs for use in components.
 */

import { ref, computed, watch, type Ref } from 'vue'
import { difficultyLevel, type DifficultyLevel } from '@/lib/difficulty'

export interface UseDifficultyOptions {
  /** Initial P-value (0-100). */
  initialValue?: number
  /** Called when the value changes. */
  onChange?: (value: number) => void
}

/**
 * Composable for reactive difficulty management.
 * Provides a ref for the P-value and computed level/label/cssClass.
 */
export function useDifficulty(options: UseDifficultyOptions = {}) {
  const pValue = ref(options.initialValue ?? 50)
  const level = computed<DifficultyLevel>(() => difficultyLevel(pValue.value))
  const label = computed(() => level.value.label)
  const cssClass = computed(() => level.value.cssClass)

  watch(pValue, (val) => {
    options.onChange?.(val)
  })

  /** Set the P-value directly. */
  function setValue(val: number): void {
    pValue.value = Math.max(0, Math.min(100, Math.round(val)))
  }

  return { pValue, level, label, cssClass, setValue }
}
