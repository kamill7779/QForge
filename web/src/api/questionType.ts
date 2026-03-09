/**
 * api/questionType.ts — Question type configuration API.
 */

import { apiRequest } from './client'
import type { QuestionTypeResponse, SaveQuestionTypeRequest } from './types'

export const questionTypeApi = {
  /** List all available question types (system + user custom). */
  list(): Promise<QuestionTypeResponse[]> {
    return apiRequest('GET', '/api/question-types')
  },

  /** Create a custom question type. */
  create(req: SaveQuestionTypeRequest): Promise<QuestionTypeResponse> {
    return apiRequest('POST', '/api/question-types', req)
  },

  /** Update a custom question type. */
  update(id: number, req: SaveQuestionTypeRequest): Promise<QuestionTypeResponse> {
    return apiRequest('PUT', `/api/question-types/${id}`, req)
  },

  /** Delete a custom question type. */
  delete(id: number): Promise<void> {
    return apiRequest('DELETE', `/api/question-types/${id}`)
  }
}
