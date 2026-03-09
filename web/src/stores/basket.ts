/**
 * stores/basket.ts — Question basket (cart) state management.
 *
 * Lightweight store that tracks which questions are selected into the basket.
 * Uses backend API for persistence, with a local reactive Set for instant UI feedback.
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { basketApi } from '@/api/basket'
import type { BasketItemResponse } from '@/api/types'

export const useBasketStore = defineStore('basket', () => {
  // ── state ──
  const items = ref<BasketItemResponse[]>([])
  const uuidSet = ref<Set<string>>(new Set())
  const loading = ref(false)
  const initialized = ref(false)

  // ── getters ──
  const count = computed(() => uuidSet.value.size)
  const isEmpty = computed(() => uuidSet.value.size === 0)

  function isInBasket(questionUuid: string): boolean {
    return uuidSet.value.has(questionUuid)
  }

  // ── actions ──

  /**
   * Initialize basket: load UUIDs from backend.
   * Called once on app mount; subsequent calls are no-ops.
   */
  async function init(): Promise<void> {
    if (initialized.value) return
    loading.value = true
    try {
      const uuids = await basketApi.listUuids()
      uuidSet.value = new Set(uuids)
      initialized.value = true
    } catch (e) {
      console.error('Failed to init basket', e)
    } finally {
      loading.value = false
    }
  }

  /**
   * Load full basket items (with stemText etc.) — used by BasketView.
   */
  async function fetchItems(): Promise<void> {
    loading.value = true
    try {
      items.value = await basketApi.list()
      uuidSet.value = new Set(items.value.map(i => i.questionUuid))
      initialized.value = true
    } catch (e) {
      console.error('Failed to fetch basket items', e)
    } finally {
      loading.value = false
    }
  }

  /**
   * Toggle a question in/out of the basket.
   * Optimistically updates local state, rolls back on failure.
   */
  async function toggle(questionUuid: string): Promise<boolean> {
    const wasIn = uuidSet.value.has(questionUuid)

    // Optimistic update
    const newSet = new Set(uuidSet.value)
    if (wasIn) {
      newSet.delete(questionUuid)
    } else {
      newSet.add(questionUuid)
    }
    uuidSet.value = newSet

    try {
      const result = await basketApi.toggle(questionUuid)
      // Sync with server response
      const syncSet = new Set(uuidSet.value)
      if (result.inBasket) {
        syncSet.add(questionUuid)
      } else {
        syncSet.delete(questionUuid)
      }
      uuidSet.value = syncSet
      return result.inBasket
    } catch (e) {
      // Rollback
      const rollbackSet = new Set(uuidSet.value)
      if (wasIn) {
        rollbackSet.add(questionUuid)
      } else {
        rollbackSet.delete(questionUuid)
      }
      uuidSet.value = rollbackSet
      console.error('Failed to toggle basket item', e)
      throw e
    }
  }

  /**
   * Clear the entire basket.
   */
  async function clear(): Promise<void> {
    const backup = new Set(uuidSet.value)
    uuidSet.value = new Set()
    items.value = []
    try {
      await basketApi.clear()
    } catch (e) {
      uuidSet.value = backup
      console.error('Failed to clear basket', e)
      throw e
    }
  }

  function $reset() {
    items.value = []
    uuidSet.value = new Set()
    loading.value = false
    initialized.value = false
  }

  return {
    // state
    items,
    uuidSet,
    loading,
    initialized,
    // getters
    count,
    isEmpty,
    isInBasket,
    // actions
    init,
    fetchItems,
    toggle,
    clear,
    $reset
  }
})
