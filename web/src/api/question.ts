/**
 * api/question.ts — Question API client (identical to Electron version).
 */

import { apiRequest } from './client'
import type {
  QuestionOverviewResponse,
  QuestionPageResponse,
  CreateQuestionRequest,
  UpdateStemRequest,
  OcrTaskSubmitRequest,
  OcrTaskAcceptedResponse,
  QuestionAssetResponse,
  UpdateTagsRequest,
  UpdateDifficultyRequest,
  UpdateSourceRequest,
  AiTaskResponse,
  ApplyAiRecommendationRequest,
  AddAnswerResponse,
  CreateAnswerRequest,
  UpdateAnswerRequest
} from './types'

export const questionApi = {
  list(token: string) {
    return apiRequest<QuestionOverviewResponse[]>('GET', '/api/questions', undefined, token)
  },
  listPage(token: string, page: number, size: number) {
    return apiRequest<QuestionPageResponse>(
      'GET',
      `/api/questions/page?page=${page}&size=${size}`,
      undefined,
      token
    )
  },
  detail(token: string, uuid: string) {
    return apiRequest<QuestionOverviewResponse>('GET', `/api/questions/${uuid}`, undefined, token)
  },
  create(token: string, req?: CreateQuestionRequest) {
    return apiRequest<QuestionOverviewResponse>('POST', '/api/questions', req ?? {}, token)
  },
  delete(token: string, uuid: string) {
    return apiRequest<void>('DELETE', `/api/questions/${uuid}`, undefined, token)
  },
  batchDelete(token: string, uuids: string[]) {
    return apiRequest<void>('POST', '/api/questions/batch-delete', { questionUuids: uuids }, token)
  },
  updateStem(token: string, uuid: string, req: UpdateStemRequest) {
    return apiRequest<void>('PUT', `/api/questions/${uuid}/stem`, req, token)
  },
  complete(token: string, uuid: string) {
    return apiRequest<void>('POST', `/api/questions/${uuid}/complete`, undefined, token)
  },
  addAnswer(token: string, uuid: string, req: CreateAnswerRequest) {
    return apiRequest<AddAnswerResponse>('POST', `/api/questions/${uuid}/answers`, req, token)
  },
  updateAnswer(token: string, qUuid: string, aUuid: string, req: UpdateAnswerRequest) {
    return apiRequest<void>('PUT', `/api/questions/${qUuid}/answers/${aUuid}`, req, token)
  },
  deleteAnswer(token: string, qUuid: string, aUuid: string) {
    return apiRequest<void>('DELETE', `/api/questions/${qUuid}/answers/${aUuid}`, undefined, token)
  },
  getAssets(token: string, uuid: string) {
    return apiRequest<QuestionAssetResponse[]>('GET', `/api/questions/${uuid}/assets`, undefined, token)
  },
  updateTags(token: string, uuid: string, req: UpdateTagsRequest) {
    return apiRequest<void>('PUT', `/api/questions/${uuid}/tags`, req, token)
  },
  updateDifficulty(token: string, uuid: string, req: UpdateDifficultyRequest) {
    return apiRequest<void>('PUT', `/api/questions/${uuid}/difficulty`, req, token)
  },
  aiAnalysis(token: string, uuid: string) {
    return apiRequest<AiTaskResponse>('POST', `/api/questions/${uuid}/ai-analysis`, undefined, token)
  },
  aiTasks(token: string, uuid: string) {
    return apiRequest<AiTaskResponse[]>('GET', `/api/questions/${uuid}/ai-tasks`, undefined, token)
  },
  applyAi(token: string, uuid: string, taskUuid: string, req: ApplyAiRecommendationRequest) {
    return apiRequest<void>('PUT', `/api/questions/${uuid}/ai-tasks/${taskUuid}/apply`, req, token)
  },
  updateSource(token: string, uuid: string, req: UpdateSourceRequest) {
    return apiRequest<void>('PUT', `/api/questions/${uuid}/source`, req, token)
  },
  listSources(token: string) {
    return apiRequest<string[]>('GET', '/api/questions/sources', undefined, token)
  }
}
