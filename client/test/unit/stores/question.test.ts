import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useQuestionStore, type QuestionEntry } from '@/stores/question'
import { questionApi } from '@/api/question'

function makeEntry(
  questionUuid: string,
  overrides: Partial<QuestionEntry> = {}
): QuestionEntry {
  return {
    questionUuid,
    status: 'DRAFT',
    stemText: '<stem><p>题干</p></stem>',
    stemDraft: '<stem><p>题干</p></stem>',
    stemConfirmed: true,
    mainTags: [],
    secondaryTags: [],
    secondaryTagsDraft: '',
    difficulty: null,
    source: '未分类',
    answerCount: 1,
    answerDraft: '',
    answersLocal: ['<answer><p>答案</p></answer>'],
    answersServerData: [{ answerUuid: `${questionUuid}-a1`, latexText: '<answer><p>答案</p></answer>' }],
    answerViewIndex: 0,
    lastOcrTaskUuid: '',
    lastOcrStatus: '',
    lastAnswerOcrTaskUuid: '',
    lastAnswerOcrStatus: '',
    stemImageBase64: '',
    inlineImages: {},
    answerImages: {},
    assetsLoaded: false,
    assetVersion: 0,
    createdAt: 1,
    updatedAt: 1,
    ...overrides
  }
}

describe('question store completion flow', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('selects the next pending question after the current one becomes completed', async () => {
    const store = useQuestionStore()
    store.stageFilter = 'PENDING_ANSWER'
    store.entries.set('q-1', makeEntry('q-1', { updatedAt: 20 }))
    store.entries.set('q-2', makeEntry('q-2', { updatedAt: 10 }))
    store.selectedQuestionUuid = 'q-1'

    vi.spyOn(questionApi, 'complete').mockResolvedValue({
      questionUuid: 'q-1',
      status: 'READY'
    })

    await store.completeQuestion('token', 'q-1')

    expect(store.selectedQuestionUuid).toBe('q-2')
  })

  it('clears selection when completion removes the last visible pending question', async () => {
    const store = useQuestionStore()
    store.stageFilter = 'PENDING_ANSWER'
    store.entries.set('q-1', makeEntry('q-1'))
    store.selectedQuestionUuid = 'q-1'

    vi.spyOn(questionApi, 'complete').mockResolvedValue({
      questionUuid: 'q-1',
      status: 'READY'
    })

    await store.completeQuestion('token', 'q-1')

    expect(store.selectedQuestionUuid).toBe('')
  })
})
