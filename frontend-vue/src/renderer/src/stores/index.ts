/**
 * stores/index.ts — Barrel export for all Pinia stores.
 */

export { useAuthStore } from './auth'
export { useQuestionStore, computeStage } from './question'
export type { QuestionEntry, QuestionStage, OcrTaskEntry, AiState } from './question'
export { useExamParseStore } from './examParse'
export type { FocusStage, FocusData, EpLogEntry } from './examParse'
export { useTagStore } from './tag'
export { useNotificationStore } from './notification'
export type { LogEntry } from './notification'
export { useWebSocketStore } from './websocket'
