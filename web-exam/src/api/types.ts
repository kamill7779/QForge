/**
 * types.ts — All backend API request/response TypeScript types.
 * Copied from frontend-vue with no Electron dependencies.
 */

// ──────────────────── Enums ────────────────────

export type QuestionStatus = 'DRAFT' | 'READY'
export type OcrBizType = 'QUESTION_STEM' | 'ANSWER_CONTENT'
export type OcrTaskStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED'
export type AiTaskStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'APPLIED'
export type ExamParseTaskStatus =
  | 'PENDING'
  | 'OCR_PROCESSING'
  | 'SPLITTING'
  | 'GENERATING'
  | 'SUCCESS'
  | 'PARTIAL_FAILED'
  | 'FAILED'
export type ExamParseConfirmStatus = 'PENDING' | 'CONFIRMED' | 'SKIPPED'

// ──────────────────── Auth ────────────────────

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  expiresInSeconds: number
  tokenType: string
}

// ──────────────────── Inline Image ────────────────────

export interface InlineImageEntry {
  imageData: string
  mimeType?: string
}

// ──────────────────── Question Requests ────────────────────

export interface CreateQuestionRequest {
  stemText?: string | null
}

export interface UpdateStemRequest {
  stemXml: string
  inlineImages?: Record<string, InlineImageEntry> | null
}

export interface CreateAnswerRequest {
  latexText: string
  inlineImages?: Record<string, InlineImageEntry> | null
}

export interface UpdateAnswerRequest {
  latexText: string
  inlineImages?: Record<string, InlineImageEntry> | null
}

export interface OcrTaskSubmitRequest {
  bizType: OcrBizType
  imageBase64: string
}

export interface UpdateTagsRequest {
  tags: string[]
}

export interface UpdateDifficultyRequest {
  difficulty: number
}

export interface ApplyAiRecommendationRequest {
  tags?: string[] | null
  difficulty?: number | null
}

// ──────────────────── Question Responses ────────────────────

export interface QuestionStatusResponse {
  questionUuid: string
  status: QuestionStatus
}

export interface QuestionMainTagResponse {
  categoryCode: string
  categoryName: string
  tagCode: string
  tagName: string
}

export interface AnswerOverviewResponse {
  answerUuid: string
  answerType: string
  latexText: string
  sortOrder: number
  official: boolean
}

export interface QuestionOverviewResponse {
  questionUuid: string
  status: QuestionStatus
  stemText: string | null
  mainTags: QuestionMainTagResponse[]
  secondaryTags: string[]
  difficulty: number | null
  answerCount: number
  answers: AnswerOverviewResponse[]
  createdAt: string
  updatedAt: string
}

export interface QuestionAssetResponse {
  assetUuid: string
  refKey: string
  imageData: string
  mimeType: string
}

export interface AddAnswerResponse {
  questionUuid: string
  status: QuestionStatus
  answerUuid: string
}

export interface OcrTaskAcceptedResponse {
  taskUuid: string
  status: string
}

export interface AiTaskAcceptedResponse {
  taskUuid: string
}

export interface AiTaskResponse {
  taskUuid: string
  questionUuid: string
  status: AiTaskStatus
  suggestedTags: string[] | null
  suggestedDifficulty: number | null
  reasoning: string | null
  errorMessage: string | null
  appliedAt: string | null
  createdAt: string
}

// ──────────────────── Tags ────────────────────

export interface TagOptionResponse {
  tagCode: string
  tagName: string
}

export interface MainTagCategoryResponse {
  categoryCode: string
  categoryName: string
  options: TagOptionResponse[]
}

export interface TagCatalogResponse {
  mainCategories: MainTagCategoryResponse[]
  secondaryCategoryCode: string
  secondaryCategoryName: string
}

// ──────────────────── WebSocket ────────────────────

export interface WsMessage {
  event: string
  payload: Record<string, unknown>
}

// ──────────────────── Exam Paper ────────────────────

export type ExamPaperStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface ExamPaperOverviewResponse {
  paperUuid: string
  title: string
  subtitle: string | null
  status: ExamPaperStatus
  durationMinutes: number | null
  totalScore: number
  sectionCount: number
  questionCount: number
  createdAt: string
  updatedAt: string
}

export interface ExamQuestionResponse {
  questionUuid: string
  stemText: string | null
  score: number
  sortOrder: number
  note: string | null
}

export interface ExamSectionResponse {
  sectionUuid: string
  title: string
  description: string | null
  questionTypeCode: string | null
  defaultScore: number
  sortOrder: number
  questions: ExamQuestionResponse[]
}

export interface ExamPaperDetailResponse {
  paperUuid: string
  title: string
  subtitle: string | null
  description: string | null
  durationMinutes: number | null
  totalScore: number
  status: ExamPaperStatus
  sections: ExamSectionResponse[]
  createdAt: string
  updatedAt: string
}

export interface CreateExamPaperRequest {
  title?: string
  subtitle?: string
  description?: string
  durationMinutes?: number
}

export interface UpdateExamPaperRequest {
  title?: string
  subtitle?: string
  description?: string
  durationMinutes?: number
  status?: ExamPaperStatus
}

export interface SaveExamContentSectionPayload {
  sectionUuid?: string
  title: string
  description?: string
  questionTypeCode?: string
  defaultScore?: number
  questions: SaveExamContentQuestionPayload[]
}

export interface SaveExamContentQuestionPayload {
  questionUuid: string
  score?: number
  note?: string
}

export interface SaveExamContentRequest {
  sections: SaveExamContentSectionPayload[]
}

export interface ExamPaperExportRequest {
  includeAnswers: boolean
  answerPosition?: string
}

// ──────────────────── Question Types ────────────────────

export interface QuestionTypeResponse {
  id: number
  typeCode: string
  typeLabel: string
  ownerUser: string
  xmlHint: string | null
  sortOrder: number
  enabled: boolean
  system: boolean
}

export interface SaveQuestionTypeRequest {
  typeCode: string
  typeLabel: string
  xmlHint?: string
  sortOrder?: number
}

