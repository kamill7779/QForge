/**
 * composables/useQuestionAssets.ts — Shared image asset loading for question rendering.
 *
 * Provides a global cache of question image assets and resolver functions
 * that can be passed to LatexPreview's imageResolver prop.
 */

import { reactive, ref } from 'vue'
import { questionApi } from '@/api/question'
import { useAuthStore } from '@/stores/auth'
import type { QuestionAssetResponse } from '@/api/types'

/** Global cache: questionUuid → Map<refKey, base64ImageData> */
const assetCache = reactive(new Map<string, Map<string, string>>())
const loadingSet = new Set<string>()
const assetVersion = ref(0)

export function useQuestionAssets() {
  const auth = useAuthStore()

  /** Load assets for a single question (skips if already cached). */
  async function loadAssets(questionUuid: string): Promise<void> {
    if (assetCache.has(questionUuid) || loadingSet.has(questionUuid)) return
    if (!auth.token) return
    loadingSet.add(questionUuid)
    try {
      const assets: QuestionAssetResponse[] = await questionApi.getAssets(auth.token, questionUuid)
      const map = new Map<string, string>()
      for (const a of assets) {
        map.set(a.refKey, a.imageData)
      }
      assetCache.set(questionUuid, map)
      assetVersion.value++
    } catch {
      // Mark as loaded (no assets) to avoid repeated fetches
      assetCache.set(questionUuid, new Map())
      assetVersion.value++
    } finally {
      loadingSet.delete(questionUuid)
    }
  }

  /** Batch-load assets for multiple questions concurrently. */
  async function loadAssetsForMany(uuids: string[]): Promise<void> {
    const unloaded = uuids.filter(u => !assetCache.has(u) && !loadingSet.has(u))
    if (!unloaded.length) return
    await Promise.allSettled(unloaded.map(u => loadAssets(u)))
  }

  /** Create an imageResolver function bound to a specific question's assets. */
  function resolverFor(questionUuid: string): (ref: string) => string {
    return (ref: string) => {
      const map = assetCache.get(questionUuid)
      return map?.get(ref) ?? ''
    }
  }

  return { loadAssets, loadAssetsForMany, resolverFor, assetCache, assetVersion }
}
