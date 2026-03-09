import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { gaokaoApi } from '@/api/gaokao'
import type {
  DraftPaper,
  DraftQuestion,
  GkPaper,
  GkQuestion,
  IngestSession,
  PhotoQueryResponse,
  UpdateDraftPaperRequest,
  UpdateDraftQuestionRequest
} from '@/api/types'

export const useGaokaoStore = defineStore('gaokao', () => {
  const loading = ref(false)
  const sessions = ref<IngestSession[]>([])
  const activeSession = ref<IngestSession | null>(null)
  const draftPaper = ref<DraftPaper | null>(null)
  const draftPaperLoadMessage = ref('')
  const activeDraftQuestionUuid = ref('')
  const corpusPapers = ref<GkPaper[]>([])
  const corpusTotal = ref(0)
  const activeCorpusPaper = ref<GkPaper | null>(null)
  const activeCorpusQuestion = ref<GkQuestion | null>(null)
  const photoQueryResult = ref<PhotoQueryResponse | null>(null)

  const draftQuestions = computed(() => draftPaper.value?.sections.flatMap((section) => section.questions) ?? [])
  const activeDraftQuestion = computed<DraftQuestion | null>(() =>
    draftQuestions.value.find((item) => item.draftQuestionUuid === activeDraftQuestionUuid.value) ?? null
  )

  async function refreshSessions() {
    sessions.value = await gaokaoApi.listSessions()
    if (!activeSession.value && sessions.value.length > 0) {
      activeSession.value = sessions.value[0]
    }
  }

  async function createSession() {
    const session = await gaokaoApi.createSession()
    await refreshSessions()
    activeSession.value = session
    return session
  }

  async function selectSession(sessionUuid: string) {
    activeSession.value = await gaokaoApi.getSession(sessionUuid)
    draftPaper.value = null
    activeDraftQuestionUuid.value = ''
  }

  async function uploadFiles(files: File[]) {
    if (!activeSession.value) throw new Error('请先创建或选择录入会话')
    await gaokaoApi.uploadFiles(activeSession.value.sessionUuid, files)
    activeSession.value = await gaokaoApi.getSession(activeSession.value.sessionUuid)
  }

  async function triggerOcrSplit() {
    if (!activeSession.value) throw new Error('当前没有录入会话')
    await gaokaoApi.triggerOcrSplit(activeSession.value.sessionUuid)
    activeSession.value = await gaokaoApi.getSession(activeSession.value.sessionUuid)
  }

  async function loadDraftPaper() {
    if (!activeSession.value) throw new Error('当前没有录入会话')
    try {
      draftPaper.value = await gaokaoApi.getDraftPaper(activeSession.value.sessionUuid)
      draftPaperLoadMessage.value = ''
      const firstQuestion = draftPaper.value.sections[0]?.questions[0]
      activeDraftQuestionUuid.value = firstQuestion?.draftQuestionUuid ?? ''
    } catch (error) {
      draftPaper.value = null
      draftPaperLoadMessage.value = (error as Error).message
      activeDraftQuestionUuid.value = ''
      throw error
    }
  }

  async function saveDraftPaper(request: UpdateDraftPaperRequest) {
    if (!draftPaper.value) throw new Error('草稿试卷尚未加载')
    draftPaper.value = await gaokaoApi.updateDraftPaper(draftPaper.value.draftPaperUuid, request)
  }

  async function saveDraftQuestion(request: UpdateDraftQuestionRequest) {
    if (!activeDraftQuestion.value) throw new Error('当前没有选中的题目')
    const updated = await gaokaoApi.updateDraftQuestion(activeDraftQuestion.value.draftQuestionUuid, request)
    if (!draftPaper.value) return
    for (const section of draftPaper.value.sections) {
      const index = section.questions.findIndex((item) => item.draftQuestionUuid === updated.draftQuestionUuid)
      if (index >= 0) {
        section.questions[index] = updated
        break
      }
    }
  }

  async function analyzeQuestion() {
    if (!activeDraftQuestion.value) throw new Error('当前没有选中的题目')
    await gaokaoApi.analyzeQuestion(activeDraftQuestion.value.draftQuestionUuid)
  }

  async function analyzePaper() {
    if (!draftPaper.value) throw new Error('草稿试卷尚未加载')
    await gaokaoApi.analyzePaper(draftPaper.value.draftPaperUuid)
  }

  async function confirmQuestion() {
    if (!activeDraftQuestion.value) throw new Error('当前没有选中的题目')
    await gaokaoApi.confirmQuestion(activeDraftQuestion.value.draftQuestionUuid)
  }

  async function publishPaper() {
    if (!draftPaper.value) throw new Error('草稿试卷尚未加载')
    const paper = await gaokaoApi.publishPaper(draftPaper.value.draftPaperUuid)
    activeCorpusPaper.value = paper
    return paper
  }

  async function loadCorpus(page = 1) {
    const result = await gaokaoApi.listCorpusPapers(page, 20)
    corpusPapers.value = result.records
    corpusTotal.value = result.total
    if (result.records.length > 0 && !activeCorpusPaper.value) {
      activeCorpusPaper.value = result.records[0]
    }
  }

  async function selectCorpusPaper(paperUuid: string) {
    activeCorpusPaper.value = await gaokaoApi.getCorpusPaper(paperUuid)
    activeCorpusQuestion.value = activeCorpusPaper.value.questions[0] ?? null
  }

  async function selectCorpusQuestion(questionUuid: string) {
    activeCorpusQuestion.value = await gaokaoApi.getCorpusQuestion(questionUuid)
  }

  async function materializeQuestion(questionUuid: string) {
    await gaokaoApi.materialize({ questionUuid, mode: 'CREATE' })
  }

  async function runPhotoQuery(imageBase64: string, topK: number) {
    photoQueryResult.value = await gaokaoApi.photoQuery({ imageBase64, topK })
  }

  return {
    loading,
    sessions,
    activeSession,
    draftPaper,
    draftPaperLoadMessage,
    draftQuestions,
    activeDraftQuestionUuid,
    activeDraftQuestion,
    corpusPapers,
    corpusTotal,
    activeCorpusPaper,
    activeCorpusQuestion,
    photoQueryResult,
    refreshSessions,
    createSession,
    selectSession,
    uploadFiles,
    triggerOcrSplit,
    loadDraftPaper,
    saveDraftPaper,
    saveDraftQuestion,
    analyzeQuestion,
    analyzePaper,
    confirmQuestion,
    publishPaper,
    loadCorpus,
    selectCorpusPaper,
    selectCorpusQuestion,
    materializeQuestion,
    runPhotoQuery
  }
})