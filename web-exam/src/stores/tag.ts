/**
 * stores/tag.ts — Tag catalog state.
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { tagApi } from '@/api/tag'
import type { TagCatalogResponse } from '@/api/types'

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
        { tagCode: 'SENIOR_3', tagName: '高三' }
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
        { tagCode: 'DERIVATIVE', tagName: '导数' }
      ]
    }
  ],
  secondaryCategoryCode: 'SECONDARY_CUSTOM',
  secondaryCategoryName: '副标签'
}

export const useTagStore = defineStore('tag', () => {
  const catalog = ref<TagCatalogResponse>({ ...DEFAULT_CATALOG })
  const loaded = ref(false)

  const mainCategories = computed(() => catalog.value.mainCategories)

  const tagNameMap = computed(() => {
    const map = new Map<string, string>()
    for (const cat of catalog.value.mainCategories) {
      for (const opt of cat.options) {
        map.set(opt.tagCode, opt.tagName)
      }
    }
    return map
  })

  async function fetchCatalog(token: string): Promise<void> {
    try {
      const res = await tagApi.getCatalog(token)
      catalog.value = res.mainCategories?.length ? res : DEFAULT_CATALOG
      loaded.value = true
    } catch {
      catalog.value = DEFAULT_CATALOG
    }
  }

  function getTagName(tagCode: string): string {
    return tagNameMap.value.get(tagCode) ?? tagCode
  }

  function $reset(): void {
    catalog.value = { ...DEFAULT_CATALOG }
    loaded.value = false
  }

  return { catalog, loaded, mainCategories, tagNameMap, fetchCatalog, getTagName, $reset }
})
