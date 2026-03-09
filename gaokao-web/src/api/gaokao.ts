import { apiRequest, apiUpload } from './client'
import { mockGaokaoApi } from './mockGaokao'
import type {
  DraftPaper,
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

export const gaokaoApi = {
  createSession() {
    if (useMock) return mockGaokaoApi.createSession()
    return apiRequest<IngestSession>('POST', '/api/gaokao/ingest-sessions')
  },
  listSessions() {
    if (useMock) return mockGaokaoApi.listSessions()
    return apiRequest<IngestSession[]>('GET', '/api/gaokao/ingest-sessions')
  },
  getSession(sessionUuid: string) {
    if (useMock) return mockGaokaoApi.getSession(sessionUuid)
    return apiRequest<IngestSession>('GET', `/api/gaokao/ingest-sessions/${sessionUuid}`)
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
    return apiRequest<DraftPaper>('GET', `/api/gaokao/ingest-sessions/${sessionUuid}/draft-paper`)
  },
  updateDraftPaper(draftPaperUuid: string, request: UpdateDraftPaperRequest) {
    if (useMock) return mockGaokaoApi.updateDraftPaper(draftPaperUuid, request)
    return apiRequest<DraftPaper>('PUT', `/api/gaokao/draft-papers/${draftPaperUuid}`, request)
  },
  updateDraftQuestion(draftQuestionUuid: string, request: UpdateDraftQuestionRequest) {
    if (useMock) return mockGaokaoApi.updateDraftQuestion(draftQuestionUuid, request)
    return apiRequest<DraftQuestion>('PUT', `/api/gaokao/draft-questions/${draftQuestionUuid}`, request)
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
    return apiRequest<GkPaper>('POST', `/api/gaokao/draft-papers/${draftPaperUuid}/publish`)
  },
  listCorpusPapers(page = 1, size = 20, examYear?: number | null, provinceCode?: string | null) {
    if (useMock) return mockGaokaoApi.listCorpusPapers()
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (examYear) params.set('examYear', String(examYear))
    if (provinceCode) params.set('provinceCode', provinceCode)
    return apiRequest<PageResponse<GkPaper>>('GET', `/api/gaokao/corpus/papers?${params.toString()}`)
  },
  getCorpusPaper(paperUuid: string) {
    if (useMock) return mockGaokaoApi.getCorpusPaper()
    return apiRequest<GkPaper>('GET', `/api/gaokao/corpus/papers/${paperUuid}`)
  },
  getCorpusQuestion(questionUuid: string) {
    if (useMock) return mockGaokaoApi.getCorpusQuestion()
    return apiRequest<GkQuestion>('GET', `/api/gaokao/corpus/questions/${questionUuid}`)
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