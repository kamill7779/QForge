<template>
  <div class="workbench-page">
    <section class="question-rail card">
      <div class="rail-head">
        <div>
          <h1 class="page-title">草稿工作台</h1>
          <p class="page-subtitle">人工修正整卷元信息与单题内容，再进入 AI 预览、确认与发布。</p>
        </div>
        <button class="btn btn-secondary" @click="goBack">返回会话列表</button>
      </div>

      <div class="paper-summary" v-if="gaokao.draftPaper">
        <span class="chip">{{ gaokao.draftPaper?.status || 'DRAFT' }}</span>
        <strong>{{ paperForm.paperName || '未命名试卷' }}</strong>
      </div>

      <div v-if="gaokao.draftPaper" class="question-groups">
        <div v-for="section in gaokao.draftPaper?.sections || []" :key="section.draftSectionUuid" class="section-box">
          <div class="section-title">{{ section.sectionTitle || section.sectionCode || '未命名 section' }}</div>
          <button
            v-for="question in section.questions"
            :key="question.draftQuestionUuid"
            class="question-pill"
            :class="{ active: gaokao.activeDraftQuestionUuid === question.draftQuestionUuid }"
            @click="selectQuestion(question.draftQuestionUuid)"
          >
            第 {{ question.questionNo || '?' }} 题
          </button>
        </div>
      </div>

      <div v-else class="rail-empty">
        <h2>草稿整卷尚未生成</h2>
        <p>
          当前会话已经进入真实后端链路，但 gaokao-corpus-service 还没有完成 OCR 结果到草稿整卷的自动生成闭环。
        </p>
        <p class="error-copy">{{ gaokao.draftPaperLoadMessage || '请先完成上传与 OCR，再在后端补齐草稿生成。' }}</p>
      </div>
    </section>

    <section v-if="gaokao.draftPaper" class="editor-shell">
      <div class="editor-panels">
        <article class="card paper-panel">
          <div class="panel-head">
            <div>
              <h2>整卷元信息</h2>
              <p>这里只更新草稿层，不会触发正式发布。</p>
            </div>
            <div class="panel-actions">
              <button class="btn btn-secondary" @click="runPaperAnalyze">整卷分析</button>
              <button class="btn btn-primary" @click="savePaper">保存整卷</button>
            </div>
          </div>

          <div v-if="paperForm" class="paper-form-grid">
            <label>卷名<input v-model="paperForm.paperName" type="text" /></label>
            <label>卷型<input v-model="paperForm.paperTypeCode" type="text" /></label>
            <label>年份<input v-model.number="paperForm.examYear" type="number" /></label>
            <label>省份<input v-model="paperForm.provinceCode" type="text" /></label>
            <label>总分<input v-model.number="paperForm.totalScore" type="number" /></label>
            <label>时长（分钟）<input v-model.number="paperForm.durationMinutes" type="number" /></label>
          </div>
        </article>

        <article class="card question-panel" v-if="questionForm">
          <div class="panel-head">
            <div>
              <h2>单题修订</h2>
              <p>可编辑题号、题型、答案模式、分值与题干 XML；AI 分析与确认独立触发。</p>
            </div>
            <div class="panel-actions">
              <button class="btn btn-secondary" @click="runQuestionAnalyze">单题分析</button>
              <button class="btn btn-secondary" @click="confirmQuestion">确认分析</button>
              <button class="btn btn-primary" @click="saveQuestion">保存单题</button>
            </div>
          </div>

          <div class="question-form-grid">
            <label>题号<input v-model="questionForm.questionNo" type="text" /></label>
            <label>题型<input v-model="questionForm.questionTypeCode" type="text" /></label>
            <label>答案模式<input v-model="questionForm.answerMode" type="text" /></label>
            <label>分值<input v-model.number="questionForm.score" type="number" step="0.5" /></label>
          </div>

          <label class="full-row">
            题干纯文本
            <textarea v-model="questionForm.stemText"></textarea>
          </label>

          <label class="full-row">
            题干 XML
            <textarea v-model="questionForm.stemXml"></textarea>
          </label>
        </article>

        <article class="card publish-panel">
          <div class="panel-head">
            <div>
              <h2>正式发布</h2>
              <p>发布后题目进入 gk_* 与向量索引层。物化到 question-core-service 仍需在正式语料页单独操作。</p>
            </div>
            <button class="btn btn-warm" @click="publishPaper">发布整卷</button>
          </div>
        </article>
      </div>

      <div class="preview-panel card">
        <div class="preview-head">
          <h2>题干预览</h2>
          <span class="chip">编辑版本 {{ questionForm?.editVersion ?? '-' }}</span>
        </div>
        <LatexPreview
          :xml="questionForm?.stemXml || ''"
          :image-resolver="resolverFor(gaokao.activeDraftQuestionUuid || '')"
          :render-key="assetVersion"
          placeholder="当前题目暂无可渲染 XML"
        />
      </div>
    </section>

    <section v-else class="editor-shell">
      <article class="card pending-card">
        <div class="panel-head">
          <div>
            <h2>当前工作台不可编辑</h2>
            <p>这是按真实后端返回状态展示的结果，不再假定草稿一定已经生成。</p>
          </div>
          <div class="panel-actions">
            <button class="btn btn-secondary" @click="retryLoadDraft">重新加载</button>
            <button class="btn btn-primary" @click="goBack">返回会话列表</button>
          </div>
        </div>

        <div class="pending-grid">
          <div>
            <strong>会话 UUID</strong>
            <p>{{ gaokao.activeSession?.sessionUuid || '-' }}</p>
          </div>
          <div>
            <strong>当前状态</strong>
            <p>{{ gaokao.activeSession?.status || '-' }}</p>
          </div>
          <div>
            <strong>上传文件数</strong>
            <p>{{ gaokao.activeSession?.sourceFileUuids.length || 0 }}</p>
          </div>
          <div>
            <strong>后端返回</strong>
            <p>{{ gaokao.draftPaperLoadMessage || '暂无额外信息' }}</p>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LatexPreview from '@/components/LatexPreview.vue'
import { useQuestionAssets } from '@/composables/useQuestionAssets'
import { toXmlPayload } from '@/lib/stemXml'
import { useGaokaoStore } from '@/stores/gaokao'
import { useNotificationStore } from '@/stores/notification'

const route = useRoute()
const router = useRouter()
const gaokao = useGaokaoStore()
const notif = useNotificationStore()
const { loadAssets, resolverFor, assetVersion } = useQuestionAssets()

const paperForm = reactive({
  paperName: '',
  paperTypeCode: '',
  examYear: null as number | null,
  provinceCode: '',
  totalScore: null as number | null,
  durationMinutes: null as number | null
})

const questionForm = reactive({
  questionNo: '',
  questionTypeCode: '',
  answerMode: '',
  stemText: '',
  stemXml: '',
  score: null as number | null,
  editVersion: null as number | null
})

const sessionUuid = computed(() => String(route.params.id || ''))

watch(
  () => gaokao.draftPaper,
  (paper) => {
    if (!paper) return
    paperForm.paperName = paper.paperName || ''
    paperForm.paperTypeCode = paper.paperTypeCode || ''
    paperForm.examYear = paper.examYear || null
    paperForm.provinceCode = paper.provinceCode || ''
    paperForm.totalScore = paper.totalScore || null
    paperForm.durationMinutes = paper.durationMinutes || null
  },
  { immediate: true }
)

watch(
  () => gaokao.activeDraftQuestion,
  (question) => {
    if (!question) return
    questionForm.questionNo = question.questionNo || ''
    questionForm.questionTypeCode = question.questionTypeCode || ''
    questionForm.answerMode = question.answerMode || ''
    questionForm.stemText = question.stemText || ''
    questionForm.stemXml = question.stemXml || toXmlPayload(question.stemText || '', 'stem')
    questionForm.score = question.score || null
    questionForm.editVersion = question.editVersion || null
  },
  { immediate: true }
)

watch(
  () => gaokao.activeDraftQuestionUuid,
  (draftQuestionUuid) => {
    if (!draftQuestionUuid) return
    loadAssets(draftQuestionUuid).catch(() => undefined)
  },
  { immediate: true }
)

watch(
  () => sessionUuid.value,
  async (uuid) => {
    if (!uuid) return
    try {
      await gaokao.selectSession(uuid)
      await gaokao.loadDraftPaper()
    } catch (error) {
      notif.push('error', `加载草稿失败: ${(error as Error).message}`)
    }
  },
  { immediate: true }
)

function selectQuestion(questionUuid: string) {
  gaokao.activeDraftQuestionUuid = questionUuid
}

function goBack() {
  router.push('/sessions')
}

async function savePaper() {
  try {
    await gaokao.saveDraftPaper({
      paperName: paperForm.paperName,
      paperTypeCode: paperForm.paperTypeCode,
      examYear: paperForm.examYear,
      provinceCode: paperForm.provinceCode,
      totalScore: paperForm.totalScore,
      durationMinutes: paperForm.durationMinutes
    })
    notif.push('success', '草稿整卷已保存')
  } catch (error) {
    notif.push('error', (error as Error).message)
  }
}

async function saveQuestion() {
  try {
    await gaokao.saveDraftQuestion({
      questionNo: questionForm.questionNo,
      questionTypeCode: questionForm.questionTypeCode,
      answerMode: questionForm.answerMode,
      stemText: questionForm.stemText,
      stemXml: questionForm.stemXml || toXmlPayload(questionForm.stemText, 'stem'),
      score: questionForm.score,
      editVersion: questionForm.editVersion
    })
    notif.push('success', '草稿单题已保存')
  } catch (error) {
    notif.push('error', (error as Error).message)
  }
}

async function runQuestionAnalyze() {
  try {
    await gaokao.analyzeQuestion()
    notif.push('info', '单题分析任务已触发')
  } catch (error) {
    notif.push('error', (error as Error).message)
  }
}

async function runPaperAnalyze() {
  try {
    await gaokao.analyzePaper()
    notif.push('info', '整卷分析任务已触发')
  } catch (error) {
    notif.push('error', (error as Error).message)
  }
}

async function confirmQuestion() {
  try {
    await gaokao.confirmQuestion()
    notif.push('success', '单题分析结果已确认')
  } catch (error) {
    notif.push('error', (error as Error).message)
  }
}

async function publishPaper() {
  try {
    const paper = await gaokao.publishPaper()
    notif.push('success', '整卷已发布到正式高考数学语料库')
    router.push('/corpus')
    if (paper.questions[0]?.questionUuid) {
      await gaokao.selectCorpusPaper(paper.paperUuid)
    }
  } catch (error) {
    notif.push('error', (error as Error).message)
  }
}

async function retryLoadDraft() {
  try {
    await gaokao.loadDraftPaper()
    notif.push('success', '草稿整卷已重新加载')
  } catch (error) {
    notif.push('warning', (error as Error).message)
  }
}
</script>

<style scoped>
.workbench-page {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 20px;
  height: 100%;
}

.question-rail {
  padding: 20px;
  overflow: auto;
}

.rail-head {
  display: grid;
  gap: 12px;
}

.paper-summary {
  display: grid;
  gap: 10px;
  margin-top: 18px;
  padding: 14px;
  border-radius: 18px;
  background: var(--color-surface-soft);
}

.question-groups {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.rail-empty {
  display: grid;
  gap: 12px;
  margin-top: 18px;
  padding: 16px;
  border-radius: 18px;
  background: var(--color-surface-soft);
}

.rail-empty h2 {
  margin: 0;
}

.rail-empty p {
  margin: 0;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.error-copy {
  color: var(--color-danger);
}

.section-box {
  display: grid;
  gap: 10px;
  padding: 14px;
  border-radius: 18px;
  background: var(--color-surface-soft);
}

.section-title {
  font-weight: 700;
}

.question-pill {
  text-align: left;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: #fffefa;
}

.question-pill.active {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}

.editor-shell {
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(320px, 0.9fr);
  gap: 20px;
}

.editor-panels {
  min-width: 0;
  display: grid;
  gap: 18px;
  overflow: auto;
}

.paper-panel,
.question-panel,
.publish-panel,
.preview-panel,
.pending-card {
  padding: 20px;
}

.pending-card {
  align-self: start;
}

.panel-head,
.preview-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.panel-head h2,
.preview-head h2 {
  margin: 0;
}

.panel-head p {
  margin: 6px 0 0;
  color: var(--color-text-secondary);
  line-height: 1.7;
}

.panel-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.paper-form-grid,
.question-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.pending-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 18px;
}

.pending-grid p {
  margin: 8px 0 0;
  color: var(--color-text-secondary);
  line-height: 1.7;
}

label {
  display: grid;
  gap: 8px;
  color: var(--color-text-secondary);
}

.full-row {
  margin-top: 14px;
}

.preview-panel {
  overflow: auto;
}

@media (max-width: 1280px) {
  .workbench-page,
  .editor-shell {
    grid-template-columns: 1fr;
  }

  .paper-form-grid,
  .question-form-grid,
  .pending-grid {
    grid-template-columns: 1fr;
  }
}
</style>