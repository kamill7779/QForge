/**
 * types.ts — All backend API request/response TypeScript types.
 *
 * Generated from backend Java DTOs (question-service, auth-service, etc.)
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

// ──────────────────── Exam Parse ────────────────────

export interface ExamParseTask {
  id: number | null
  taskUuid: string
  ownerUser: string
  status: ExamParseTaskStatus
  progress: number | null
  fileCount: number | null
  totalPages: number | null
  questionCount: number | null
  hasAnswerHint: boolean | null
  errorMsg: string | null
  createdAt: string
  updatedAt: string
}

export interface ExamParseQuestion {
  id: number | null
  taskUuid: string
  seqNo: number
  questionType: string | null
  rawStemText: string | null
  stemXml: string | null
  rawAnswerText: string | null
  answerXml: string | null
  stemImagesJson: string | null
  answerImagesJson: string | null
  sourcePages: string | null
  parseError: boolean | null
  questionUuid: string | null
  mainTagsJson: string | null
  secondaryTagsJson: string | null
  difficulty: number | null
  confirmStatus: ExamParseConfirmStatus
  errorMsg: string | null
  createdAt: string
}

export interface ExamParseTaskCreatedResponse {
  taskUuid: string
  status: ExamParseTaskStatus
  fileCount: number
  message: string
}

export interface ExamParseTaskDetailResponse {
  task: ExamParseTask
  questions: ExamParseQuestion[]
}

export interface ExamParseConfirmResponse {
  taskUuid: string
  confirmedCount: number
  message: string
}

export interface ExamParseSingleConfirmResponse {
  taskUuid: string
  seqNo: number
  questionUuid: string
  message: string
}

export interface ExamParseMessageResponse {
  taskUuid: string
  seqNo: number
  message: string
}

// ──────────────────── WebSocket ────────────────────

export interface WsMessage {
  event: string
  payload: Record<string, unknown>
}
