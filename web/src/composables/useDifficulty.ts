/**
 * composables/useDifficulty.ts — Difficulty reactive helpers.
 */

import { ref, computed, watch } from 'vue'
import { difficultyLevel, type DifficultyLevel } from '@/lib/difficulty'

export interface UseDifficultyOptions {
  initialValue?: number
  onChange?: (value: number) => void
}

export function useDifficulty(options: UseDifficultyOptions = {}) {
  const pValue = ref(options.initialValue ?? 0.5)
  const level = computed<DifficultyLevel>(() => difficultyLevel(pValue.value))
  const label = computed(() => level.value.label)
  const cssClass = computed(() => level.value.cssClass)

  watch(pValue, (val) => {
    options.onChange?.(val)
  })

  function setValue(val: number): void {
    pValue.value = Math.max(0, Math.min(1, Math.round(val * 100) / 100))
  }

  return { pValue, level, label, cssClass, setValue }
}
