/**
 * api/examPaper.ts — Exam paper API client (CRUD + content save + export).
 */

import { apiRequest } from './client'
import type {
  ExamPaperOverviewResponse,
  ExamPaperDetailResponse,
  CreateExamPaperRequest,
  UpdateExamPaperRequest,
  SaveExamContentRequest,
  ExamPaperExportRequest,
  ExportFileResponse
} from './types'

function parseFilename(contentDisposition: string | null, fallback: string): string {
  if (!contentDisposition) return fallback
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1])
    } catch {
      return utf8Match[1]
    }
  }
  const plainMatch = contentDisposition.match(/filename="?([^"]+)"?/i)
  return plainMatch?.[1] || fallback
}

export const examPaperApi = {
  /** List all exam papers for current user. */
  list(): Promise<ExamPaperOverviewResponse[]> {
    return apiRequest('GET', '/api/exam-papers')
  },

  /** Create a new exam paper. */
  create(req: CreateExamPaperRequest): Promise<ExamPaperDetailResponse> {
    return apiRequest('POST', '/api/exam-papers', req)
  },

  /** Get full exam paper with sections + questions. */
  detail(paperUuid: string): Promise<ExamPaperDetailResponse> {
    return apiRequest('GET', `/api/exam-papers/${paperUuid}`)
  },

  /** Update exam paper metadata. */
  update(paperUuid: string, req: UpdateExamPaperRequest): Promise<ExamPaperDetailResponse> {
    return apiRequest('PUT', `/api/exam-papers/${paperUuid}`, req)
  },

  /** Soft-delete an exam paper. */
  delete(paperUuid: string): Promise<void> {
    return apiRequest('DELETE', `/api/exam-papers/${paperUuid}`)
  },

  /** Atomic save of all sections + questions. */
  saveContent(paperUuid: string, req: SaveExamContentRequest): Promise<ExamPaperDetailResponse> {
    return apiRequest('PUT', `/api/exam-papers/${paperUuid}/content`, req)
  },

  /** Export paper to Word through exam-service -> export-sidecar. */
  async exportWord(paperUuid: string, req: ExamPaperExportRequest): Promise<ExportFileResponse> {
    const { useAuthStore } = await import('@/stores/auth')
    const auth = useAuthStore()
    const headers: Record<string, string> = {
      'Content-Type': 'application/json'
    }
    if (auth.token) headers['Authorization'] = `Bearer ${auth.token}`

    const res = await fetch(`/api/exam-papers/${paperUuid}/export/word`, {
      method: 'POST',
      headers,
      body: JSON.stringify(req)
    })
    if (!res.ok) throw new Error(`Export failed: ${res.status}`)
    return {
      blob: await res.blob(),
      filename: parseFilename(res.headers.get('Content-Disposition'), `exam-paper-${paperUuid}.docx`)
    }
  }
}
