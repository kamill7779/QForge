import { describe, it, expect } from 'vitest'
import { difficultyLevel, difficultyLabel, allDifficultyLevels } from '@/lib/difficulty'

describe('difficulty', () => {
  it('returns easy for p >= 0.70', () => {
    expect(difficultyLevel(0.70).key).toBe('easy')
    expect(difficultyLevel(1.00).key).toBe('easy')
    expect(difficultyLevel(0.85).label).toBe('简单')
  })

  it('returns medium-easy for 0.50 <= p < 0.70', () => {
    expect(difficultyLevel(0.50).key).toBe('medium-easy')
    expect(difficultyLevel(0.69).key).toBe('medium-easy')
  })

  it('returns medium for 0.30 <= p < 0.50', () => {
    expect(difficultyLevel(0.30).key).toBe('medium')
    expect(difficultyLevel(0.49).key).toBe('medium')
  })

  it('returns hard for 0.15 <= p < 0.30', () => {
    expect(difficultyLevel(0.15).key).toBe('hard')
    expect(difficultyLevel(0.29).key).toBe('hard')
  })

  it('returns very-hard for p < 0.15', () => {
    expect(difficultyLevel(0.14).key).toBe('very-hard')
    expect(difficultyLevel(0).key).toBe('very-hard')
  })

  it('difficultyLabel returns Chinese label', () => {
    expect(difficultyLabel(0.80)).toBe('简单')
    expect(difficultyLabel(0.55)).toBe('中等偏易')
    expect(difficultyLabel(0.35)).toBe('中等')
    expect(difficultyLabel(0.20)).toBe('较难')
    expect(difficultyLabel(0.05)).toBe('极难')
  })

  it('allDifficultyLevels returns 5 levels', () => {
    const levels = allDifficultyLevels()
    expect(levels).toHaveLength(5)
    expect(levels[0].key).toBe('easy')
    expect(levels[4].key).toBe('very-hard')
  })

  it('each level has cssClass', () => {
    for (const level of allDifficultyLevels()) {
      expect(level.cssClass).toMatch(/^d-/)
    }
  })
})
