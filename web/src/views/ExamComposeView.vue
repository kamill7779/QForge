<template>
  <div class="compose-view">
    <!-- Left: Question Bank Panel -->
    <aside class="compose-bank">
      <div class="panel-header">
        <h3>题库</h3>
        <div class="bank-search">
          <input v-model="keyword" type="text" placeholder="搜索题目…" @keydown.enter="doSearch" />
        </div>
      </div>
      <div class="bank-list" ref="bankListRef" @scroll="onBankScroll">
        <div v-if="questionStore.loading && !questionStore.questions.length" class="list-hint">
          加载中…
        </div>
        <div
          v-for="(q, i) in questionStore.questions"
          :key="q.questionUuid"
          class="mini-card"
          :class="{ selected: examStore.selectedQuestionUuids.has(q.questionUuid) }"
          @click="addToCurrentSection(q)"
        >
          <div class="mini-card-top">
            <span class="mini-seq">{{ i + 1 }}.</span>
            <LatexPreview :xml="q.stemText" compact class="mini-stem" />
          </div>
          <div class="mini-card-bottom">
            <span v-for="t in q.mainTags.slice(0, 2)" :key="t.tagCode" class="mini-tag">{{ t.tagName }}</span>
            <span v-if="examStore.selectedQuestionUuids.has(q.questionUuid)" class="mini-selected">✓ 已选</span>
          </div>
        </div>
        <div v-if="questionStore.loading && questionStore.questions.length" class="list-hint">
          加载更多…
        </div>
      </div>
    </aside>

    <!-- Right: Exam Editor Panel -->
    <section class="compose-editor">
      <div v-if="exam" class="editor-content">
        <!-- Exam meta -->
        <div class="exam-meta-bar">
          <input v-model="exam.title" class="exam-title-input" placeholder="试卷标题" />
          <input v-model="exam.subtitle" class="exam-subtitle-input" placeholder="副标题（可选）" />
          <div class="exam-meta-row">
            <label>考试时长
              <input v-model.number="exam.duration" type="number" min="1" class="duration-input" /> 分钟
            </label>
            <span class="total-score">总分: {{ exam.totalScore }} 分</span>
            <span class="total-questions">共 {{ totalQuestionCount }} 题</span>
          </div>
        </div>

        <!-- Sections -->
        <div class="sections-area">
          <div v-for="(sec, sIdx) in exam.sections" :key="sec.id" class="section-block">
            <div class="section-header">
              <div class="section-title-row">
                <span class="section-seq">{{ chineseOrdinal(sIdx) }}、</span>
                <template v-if="editingSectionId === sec.id">
                  <input
                    v-model="sec.title"
                    class="section-title-input"
                    placeholder="大题标题"
                    @blur="finishEditSection(sec)"
                    @keydown.enter="finishEditSection(sec)"
                    :ref="(el) => { if (el) sectionInputRef = el as HTMLInputElement }"
                  />
                </template>
                <template v-else>
                  <span class="section-title-text">{{ sec.title || '未命名' }}</span>
                  <button class="section-edit-btn" @click="startEditSection(sec.id)" title="编辑标题">✎</button>
                </template>
              </div>
              <div class="section-actions">
                <label class="default-score-label">默认分值
                  <input
                    type="number"
                    class="default-score-input"
                    :value="sec.defaultScore ?? 5"
                    min="0"
                    @change="onDefaultScoreChange(sec, Number(($event.target as HTMLInputElement).value))"
                  />
                </label>
                <span class="section-stats">{{ sec.questions.length }} 题 · {{ sectionScore(sec) }} 分</span>
                <div class="section-move-btns">
                  <button class="sec-btn sec-move" :disabled="sIdx === 0" @click="examStore.moveSection(exam!.id, sIdx, sIdx - 1)">↑</button>
                  <button class="sec-btn sec-move" :disabled="sIdx === exam!.sections.length - 1" @click="examStore.moveSection(exam!.id, sIdx, sIdx + 1)">↓</button>
                  <button class="sec-btn sec-delete" @click="examStore.removeSection(exam!.id, sec.id)" v-if="exam!.sections.length > 1">
                    删除
                  </button>
                </div>
              </div>
            </div>

            <!-- Questions in section -->
            <div v-if="sec.questions.length" class="section-questions">
              <div
                v-for="(q, qIdx) in sec.questions"
                :key="q.questionUuid"
                class="exam-question-item"
              >
                <div class="eq-left" @click="openDetail(q.questionUuid)">
                  <span class="eq-seq">{{ globalSeq(sIdx, qIdx) }}.</span>
                  <LatexPreview
                    :xml="q.snapshot.stemText"
                    :image-resolver="assetHelper.resolverFor(q.questionUuid)"
                    :render-key="assetRenderKey"
                    class="eq-stem"
                  />
                </div>
                <div class="eq-right">
                  <input
                    type="number"
                    class="score-input"
                    :value="q.score"
                    min="0"
                    @change="(e) => examStore.updateQuestionScore(exam!.id, sec.id, q.questionUuid, Number((e.target as HTMLInputElement).value))"
                  />
                  <span class="score-label">分</span>
                  <button class="eq-move" :disabled="qIdx === 0" @click="examStore.moveQuestion(exam!.id, sec.id, qIdx, qIdx - 1)">↑</button>
                  <button class="eq-move" :disabled="qIdx === sec.questions.length - 1" @click="examStore.moveQuestion(exam!.id, sec.id, qIdx, qIdx + 1)">↓</button>
                  <button class="eq-remove" @click="examStore.removeQuestion(exam!.id, sec.id, q.questionUuid)">×</button>
                </div>
              </div>
            </div>
            <div v-else class="section-empty">
              从左侧题库点击题目添加到此大题
            </div>
          </div>

          <button class="add-section-btn" @click="examStore.addSection(exam!.id)">
            + 添加大题
          </button>
        </div>

        <!-- Bottom actions -->
        <div class="editor-footer">
          <router-link :to="`/preview/${exam.id}`" class="footer-btn preview-btn">
            预览试卷
          </router-link>
          <button class="footer-btn export-btn" @click="doExportWord" :disabled="exporting">
            {{ exporting ? '导出中…' : '📄 导出 Word' }}
          </button>
        </div>
      </div>

      <!-- No exam selected -->
      <div v-else class="no-exam">
        <p>请先选择或创建一份试卷</p>
        <button class="btn-create" @click="createNew">+ 新建试卷</button>
      </div>
    </section>

    <!-- Question Type Manager Modal -->
    <QuestionTypeManager v-if="showTypeManager" @close="showTypeManager = false" />

    <!-- Question Detail Modal -->
    <QuestionDetailModal
      v-if="detailUuid"
      :question-uuid="detailUuid"
      @close="detailUuid = null"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LatexPreview from '@/components/LatexPreview.vue'
import QuestionTypeManager from '@/components/QuestionTypeManager.vue'
import QuestionDetailModal from '@/components/QuestionDetailModal.vue'
import { useQuestionStore } from '@/stores/question'
import { useExamStore } from '@/stores/exam'
import { useQuestionTypeStore } from '@/stores/questionType'
import { useNotificationStore } from '@/stores/notification'
import { useQuestionAssets } from '@/composables/useQuestionAssets'
import type { ExamSection } from '@/stores/exam'
import type { QuestionEntry } from '@/stores/question'

const route = useRoute()
const router = useRouter()
const questionStore = useQuestionStore()
const examStore = useExamStore()
const questionTypeStore = useQuestionTypeStore()
const notif = useNotificationStore()
const assetHelper = useQuestionAssets()

const keyword = ref('')
const bankListRef = ref<HTMLElement>()
const activeSectionId = ref<string | null>(null)
const showTypeManager = ref(false)
const assetRenderKey = ref(0)
const exporting = ref(false)

// Editable section title state
const editingSectionId = ref<string | null>(null)
const sectionInputRef = ref<HTMLInputElement | null>(null)

// Detail modal state
const detailUuid = ref<string | null>(null)

const exam = computed(() => examStore.activeExam)

const totalQuestionCount = computed(() =>
  exam.value?.sections.reduce((s, sec) => s + sec.questions.length, 0) ?? 0
)

// Resolve exam from route param
onMounted(async () => {
  // Ensure exam list is loaded from backend
  if (!examStore.exams.length) {
    await examStore.fetchExams()
  }

  // Ensure question types are loaded
  if (!questionTypeStore.types.length) {
    questionTypeStore.fetchTypes()
  }

  const id = route.params.id as string
  if (id) {
    await examStore.setActiveExam(id)
  } else if (!examStore.activeExam && examStore.exams.length) {
    await examStore.setActiveExam(examStore.exams[0].id)
  }
  if (!questionStore.questions.length) {
    questionStore.fetchQuestions()
  }

  // Load image assets for all questions in current exam
  loadExamAssets()
})

watch(() => route.params.id, (id) => {
  if (id) {
    examStore.setActiveExam(id as string).then(() => loadExamAssets())
  }
})

/** Load image assets for all questions in current exam sections. */
function loadExamAssets() {
  const e = exam.value
  if (!e) return
  const uuids = e.sections.flatMap(s => s.questions.map(q => q.questionUuid))
  if (uuids.length) {
    assetHelper.loadAssetsForMany(uuids).then(() => {
      assetRenderKey.value++
    })
  }
}

async function createNew() {
  const e = await examStore.createExam()
  router.replace(`/compose/${e.id}`)
}

function doSearch() {
  if (keyword.value.trim()) {
    questionStore.searchQuestions(keyword.value.trim())
  } else {
    questionStore.clearSearch()
  }
}

function onBankScroll() {
  if (!bankListRef.value) return
  const { scrollTop, scrollHeight, clientHeight } = bankListRef.value
  if (scrollHeight - scrollTop - clientHeight < 200) {
    questionStore.loadMore()
  }
}

function addToCurrentSection(q: QuestionEntry) {
  if (!exam.value) {
    createNew()
  }
  const e = exam.value!
  // If already in exam, remove
  if (examStore.isQuestionInExam(q.questionUuid)) {
    for (const sec of e.sections) {
      if (sec.questions.some((item) => item.questionUuid === q.questionUuid)) {
        examStore.removeQuestion(e.id, sec.id, q.questionUuid)
        return
      }
    }
    return
  }
  // Add to active section or last section
  const targetSec = activeSectionId.value
    ? e.sections.find((s) => s.id === activeSectionId.value)
    : e.sections[e.sections.length - 1]
  if (targetSec) {
    examStore.addQuestionToSection(e.id, targetSec.id, q)
    // Load assets for this question in background
    assetHelper.loadAssets(q.questionUuid).then(() => {
      assetRenderKey.value++
    })
  }
}

function sectionScore(sec: ExamSection): number {
  return sec.questions.reduce((s, q) => s + q.score, 0)
}

function globalSeq(sIdx: number, qIdx: number): number {
  if (!exam.value) return qIdx + 1
  let seq = 0
  for (let i = 0; i < sIdx; i++) {
    seq += exam.value.sections[i].questions.length
  }
  return seq + qIdx + 1
}

const CHINESE_NUMS = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十',
  '十一', '十二', '十三', '十四', '十五', '十六', '十七', '十八', '十九', '二十']

function chineseOrdinal(idx: number): string {
  return CHINESE_NUMS[idx] ?? String(idx + 1)
}

function startEditSection(sectionId: string) {
  editingSectionId.value = sectionId
  nextTick(() => {
    sectionInputRef.value?.focus()
    sectionInputRef.value?.select()
  })
}

function finishEditSection(sec: ExamSection) {
  editingSectionId.value = null
  if (exam.value) {
    examStore.updateSection(exam.value.id, sec.id, { title: sec.title })
  }
}

function onDefaultScoreChange(sec: ExamSection, value: number): void {
  if (!exam.value) return
  examStore.updateSection(exam.value.id, sec.id, { defaultScore: Math.max(0, value) })
}

function openDetail(questionUuid: string) {
  detailUuid.value = questionUuid
}

async function doExportWord() {
  if (!exam.value) return
  exporting.value = true
  try {
    await examStore.exportWord(exam.value.id, true)
    notif.log('试卷已导出')
  } catch (e: any) {
    notif.log(e?.message ?? '导出失败')
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.compose-view {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* ── Left bank panel ── */
.compose-bank {
  width: 380px;
  min-width: 280px;
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  background: var(--color-bg-sidebar);
}
.panel-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.panel-header h3 {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 8px;
  color: var(--color-text-primary);
}
.bank-search input {
  width: 100%;
}
.bank-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.list-hint {
  text-align: center;
  padding: 16px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.mini-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all var(--transition-fast);
}
.mini-card:hover {
  border-color: var(--color-accent);
  background: var(--color-bg-hover);
}
.mini-card.selected {
  border-left: 3px solid var(--color-accent);
  background: var(--color-accent-muted);
}
.mini-card-top {
  display: flex;
  gap: 6px;
  align-items: flex-start;
}
.mini-seq {
  font-weight: 700;
  font-size: 13px;
  color: var(--color-accent);
  min-width: 24px;
  flex-shrink: 0;
}
.mini-stem {
  flex: 1;
  max-height: 80px;
  overflow: hidden;
  font-size: 12px;
}
.mini-card-bottom {
  display: flex;
  gap: 6px;
  margin-top: 6px;
  align-items: center;
}
.mini-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: var(--radius-pill);
  background: var(--color-info-bg);
  color: var(--color-info);
}
.mini-selected {
  margin-left: auto;
  font-size: 11px;
  color: var(--color-accent);
  font-weight: 600;
}

/* ── Right editor panel ── */
.compose-editor {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
.editor-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 20px 24px;
  gap: 20px;
}

.exam-meta-bar {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  padding: 20px;
}
.exam-title-input {
  font-size: 20px;
  font-weight: 700;
  border: none;
  background: transparent;
  width: 100%;
  color: var(--color-text-primary);
  margin-bottom: 4px;
  padding: 4px 0;
}
.exam-title-input:focus {
  outline: none;
  border-bottom: 2px solid var(--color-accent);
}
.exam-subtitle-input {
  font-size: 14px;
  border: none;
  background: transparent;
  width: 100%;
  color: var(--color-text-secondary);
  margin-bottom: 12px;
  padding: 4px 0;
}
.exam-subtitle-input:focus {
  outline: none;
  border-bottom: 1px solid var(--color-border);
}
.exam-meta-row {
  display: flex;
  align-items: center;
  gap: 24px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.exam-meta-row label {
  display: flex;
  align-items: center;
  gap: 6px;
}
.duration-input {
  width: 60px;
  text-align: center;
  padding: 4px 8px;
}
.total-score {
  font-weight: 700;
  color: var(--color-accent);
}
.total-questions {
  color: var(--color-text-muted);
}

.sections-area {
  flex: 1;
}
.section-block {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  margin-bottom: 16px;
  overflow: hidden;
}
.section-header {
  display: flex;
  flex-direction: column;
  padding: 12px 16px;
  background: var(--color-bg-panel);
  border-bottom: 1px solid var(--color-border-light);
  gap: 8px;
}
.section-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.section-seq {
  font-weight: 700;
  font-size: 15px;
  color: var(--color-accent);
  white-space: nowrap;
  flex-shrink: 0;
}
.section-title-text {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  cursor: default;
}
.section-edit-btn {
  width: 26px;
  height: 26px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  display: grid;
  place-items: center;
  font-size: 14px;
  flex-shrink: 0;
  transition: all var(--transition-fast);
}
.section-edit-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: var(--color-accent-muted);
}
.section-title-input {
  font-size: 15px;
  font-weight: 600;
  border: 1px solid var(--color-accent);
  background: var(--color-bg-card);
  color: var(--color-text-primary);
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  flex: 1;
}
.section-title-input:focus {
  outline: none;
  box-shadow: 0 0 0 2px var(--color-accent-glow);
}
.section-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.default-score-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}
.default-score-input {
  width: 48px;
  text-align: center;
  padding: 3px 4px;
  font-size: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}
.sec-move {
  font-size: 12px;
}
.sec-move:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
.section-move-btns {
  display: flex;
  align-items: center;
  gap: 4px;
}
.sec-btn {
  padding: 4px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 12px;
  cursor: pointer;
}
.sec-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}
.sec-delete:hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
  background: var(--color-danger-bg);
}

.section-questions { padding: 4px 0; }
.exam-question-item {
  display: flex;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid var(--color-border-light);
  gap: 8px;
  transition: background var(--transition-fast);
}
.exam-question-item:hover { background: var(--color-bg-hover); }
.exam-question-item:last-child { border-bottom: none; }
.eq-left {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  flex: 1;
  min-width: 0;
  cursor: pointer;
}
.eq-seq {
  font-weight: 700;
  font-size: 14px;
  color: var(--color-accent);
  min-width: 28px;
  flex-shrink: 0;
}
.eq-stem {
  flex: 1;
  font-size: 13px;
}
.eq-right {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.score-input {
  width: 48px;
  text-align: center;
  padding: 4px;
  font-size: 13px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}
.score-label {
  font-size: 12px;
  color: var(--color-text-muted);
}
.eq-move {
  width: 24px;
  height: 24px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  display: grid;
  place-items: center;
  font-size: 12px;
}
.eq-move:disabled { opacity: 0.3; cursor: not-allowed; }
.eq-move:hover:not(:disabled) {
  border-color: var(--color-accent);
  color: var(--color-accent);
}
.eq-remove {
  width: 24px;
  height: 24px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  display: grid;
  place-items: center;
  font-size: 14px;
}
.eq-remove:hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
  background: var(--color-danger-bg);
}

.section-empty {
  padding: 24px;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 13px;
}

.add-section-btn {
  width: 100%;
  padding: 12px;
  border: 2px dashed var(--color-border);
  border-radius: var(--radius-lg);
  background: transparent;
  color: var(--color-text-muted);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
}
.add-section-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: var(--color-accent-muted);
}

.editor-footer {
  padding: 16px 0;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
.footer-btn {
  padding: 10px 28px;
  border-radius: var(--radius-md);
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  text-decoration: none;
  transition: all var(--transition-fast);
  border: none;
}
.preview-btn {
  background: linear-gradient(135deg, var(--color-accent), #a29bfe);
  color: #fff;
  box-shadow: 0 4px 16px var(--color-accent-glow);
}
.preview-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px var(--color-accent-glow);
  text-decoration: none;
  color: #fff;
}
.export-btn {
  background: var(--color-bg-card);
  border: 1px solid var(--color-accent);
  color: var(--color-accent);
}
.export-btn:hover {
  background: var(--color-accent);
  color: #fff;
}
.export-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.no-exam {
  flex: 1;
  display: grid;
  place-items: center;
  text-align: center;
  color: var(--color-text-muted);
}
.no-exam p { margin-bottom: 16px; font-size: 15px; }
.btn-create {
  padding: 10px 24px;
  border: none;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-accent), #a29bfe);
  color: #fff;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 16px var(--color-accent-glow);
}
</style>
