/**
 * examParse.ts — Exam Parse API endpoints.
 */

import { apiRequest, apiUpload } from './client'
import type {
  ExamParseTask,
  ExamParseTaskCreatedResponse,
  ExamParseTaskDetailResponse,
  ExamParseConfirmResponse,
  ExamParseSingleConfirmResponse,
  ExamParseMessageResponse,
  ExamParseQuestion
} from './types'

export const examParseApi = {
  /** Create a new exam parse task (multipart upload). */
  createTask: (token: string, filePaths: string[], hasAnswerHint: boolean) =>
    apiUpload<ExamParseTaskCreatedResponse>(
      '/api/exam-parse/tasks',
      token,
      filePaths,
      { hasAnswerHint: String(hasAnswerHint) }
    ),

  /** List all tasks. */
  listTasks: (token: string) =>
    apiRequest<ExamParseTask[]>('/api/exam-parse/tasks', 'GET', token),

  /** Get task detail with all questions. */
  getTask: (token: string, taskUuid: string) =>
    apiRequest<ExamParseTaskDetailResponse>(
      `/api/exam-parse/tasks/${taskUuid}`,
      'GET',
      token
    ),

  /** Update a parsed question's fields. */
  updateQuestion: (
    token: string,
    taskUuid: string,
    seqNo: number,
    fields: Record<string, string>
  ) =>
    apiRequest<ExamParseQuestion>(
      `/api/exam-parse/tasks/${taskUuid}/questions/${seqNo}`,
      'PUT',
      token,
      fields
    ),

  /** Confirm all pending questions in a task at once. */
  confirmAll: (token: string, taskUuid: string) =>
    apiRequest<ExamParseConfirmResponse>(
      `/api/exam-parse/tasks/${taskUuid}/confirm`,
      'POST',
      token
    ),

  /** Confirm a single question. */
  confirmQuestion: (token: string, taskUuid: string, seqNo: number) =>
    apiRequest<ExamParseSingleConfirmResponse>(
      `/api/exam-parse/tasks/${taskUuid}/questions/${seqNo}/confirm`,
      'POST',
      token
    ),

  /** Skip a question. */
  skipQuestion: (token: string, taskUuid: string, seqNo: number) =>
    apiRequest<ExamParseMessageResponse>(
      `/api/exam-parse/tasks/${taskUuid}/questions/${seqNo}/skip`,
      'POST',
      token
    ),

  /** Un-skip a question. */
  unskipQuestion: (token: string, taskUuid: string, seqNo: number) =>
    apiRequest<ExamParseMessageResponse>(
      `/api/exam-parse/tasks/${taskUuid}/questions/${seqNo}/unskip`,
      'POST',
      token
    ),

  /** Delete a task. */
  deleteTask: (token: string, taskUuid: string) =>
    apiRequest<void>(`/api/exam-parse/tasks/${taskUuid}`, 'DELETE', token)
}
