/**
 * difficulty.ts — Unified five-level difficulty classification.
 *
 * Resolves the inconsistency between renderer.js (5-level) and exam-parse-runtime (3-level)
 * by establishing a single source of truth for P-value → difficulty mapping.
 *
 * P-value is on a 0–1 scale (decimal). Lower value = harder.
 * Thresholds:
 *   ≥0.70: easy, ≥0.50: medium-easy, ≥0.30: medium, ≥0.15: hard, <0.15: very-hard
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

/** Map a P-value (0–1) to its difficulty level. */
export function difficultyLevel(pValue: number): DifficultyLevel {
  if (pValue >= 0.7) return LEVELS[0]
  if (pValue >= 0.5) return LEVELS[1]
  if (pValue >= 0.3) return LEVELS[2]
  if (pValue >= 0.15) return LEVELS[3]
  return LEVELS[4]
}

/** Shorthand: get the Chinese label for a P-value. */
export function difficultyLabel(pValue: number): string {
  return difficultyLevel(pValue).label
}

/** Get all difficulty levels (useful for rendering legends/selectors). */
export function allDifficultyLevels(): DifficultyLevel[] {
  return [...LEVELS]
}
