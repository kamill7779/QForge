export interface LoginResponse {
  accessToken: string
  expiresInSeconds: number
  tokenType: string
}

export interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface IngestSession {
  sessionUuid: string
  status: string
  sourceKind: string | null
  subjectCode: string | null
  operatorUser: string | null
  paperNameGuess: string | null
  examYearGuess: number | null
  provinceCodeGuess: string | null
  errorMsg: string | null
  createdAt: string
  updatedAt: string
  sourceFileUuids: string[]
}

export interface DraftQuestion {
  draftQuestionUuid: string
  questionNo: string | null
  questionTypeCode: string | null
  answerMode: string | null
  stemText: string | null
  stemXml: string | null
  normalizedStemText: string | null
  score: number | null
  hasAnswer: boolean | null
  editVersion: number | null
}

export interface DraftSection {
  draftSectionUuid: string
  sectionCode: string | null
  sectionTitle: string | null
  sortOrder: number | null
  questions: DraftQuestion[]
}

export interface DraftPaper {
  draftPaperUuid: string
  paperName: string | null
  paperTypeCode: string | null
  examYear: number | null
  provinceCode: string | null
  totalScore: number | null
  durationMinutes: number | null
  status: string | null
  sections: DraftSection[]
}

export interface UpdateDraftPaperRequest {
  paperName: string | null
  paperTypeCode: string | null
  examYear: number | null
  provinceCode: string | null
  totalScore: number | null
  durationMinutes: number | null
}

export interface UpdateDraftQuestionRequest {
  questionNo: string | null
  questionTypeCode: string | null
  answerMode: string | null
  stemText: string | null
  stemXml: string | null
  score: number | null
  editVersion: number | null
}

export interface GkQuestion {
  questionUuid: string
  paperUuid: string | null
  questionNo: string | null
  questionTypeCode: string | null
  answerMode: string | null
  stemText: string | null
  stemXml: string | null
  normalizedStemText: string | null
  score: number | null
  difficultyScore: number | null
  difficultyLevel: string | null
  reasoningStepCount: number | null
  hasAnswer: boolean | null
}

export interface GkPaper {
  paperUuid: string
  paperName: string | null
  paperTypeCode: string | null
  examYear: number | null
  provinceCode: string | null
  subjectCode: string | null
  status: string | null
  questions: GkQuestion[]
}

export interface MaterializeRequest {
  gkQuestionId?: number | null
  questionUuid?: string | null
  mode?: string | null
}

export interface PhotoQueryRequest {
  imageBase64?: string | null
  storageRef?: string | null
  topK?: number | null
}

export interface PhotoQueryMatchResult {
  questionUuid: string
  stemText: string | null
  similarity: number | null
}

export interface PhotoQueryResponse {
  results: PhotoQueryMatchResult[]
}