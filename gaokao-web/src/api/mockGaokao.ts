import type {
  DraftPaper,
  GkPaper,
  IngestSession,
  PageResponse,
  PhotoQueryRequest,
  PhotoQueryResponse,
  UpdateDraftPaperRequest,
  UpdateDraftQuestionRequest
} from './types'

const now = new Date().toISOString()

const mockSessions: IngestSession[] = [
  {
    sessionUuid: 'session-mock-001',
    status: 'SPLIT_READY',
    sourceKind: 'PDF',
    subjectCode: 'MATH',
    operatorUser: 'admin',
    paperNameGuess: '2025 全国新高考 I 卷 数学',
    examYearGuess: 2025,
    provinceCodeGuess: 'NATIONWIDE',
    errorMsg: null,
    createdAt: now,
    updatedAt: now,
    sourceFileUuids: ['file-001']
  }
]

const mockDraftPaper: DraftPaper = {
  draftPaperUuid: 'draft-paper-mock-001',
  paperName: '2025 全国新高考 I 卷 数学',
  paperTypeCode: 'GAOKAO_FORMAL',
  examYear: 2025,
  provinceCode: 'NATIONWIDE',
  totalScore: 150,
  durationMinutes: 120,
  status: 'SPLIT_READY',
  sections: [
    {
      draftSectionUuid: 'draft-section-001',
      sectionCode: 'CHOICE',
      sectionTitle: '选择题',
      sortOrder: 1,
      questions: [
        {
          draftQuestionUuid: 'draft-question-001',
          questionNo: '1',
          questionTypeCode: 'SINGLE_CHOICE',
          answerMode: 'OPTION',
          stemText: '已知集合 $A={x|x>1}$，则下列正确的是？',
          stemXml: '<stem version="1"><p>已知集合 $A={x|x&gt;1}$，则下列正确的是？</p></stem>',
          normalizedStemText: '已知集合A={x|x>1}，则下列正确的是？',
          score: 5,
          hasAnswer: true,
          editVersion: 1
        },
        {
          draftQuestionUuid: 'draft-question-002',
          questionNo: '2',
          questionTypeCode: 'FILL_BLANK',
          answerMode: 'TEXT',
          stemText: '函数 $f(x)=x^2-2x+1$ 的最小值为。',
          stemXml: '<stem version="1"><p>函数 $f(x)=x^2-2x+1$ 的最小值为。</p></stem>',
          normalizedStemText: '函数f(x)=x^2-2x+1的最小值为。',
          score: 5,
          hasAnswer: false,
          editVersion: 2
        }
      ]
    }
  ]
}

const mockCorpusPaper: GkPaper = {
  paperUuid: 'gk-paper-001',
  paperName: '2024 北京卷 数学',
  paperTypeCode: 'GAOKAO_FORMAL',
  examYear: 2024,
  provinceCode: 'BJ',
  subjectCode: 'MATH',
  status: 'PUBLISHED',
  questions: [
    {
      questionUuid: 'gk-question-001',
      paperUuid: 'gk-paper-001',
      questionNo: '7',
      questionTypeCode: 'SOLUTION',
      answerMode: 'TEXT',
      stemText: '设抛物线 $y^2=2px$ 的焦点为 $F$，求直线与抛物线位置关系。',
      stemXml: '<stem version="1"><p>设抛物线 $y^2=2px$ 的焦点为 $F$，求直线与抛物线位置关系。</p></stem>',
      normalizedStemText: '设抛物线y^2=2px的焦点为F，求直线与抛物线位置关系。',
      score: 12,
      difficultyScore: 0.74,
      difficultyLevel: 'HARD',
      reasoningStepCount: 5,
      hasAnswer: true
    }
  ]
}

function delay<T>(payload: T, timeout = 240): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(payload), timeout))
}

export const mockGaokaoApi = {
  createSession: async () => delay(mockSessions[0]),
  listSessions: async () => delay(mockSessions),
  getSession: async (sessionUuid: string) =>
    delay(mockSessions.find((item) => item.sessionUuid === sessionUuid) ?? mockSessions[0]),
  uploadFiles: async () => delay(undefined),
  triggerOcrSplit: async () => delay(undefined),
  getDraftPaper: async () => delay(structuredClone(mockDraftPaper)),
  updateDraftPaper: async (_draftPaperUuid: string, request: UpdateDraftPaperRequest) =>
    delay({ ...mockDraftPaper, ...request }),
  updateDraftQuestion: async (draftQuestionUuid: string, request: UpdateDraftQuestionRequest) => {
    const draft = structuredClone(mockDraftPaper)
    for (const section of draft.sections) {
      const question = section.questions.find((item) => item.draftQuestionUuid === draftQuestionUuid)
      if (question) {
        Object.assign(question, request)
      }
    }
    return delay(draft.sections.flatMap((item) => item.questions).find((item) => item.draftQuestionUuid === draftQuestionUuid)!)
  },
  analyzeQuestion: async () => delay(undefined),
  analyzePaper: async () => delay(undefined),
  confirmQuestion: async () => delay(undefined),
  publishPaper: async () => delay(mockCorpusPaper),
  listCorpusPapers: async () =>
    delay({
      records: [mockCorpusPaper],
      total: 1,
      size: 20,
      current: 1,
      pages: 1
    } satisfies PageResponse<GkPaper>),
  getCorpusPaper: async () => delay(mockCorpusPaper),
  getCorpusQuestion: async () => delay(mockCorpusPaper.questions[0]),
  materialize: async () => delay(undefined),
  photoQuery: async (_request: PhotoQueryRequest): Promise<PhotoQueryResponse> =>
    delay({
      results: [
        { questionUuid: 'gk-question-001', stemText: mockCorpusPaper.questions[0].stemText, similarity: 0.93 },
        { questionUuid: 'gk-question-002', stemText: '已知数列满足递推关系，求通项公式。', similarity: 0.84 }
      ]
    })
}