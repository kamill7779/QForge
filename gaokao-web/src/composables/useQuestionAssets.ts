import { reactive, ref } from 'vue'
import { gaokaoApi } from '@/api/gaokao'

const assetCache = reactive(new Map<string, Map<string, string>>())
const loadingSet = new Set<string>()
const assetVersion = ref(0)

export function useQuestionAssets() {
  async function loadAssets(draftQuestionUuid: string): Promise<void> {
    if (!draftQuestionUuid || assetCache.has(draftQuestionUuid) || loadingSet.has(draftQuestionUuid)) return
    loadingSet.add(draftQuestionUuid)
    try {
      const assets = await gaokaoApi.getDraftQuestionAssets(draftQuestionUuid)
      const map = new Map<string, string>()
      for (const asset of assets) {
        if (asset.refKey && asset.imageBase64) {
          map.set(asset.refKey, asset.imageBase64)
        }
      }
      assetCache.set(draftQuestionUuid, map)
      assetVersion.value++
    } catch {
      assetCache.set(draftQuestionUuid, new Map())
      assetVersion.value++
    } finally {
      loadingSet.delete(draftQuestionUuid)
    }
  }

  function resolverFor(draftQuestionUuid: string): (ref: string) => string {
    return (ref: string) => assetCache.get(draftQuestionUuid)?.get(ref) ?? ''
  }

  return { loadAssets, resolverFor, assetCache, assetVersion }
}