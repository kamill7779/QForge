/**
 * question.ts — Question CRUD + OCR + AI API endpoints.
 *
 * All functions take `token` explicitly to avoid circular store imports.
 */

import { apiRequest } from './client'
import type {
  QuestionOverviewResponse,
  QuestionStatusResponse,
  CreateQuestionRequest,
  UpdateStemRequest,
  CreateAnswerRequest,
  UpdateAnswerRequest,
  AddAnswerResponse,
  OcrTaskSubmitRequest,
  OcrTaskAcceptedResponse,
  QuestionAssetResponse,
  UpdateTagsRequest,
  UpdateDifficultyRequest,
  UpdateSourceRequest,
  AiTaskAcceptedResponse,
  AiTaskResponse,
  ApplyAiRecommendationRequest
} from './types'

export const questionApi = {
  /** List all questions for the current user. */
  list: (token: string) =>
    apiRequest<QuestionOverviewResponse[]>('/api/questions', 'GET', token),

  /** Create a new question (optionally with initial stem text). */
  create: (token: string, body?: CreateQuestionRequest) =>
    apiRequest<QuestionStatusResponse>('/api/questions', 'POST', token, body),

  /** Delete a question. */
  delete: (token: string, uuid: string) =>
    apiRequest<void>(`/api/questions/${uuid}`, 'DELETE', token),

  /** Batch delete questions. */
  batchDelete: (token: string, uuids: string[]) =>
    apiRequest<{ deleted: number }>('/api/questions/batch-delete', 'POST', token, {
      questionUuids: uuids
    }),

  /** Update stem XML + optional inline images. */
  updateStem: (token: string, uuid: string, body: UpdateStemRequest) =>
    apiRequest<QuestionStatusResponse>(`/api/questions/${uuid}/stem`, 'PUT', token, body),

  /** Mark question as complete (DRAFT → READY). */
  complete: (token: string, uuid: string) =>
    apiRequest<QuestionStatusResponse>(`/api/questions/${uuid}/complete`, 'POST', token),

  /** Add an answer. */
  addAnswer: (token: string, uuid: string, body: CreateAnswerRequest) =>
    apiRequest<AddAnswerResponse>(`/api/questions/${uuid}/answers`, 'POST', token, body),

  /** Update an existing answer. */
  updateAnswer: (
    token: string,
    uuid: string,
    answerUuid: string,
    body: UpdateAnswerRequest
  ) =>
    apiRequest<QuestionStatusResponse>(
      `/api/questions/${uuid}/answers/${answerUuid}`,
      'PUT',
      token,
      body
    ),

  /** Delete an answer. */
  deleteAnswer: (token: string, uuid: string, answerUuid: string) =>
    apiRequest<void>(`/api/questions/${uuid}/answers/${answerUuid}`, 'DELETE', token),

  /** Submit an OCR task (stem or answer). */
  submitOcr: (token: string, uuid: string, body: OcrTaskSubmitRequest) =>
    apiRequest<OcrTaskAcceptedResponse>(
      `/api/questions/${uuid}/ocr-tasks`,
      'POST',
      token,
      body
    ),

  /** Get question assets (images). */
  getAssets: (token: string, uuid: string) =>
    apiRequest<QuestionAssetResponse[]>(`/api/questions/${uuid}/assets`, 'GET', token),

  /** Update tags. */
  updateTags: (token: string, uuid: string, body: UpdateTagsRequest) =>
    apiRequest<QuestionStatusResponse>(`/api/questions/${uuid}/tags`, 'PUT', token, body),

  /** Update difficulty P-value. */
  updateDifficulty: (token: string, uuid: string, body: UpdateDifficultyRequest) =>
    apiRequest<QuestionStatusResponse>(
      `/api/questions/${uuid}/difficulty`,
      'PUT',
      token,
      body
    ),

  /** Update source. */
  updateSource: (token: string, uuid: string, body: UpdateSourceRequest) =>
    apiRequest<QuestionStatusResponse>(
      `/api/questions/${uuid}/source`,
      'PUT',
      token,
      body
    ),

  /** List distinct sources for current user. */
  listSources: (token: string) =>
    apiRequest<string[]>('/api/questions/sources', 'GET', token),

  /** Trigger AI analysis. */
  aiAnalysis: (token: string, uuid: string) =>
    apiRequest<AiTaskAcceptedResponse>(
      `/api/questions/${uuid}/ai-analysis`,
      'POST',
      token
    ),

  /** List AI analysis tasks for a question. */
  aiTasks: (token: string, uuid: string) =>
    apiRequest<AiTaskResponse[]>(`/api/questions/${uuid}/ai-tasks`, 'GET', token),

  /** Apply AI recommendation. */
  applyAi: (
    token: string,
    uuid: string,
    taskUuid: string,
    body: ApplyAiRecommendationRequest
  ) =>
    apiRequest<QuestionStatusResponse>(
      `/api/questions/${uuid}/ai-tasks/${taskUuid}/apply`,
      'PUT',
      token,
      body
    )
}
