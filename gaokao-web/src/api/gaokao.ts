import { apiRequest, apiUpload } from './client'
import { mockGaokaoApi } from './mockGaokao'
import type {
  DraftPaper,
  DraftQuestionAsset,
  DraftQuestion,
  GkPaper,
  GkQuestion,
  IngestSession,
  MaterializeRequest,
  PageResponse,
  PhotoQueryRequest,
  PhotoQueryResponse,
  UpdateDraftPaperRequest,
  UpdateDraftQuestionRequest
} from './types'

const useMock = import.meta.env.VITE_GAOKAO_MOCK === 'true'

function normalizeIngestSession(session: IngestSession): IngestSession {
  return {
    ...session,
    sourceFileUuids: Array.isArray(session.sourceFileUuids) ? session.sourceFileUuids : []
  }
}

function normalizeDraftQuestion(question: DraftQuestion): DraftQuestion {
  return {
    ...question,
    score: question.score ?? null,
    hasAnswer: question.hasAnswer ?? null,
    editVersion: question.editVersion ?? null
  }
}

function normalizeDraftPaper(paper: DraftPaper): DraftPaper {
  return {
    ...paper,
    sections: Array.isArray(paper.sections)
      ? paper.sections.map((section) => ({
          ...section,
          questions: Array.isArray(section.questions) ? section.questions.map(normalizeDraftQuestion) : []
        }))
      : []
  }
}

function normalizeGkQuestion(question: GkQuestion): GkQuestion {
  return {
    ...question,
    score: question.score ?? null,
    difficultyScore: question.difficultyScore ?? null,
    reasoningStepCount: question.reasoningStepCount ?? null,
    hasAnswer: question.hasAnswer ?? null
  }
}

function normalizeGkPaper(paper: GkPaper): GkPaper {
  return {
    ...paper,
    questions: Array.isArray(paper.questions) ? paper.questions.map(normalizeGkQuestion) : []
  }
}

export const gaokaoApi = {
  createSession() {
    if (useMock) return mockGaokaoApi.createSession()
    return apiRequest<IngestSession>('POST', '/api/gaokao/ingest-sessions').then(normalizeIngestSession)
  },
  listSessions() {
    if (useMock) return mockGaokaoApi.listSessions()
    return apiRequest<IngestSession[]>('GET', '/api/gaokao/ingest-sessions').then((sessions) =>
      Array.isArray(sessions) ? sessions.map(normalizeIngestSession) : []
    )
  },
  getSession(sessionUuid: string) {
    if (useMock) return mockGaokaoApi.getSession(sessionUuid)
    return apiRequest<IngestSession>('GET', `/api/gaokao/ingest-sessions/${sessionUuid}`).then(normalizeIngestSession)
  },
  uploadFiles(sessionUuid: string, files: File[]) {
    if (useMock) return mockGaokaoApi.uploadFiles()
    return apiUpload<void>(`/api/gaokao/ingest-sessions/${sessionUuid}/files`, files)
  },
  triggerOcrSplit(sessionUuid: string) {
    if (useMock) return mockGaokaoApi.triggerOcrSplit()
    return apiRequest<void>('POST', `/api/gaokao/ingest-sessions/${sessionUuid}/ocr-split`)
  },
  getDraftPaper(sessionUuid: string) {
    if (useMock) return mockGaokaoApi.getDraftPaper()
    return apiRequest<DraftPaper>('GET', `/api/gaokao/ingest-sessions/${sessionUuid}/draft-paper`).then(normalizeDraftPaper)
  },
  updateDraftPaper(draftPaperUuid: string, request: UpdateDraftPaperRequest) {
    if (useMock) return mockGaokaoApi.updateDraftPaper(draftPaperUuid, request)
    return apiRequest<DraftPaper>('PUT', `/api/gaokao/draft-papers/${draftPaperUuid}`, request).then(normalizeDraftPaper)
  },
  updateDraftQuestion(draftQuestionUuid: string, request: UpdateDraftQuestionRequest) {
    if (useMock) return mockGaokaoApi.updateDraftQuestion(draftQuestionUuid, request)
    return apiRequest<DraftQuestion>('PUT', `/api/gaokao/draft-questions/${draftQuestionUuid}`, request).then(normalizeDraftQuestion)
  },
  getDraftQuestionAssets(draftQuestionUuid: string) {
    if (useMock) return mockGaokaoApi.getDraftQuestionAssets(draftQuestionUuid)
    return apiRequest<DraftQuestionAsset[]>('GET', `/api/gaokao/draft-questions/${draftQuestionUuid}/assets`)
  },
  analyzeQuestion(draftQuestionUuid: string) {
    if (useMock) return mockGaokaoApi.analyzeQuestion()
    return apiRequest<void>('POST', `/api/gaokao/draft-questions/${draftQuestionUuid}/analyze`)
  },
  analyzePaper(draftPaperUuid: string) {
    if (useMock) return mockGaokaoApi.analyzePaper()
    return apiRequest<void>('POST', `/api/gaokao/draft-papers/${draftPaperUuid}/analyze`)
  },
  confirmQuestion(draftQuestionUuid: string) {
    if (useMock) return mockGaokaoApi.confirmQuestion()
    return apiRequest<void>('POST', `/api/gaokao/draft-questions/${draftQuestionUuid}/confirm`)
  },
  publishPaper(draftPaperUuid: string) {
    if (useMock) return mockGaokaoApi.publishPaper()
    return apiRequest<GkPaper>('POST', `/api/gaokao/draft-papers/${draftPaperUuid}/publish`).then(normalizeGkPaper)
  },
  listCorpusPapers(page = 1, size = 20, examYear?: number | null, provinceCode?: string | null) {
    if (useMock) return mockGaokaoApi.listCorpusPapers()
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (examYear) params.set('examYear', String(examYear))
    if (provinceCode) params.set('provinceCode', provinceCode)
    return apiRequest<PageResponse<GkPaper>>('GET', `/api/gaokao/corpus/papers?${params.toString()}`).then((pageData) => ({
      ...pageData,
      records: Array.isArray(pageData.records) ? pageData.records.map(normalizeGkPaper) : []
    }))
  },
  getCorpusPaper(paperUuid: string) {
    if (useMock) return mockGaokaoApi.getCorpusPaper()
    return apiRequest<GkPaper>('GET', `/api/gaokao/corpus/papers/${paperUuid}`).then(normalizeGkPaper)
  },
  getCorpusQuestion(questionUuid: string) {
    if (useMock) return mockGaokaoApi.getCorpusQuestion()
    return apiRequest<GkQuestion>('GET', `/api/gaokao/corpus/questions/${questionUuid}`).then(normalizeGkQuestion)
  },
  materialize(request: MaterializeRequest) {
    if (useMock) return mockGaokaoApi.materialize()
    return apiRequest<void>('POST', '/api/gaokao/materialize', request)
  },
  photoQuery(request: PhotoQueryRequest) {
    if (useMock) return mockGaokaoApi.photoQuery(request)
    return apiRequest<PhotoQueryResponse>('POST', '/api/gaokao/photo-query', request)
  }
}