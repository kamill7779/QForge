/**
 * questionType.ts — Question type enum and label mapping.
 */

export const QUESTION_TYPES: Record<string, string> = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  TRUE_FALSE: '判断题',
  FILL_BLANK: '填空题',
  SHORT_ANSWER: '简答题',
  ESSAY: '论述题',
  CALCULATION: '计算题',
  PROOF: '证明题',
  COMPREHENSIVE: '综合题',
  OTHER: '其他'
}

/** Get the Chinese label for a question type key. Falls back to the key itself. */
export function questionTypeLabel(type: string): string {
  return QUESTION_TYPES[type] || type
}

/** Get all valid question type keys. */
export function questionTypeKeys(): string[] {
  return Object.keys(QUESTION_TYPES)
}

/** Get entries as [key, label] pairs (for dropdowns). */
export function questionTypeEntries(): [string, string][] {
  return Object.entries(QUESTION_TYPES)
}
