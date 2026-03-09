import { describe, it, expect } from 'vitest'
import {
  refSeed,
  buildScopedRef,
  parseRefIndex,
  nextFigureRef,
  toScopedRef,
  prepareAnswerSubmitData
} from '@/lib/imageRef'

describe('imageRef — ref utilities', () => {
  it('refSeed normalizes uuid into 8-char lowercase seed', () => {
    expect(refSeed('A92F6C03-3742-48ca-bf50-2de1678b4118')).toBe('a92f6c03')
  })

  it('refSeed handles short ids', () => {
    expect(refSeed('abcd')).toBe('abcd')
  })

  it('buildScopedRef builds answer-prefixed ref', () => {
    expect(buildScopedRef('a92f6c03-3742-48ca-bf50-2de1678b4118', 2)).toBe('aa92f6c03-img-2')
  })

  it('parseRefIndex extracts trailing number', () => {
    expect(parseRefIndex('fig-1')).toBe(1)
    expect(parseRefIndex('aa92f6c03-img-2')).toBe(2)
    expect(parseRefIndex('img-4')).toBe(4)
    expect(parseRefIndex('random')).toBe(0)
  })

  it('nextFigureRef finds max index across all ref styles', () => {
    const next = nextFigureRef(['fig-1', 'aa92f6c03-img-2', 'img-4', 'random'])
    expect(next).toBe('fig-5')
  })

  it('nextFigureRef returns fig-1 for empty list', () => {
    expect(nextFigureRef([])).toBe('fig-1')
  })

  it('toScopedRef passes through already-scoped refs', () => {
    expect(toScopedRef('anything', 'aa92f6c03-img-2')).toBe('aa92f6c03-img-2')
  })

  it('toScopedRef converts fig-N to scoped ref', () => {
    expect(toScopedRef('b2345f67-3742-48ca-bf50-2de1678b4118', 'fig-3')).toBe('ab2345f67-img-3')
  })
})

describe('imageRef — prepareAnswerSubmitData', () => {
  it('converts fig refs for submit and keeps image payload aligned', () => {
    const entry = {
      questionUuid: 'b2345f67-3742-48ca-bf50-2de1678b4118',
      answerImages: { 'fig-1': 'BASE64_MANUAL' },
      inlineImages: {}
    }

    const blocks = [
      { type: 'p', text: 'answer text' },
      { type: 'image', ref: 'fig-1' }
    ]

    const out = prepareAnswerSubmitData({
      entry,
      blocks,
      answerUuid: null,
      resolveAnswerImage: () => ''
    })

    expect(out.blocks[1].ref).toBe('ab2345f67-img-1')
    expect(out.inlineImages['ab2345f67-img-1'].imageData).toBe('BASE64_MANUAL')
  })

  it('keeps existing scoped ref unchanged', () => {
    const entry = {
      questionUuid: 'q-1',
      answerImages: { 'aa92f6c03-img-2': 'BASE64_OCR' },
      inlineImages: {}
    }

    const blocks = [{ type: 'image', ref: 'aa92f6c03-img-2' }]

    const out = prepareAnswerSubmitData({
      entry,
      blocks,
      answerUuid: '99999999-0000-0000-0000-000000000000',
      resolveAnswerImage: () => 'BASE64_OCR'
    })

    expect(out.blocks[0].ref).toBe('aa92f6c03-img-2')
    expect(out.inlineImages['aa92f6c03-img-2'].imageData).toBe('BASE64_OCR')
  })

  it('uses answerUuid as seed source when provided', () => {
    const entry = {
      questionUuid: 'q-1',
      answerImages: { 'fig-1': 'B64' },
      inlineImages: {}
    }

    const out = prepareAnswerSubmitData({
      entry,
      blocks: [{ type: 'image', ref: 'fig-1' }],
      answerUuid: 'deadbeef-0000-0000-0000-000000000000',
      resolveAnswerImage: () => ''
    })

    // seedSource = answerUuid, refSeed("deadbeef-...") = "deadbeef"
    expect(out.blocks[0].ref).toBe('adeadbeef-img-1')
  })

  it('passes through non-image blocks unchanged', () => {
    const entry = { questionUuid: 'q-1' }
    const blocks = [{ type: 'p', text: 'hello' }]

    const out = prepareAnswerSubmitData({
      entry,
      blocks,
      answerUuid: null,
      resolveAnswerImage: () => ''
    })

    expect(out.blocks).toEqual([{ type: 'p', text: 'hello' }])
    expect(Object.keys(out.inlineImages)).toHaveLength(0)
  })
})
