/**
 * questionType.ts — Question type enum and label mapping.
 *
 * Static fallback for when the API-driven store hasn't loaded yet.
 * Prefer using `useQuestionTypeStore()` for dynamic, customizable types.
 */

export const QUESTION_TYPES: Record<string, string> = {
  SINGLE_CHOICE: '单项选择题',
  MULTI_CHOICE: '多项选择题',
  MULTIPLE_CHOICE: '多项选择题',
  TRUE_FALSE: '判断题',
  FILL_BLANK: '填空题',
  SHORT_ANSWER: '简答题',
  ESSAY: '论述题',
  CALCULATION: '计算题',
  PROOF: '证明题',
  COMPREHENSIVE: '综合题',
  OTHER: '其他'
}

export function questionTypeLabel(type: string): string {
  return QUESTION_TYPES[type] || type
}

export function questionTypeKeys(): string[] {
  return Object.keys(QUESTION_TYPES)
}

export function questionTypeEntries(): [string, string][] {
  return Object.entries(QUESTION_TYPES)
}
