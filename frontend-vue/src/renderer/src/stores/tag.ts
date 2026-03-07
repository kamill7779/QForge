/**
 * stores/tag.ts — Tag catalog state.
 *
 * Fetches and caches the global tag dictionary from backend.
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { tagApi } from '@/api/tag'
import type {
  TagCatalogResponse,
  MainTagCategoryResponse,
  TagOptionResponse
} from '@/api/types'

/** Default fallback catalog when backend hasn't responded yet. */
const DEFAULT_CATALOG: TagCatalogResponse = {
  mainCategories: [
    {
      categoryCode: 'MAIN_GRADE',
      categoryName: '年级',
      options: [
        { tagCode: 'GRADE_7', tagName: '七年级' },
        { tagCode: 'GRADE_8', tagName: '八年级' },
        { tagCode: 'GRADE_9', tagName: '九年级' },
        { tagCode: 'SENIOR_1', tagName: '高一' },
        { tagCode: 'SENIOR_2', tagName: '高二' },
        { tagCode: 'SENIOR_3', tagName: '高三' },
        { tagCode: 'COLLEGE_1', tagName: '大一' },
        { tagCode: 'COLLEGE_2', tagName: '大二' }
      ]
    },
    {
      categoryCode: 'MAIN_KNOWLEDGE',
      categoryName: '知识点',
      options: [
        { tagCode: 'SETS_LOGIC', tagName: '集合与逻辑' },
        { tagCode: 'FUNCTION', tagName: '函数' },
        { tagCode: 'EQUATION', tagName: '方程与不等式' },
        { tagCode: 'TRIGONOMETRY', tagName: '三角函数' },
        { tagCode: 'SEQUENCE', tagName: '数列' },
        { tagCode: 'VECTOR', tagName: '向量' },
        { tagCode: 'SOLID_GEOMETRY', tagName: '立体几何' },
        { tagCode: 'ANALYTIC_GEOMETRY', tagName: '解析几何' },
        { tagCode: 'PROBABILITY', tagName: '概率与统计' },
        { tagCode: 'DERIVATIVE', tagName: '导数' },
        { tagCode: 'INTEGRAL', tagName: '积分' },
        { tagCode: 'PLANE_GEOMETRY', tagName: '平面几何' },
        { tagCode: 'ALGEBRA', tagName: '代数' },
        { tagCode: 'NUMBER_THEORY', tagName: '数论' },
        { tagCode: 'COMBINATORICS', tagName: '排列组合' },
        { tagCode: 'LINEAR_ALGEBRA', tagName: '线性代数' }
      ]
    }
  ],
  secondaryCategoryCode: 'SECONDARY_CUSTOM',
  secondaryCategoryName: '副标签'
}

export const useTagStore = defineStore('tag', () => {
  // ── State ──

  const catalog = ref<TagCatalogResponse>({ ...DEFAULT_CATALOG })
  const loaded = ref(false)

  // ── Getters ──

  const mainCategories = computed(() => catalog.value.mainCategories)

  /** Flat map: tagCode → tagName for quick lookup. */
  const tagNameMap = computed(() => {
    const map = new Map<string, string>()
    for (const cat of catalog.value.mainCategories) {
      for (const opt of cat.options) {
        map.set(opt.tagCode, opt.tagName)
      }
    }
    return map
  })

  // ── Actions ──

  /** Fetch tag catalog from backend. */
  async function fetchCatalog(token: string): Promise<void> {
    const res = await tagApi.getCatalog(token)
    catalog.value = normalizeCatalog(res)
    loaded.value = true
  }

  /**
   * Ensure the catalog has at least the default categories.
   * Merges backend data with fallback defaults.
   */
  function normalizeCatalog(raw: TagCatalogResponse): TagCatalogResponse {
    const result: TagCatalogResponse = {
      mainCategories: raw.mainCategories?.length
        ? raw.mainCategories
        : DEFAULT_CATALOG.mainCategories,
      secondaryCategoryCode:
        raw.secondaryCategoryCode || DEFAULT_CATALOG.secondaryCategoryCode,
      secondaryCategoryName:
        raw.secondaryCategoryName || DEFAULT_CATALOG.secondaryCategoryName
    }
    return result
  }

  /** Get tag name by code, or return the code itself if not found. */
  function getTagName(tagCode: string): string {
    return tagNameMap.value.get(tagCode) ?? tagCode
  }

  function $reset(): void {
    catalog.value = { ...DEFAULT_CATALOG }
    loaded.value = false
  }

  return {
    catalog,
    loaded,
    mainCategories,
    tagNameMap,
    fetchCatalog,
    getTagName,
    $reset
  }
})
