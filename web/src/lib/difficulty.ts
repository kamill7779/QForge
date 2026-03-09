/**
 * difficulty.ts — Unified five-level difficulty classification.
 */

export interface DifficultyLevel {
  key: string
  label: string
  cssClass: string
}

const LEVELS: DifficultyLevel[] = [
  { key: 'easy', label: '简单', cssClass: 'd-easy' },
  { key: 'medium-easy', label: '中等偏易', cssClass: 'd-medium-easy' },
  { key: 'medium', label: '中等', cssClass: 'd-medium' },
  { key: 'hard', label: '较难', cssClass: 'd-hard' },
  { key: 'very-hard', label: '极难', cssClass: 'd-very-hard' }
]

export function difficultyLevel(pValue: number): DifficultyLevel {
  if (pValue >= 0.7) return LEVELS[0]
  if (pValue >= 0.5) return LEVELS[1]
  if (pValue >= 0.3) return LEVELS[2]
  if (pValue >= 0.15) return LEVELS[3]
  return LEVELS[4]
}

export function difficultyLabel(pValue: number): string {
  return difficultyLevel(pValue).label
}

export function allDifficultyLevels(): DifficultyLevel[] {
  return [...LEVELS]
}
