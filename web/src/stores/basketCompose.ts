import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { basketComposeApi } from '@/api/basketCompose'
import type {
  BasketComposeDetailResponse,
  SaveBasketComposeSectionPayload
} from '@/api/types'

export interface BasketComposeQuestion {
  questionUuid: string
  score: number
  note?: string
  stemText: string
  source?: string
  difficulty: number | null
}

export interface BasketComposeSection {
  id: string
  title: string
  description: string
  questionTypeCode?: string
  defaultScore?: number
  questions: BasketComposeQuestion[]
}

export interface BasketCompose {
  id: string
  title: string
  subtitle: string
  description?: string
  duration: number
  sections: BasketComposeSection[]
  createdAt: string
  updatedAt: string
}

function genId(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

export const useBasketComposeStore = defineStore('basketCompose', () => {
  const compose = ref<BasketCompose | null>(null)
  const loading = ref(false)
  const saving = ref(false)
  let metaTimer: ReturnType<typeof setTimeout> | null = null
  let contentTimer: ReturnType<typeof setTimeout> | null = null
  const SAVE_DELAY = 800

  const totalQuestionCount = computed(() =>
    compose.value?.sections.reduce((sum, section) => sum + section.questions.length, 0) ?? 0
  )

  async function fetchCompose(): Promise<void> {
    loading.value = true
    try {
      compose.value = detailToLocal(await basketComposeApi.detail())
    } finally {
      loading.value = false
    }
  }

  function updateMeta(meta: Partial<Pick<BasketCompose, 'title' | 'subtitle' | 'description' | 'duration'>>) {
    if (!compose.value) return
    Object.assign(compose.value, meta)
    debouncedSaveMeta()
  }

  function addSection(title = '新大题') {
    if (!compose.value) return
    compose.value.sections.push({
      id: genId(),
      title,
      description: '',
      questions: []
    })
    debouncedSaveContent()
  }

  function removeSection(sectionId: string) {
    if (!compose.value) return
    const index = compose.value.sections.findIndex((section) => section.id === sectionId)
    if (index < 0 || compose.value.sections.length <= 1) return
    const [removed] = compose.value.sections.splice(index, 1)
    const fallbackIndex = Math.max(0, index - 1)
    compose.value.sections[fallbackIndex].questions.push(...removed.questions)
    debouncedSaveContent()
  }

  function updateSection(
    sectionId: string,
    data: Partial<Pick<BasketComposeSection, 'title' | 'description' | 'questionTypeCode' | 'defaultScore'>>
  ) {
    const section = compose.value?.sections.find((item) => item.id === sectionId)
    if (!section) return
    Object.assign(section, data)
    debouncedSaveContent()
  }

  function moveSection(fromIdx: number, toIdx: number) {
    if (!compose.value) return
    if (toIdx < 0 || toIdx >= compose.value.sections.length) return
    const [section] = compose.value.sections.splice(fromIdx, 1)
    compose.value.sections.splice(toIdx, 0, section)
    debouncedSaveContent()
  }

  function updateQuestionScore(sectionId: string, questionUuid: string, score: number) {
    const question = compose.value?.sections
      .find((section) => section.id === sectionId)
      ?.questions.find((item) => item.questionUuid === questionUuid)
    if (!question) return
    question.score = Math.max(0, score)
    debouncedSaveContent()
  }

  function moveQuestion(sectionId: string, fromIdx: number, toIdx: number) {
    const section = compose.value?.sections.find((item) => item.id === sectionId)
    if (!section || toIdx < 0 || toIdx >= section.questions.length) return
    const [question] = section.questions.splice(fromIdx, 1)
    section.questions.splice(toIdx, 0, question)
    debouncedSaveContent()
  }

  function moveQuestionAcrossSections(fromSectionId: string, questionUuid: string, targetSectionId: string) {
    if (!compose.value || fromSectionId === targetSectionId) return
    const fromSection = compose.value.sections.find((section) => section.id === fromSectionId)
    const targetSection = compose.value.sections.find((section) => section.id === targetSectionId)
    if (!fromSection || !targetSection) return
    const index = fromSection.questions.findIndex((question) => question.questionUuid === questionUuid)
    if (index < 0) return
    const [question] = fromSection.questions.splice(index, 1)
    targetSection.questions.push(question)
    debouncedSaveContent()
  }

  async function confirmCompose(): Promise<string> {
    const detail = await basketComposeApi.confirm()
    compose.value = null
    return detail.paperUuid
  }

  function debouncedSaveMeta() {
    if (metaTimer) clearTimeout(metaTimer)
    metaTimer = setTimeout(async () => {
      if (!compose.value) return
      await basketComposeApi.updateMeta({
        title: compose.value.title,
        subtitle: compose.value.subtitle || undefined,
        description: compose.value.description || undefined,
        durationMinutes: compose.value.duration
      })
    }, SAVE_DELAY)
  }

  function debouncedSaveContent() {
    if (contentTimer) clearTimeout(contentTimer)
    contentTimer = setTimeout(() => void saveContentNow(), SAVE_DELAY)
  }

  async function saveContentNow() {
    if (!compose.value) return
    saving.value = true
    try {
      const sections: SaveBasketComposeSectionPayload[] = compose.value.sections.map((section) => ({
        sectionUuid: section.id,
        title: section.title,
        description: section.description || undefined,
        questionTypeCode: section.questionTypeCode || undefined,
        defaultScore: section.defaultScore,
        questions: section.questions.map((question) => ({
          questionUuid: question.questionUuid,
          score: question.score,
          note: question.note || undefined
        }))
      }))
      compose.value = detailToLocal(await basketComposeApi.saveContent({ sections }))
    } finally {
      saving.value = false
    }
  }

  function detailToLocal(detail: BasketComposeDetailResponse): BasketCompose {
    return {
      id: detail.composeUuid,
      title: detail.title,
      subtitle: detail.subtitle ?? '',
      description: detail.description ?? undefined,
      duration: detail.durationMinutes ?? 120,
      sections: detail.sections.map((section) => ({
        id: section.sectionUuid,
        title: section.title,
        description: section.description ?? '',
        questionTypeCode: section.questionTypeCode ?? undefined,
        defaultScore: section.defaultScore,
        questions: section.questions.map((question) => ({
          questionUuid: question.questionUuid,
          score: question.score,
          note: question.note ?? undefined,
          stemText: question.stemText ?? '',
          source: question.source ?? undefined,
          difficulty: question.difficulty
        }))
      })),
      createdAt: detail.createdAt,
      updatedAt: detail.updatedAt
    }
  }

  function $reset() {
    compose.value = null
    loading.value = false
    saving.value = false
  }

  return {
    compose,
    loading,
    saving,
    totalQuestionCount,
    fetchCompose,
    updateMeta,
    addSection,
    removeSection,
    updateSection,
    moveSection,
    updateQuestionScore,
    moveQuestion,
    moveQuestionAcrossSections,
    confirmCompose,
    $reset
  }
})
