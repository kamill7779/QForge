import { describe, it, expect } from 'vitest'
import { difficultyLevel, difficultyLabel, allDifficultyLevels } from '@/lib/difficulty'

describe('difficulty', () => {
  it('returns easy for p >= 70', () => {
    expect(difficultyLevel(70).key).toBe('easy')
    expect(difficultyLevel(100).key).toBe('easy')
    expect(difficultyLevel(85).label).toBe('简单')
  })

  it('returns medium-easy for 50 <= p < 70', () => {
    expect(difficultyLevel(50).key).toBe('medium-easy')
    expect(difficultyLevel(69).key).toBe('medium-easy')
  })

  it('returns medium for 30 <= p < 50', () => {
    expect(difficultyLevel(30).key).toBe('medium')
    expect(difficultyLevel(49).key).toBe('medium')
  })

  it('returns hard for 15 <= p < 30', () => {
    expect(difficultyLevel(15).key).toBe('hard')
    expect(difficultyLevel(29).key).toBe('hard')
  })

  it('returns very-hard for p < 15', () => {
    expect(difficultyLevel(14).key).toBe('very-hard')
    expect(difficultyLevel(0).key).toBe('very-hard')
  })

  it('difficultyLabel returns Chinese label', () => {
    expect(difficultyLabel(80)).toBe('简单')
    expect(difficultyLabel(55)).toBe('中等偏易')
    expect(difficultyLabel(35)).toBe('中等')
    expect(difficultyLabel(20)).toBe('较难')
    expect(difficultyLabel(5)).toBe('极难')
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
