<template>
  <div class="compose-view">
    <section class="compose-editor">
      <div v-if="exam" class="editor-content">
        <div class="exam-meta-bar">
          <input
            :value="exam.title"
            class="exam-title-input"
            placeholder="试卷标题"
            @input="onExamMetaInput('title', ($event.target as HTMLInputElement).value)"
          />
          <input
            :value="exam.subtitle"
            class="exam-subtitle-input"
            placeholder="副标题（可选）"
            @input="onExamMetaInput('subtitle', ($event.target as HTMLInputElement).value)"
          />
          <div class="exam-meta-row">
            <label>考试时长
              <input
                :value="exam.duration"
                type="number"
                min="1"
                class="duration-input"
                @input="onExamDurationInput(($event.target as HTMLInputElement).value)"
              /> 分钟
            </label>
            <span class="total-score">总分: {{ exam.totalScore }} 分</span>
            <span class="total-questions">共 {{ totalQuestionCount }} 题</span>
          </div>
        </div>

        <div class="sections-area">
          <div v-for="(sec, sIdx) in exam.sections" :key="sec.id" class="section-block">
            <div class="section-header">
              <div class="section-title-row">
                <span class="section-seq">{{ chineseOrdinal(sIdx) }}、</span>
                <input
                  :value="sec.title"
                  class="section-title-input"
                  placeholder="大题标题"
                  @input="examStore.updateSection(exam.id, sec.id, { title: ($event.target as HTMLInputElement).value })"
                />
              </div>
              <div class="section-actions">
                <label class="default-score-label">默认分值
                  <input
                    type="number"
                    class="default-score-input"
                    :value="sec.defaultScore ?? 5"
                    min="0"
                    @change="examStore.updateSection(exam.id, sec.id, { defaultScore: Math.max(0, Number(($event.target as HTMLInputElement).value)) })"
                  />
                </label>
                <span class="section-stats">{{ sec.questions.length }} 题 · {{ sectionScore(sec) }} 分</span>
                <div class="section-move-btns">
                  <button class="sec-btn" :disabled="sIdx === 0" @click="examStore.moveSection(exam.id, sIdx, sIdx - 1)">↑</button>
                  <button class="sec-btn" :disabled="sIdx === exam.sections.length - 1" @click="examStore.moveSection(exam.id, sIdx, sIdx + 1)">↓</button>
                  <button class="sec-btn" :disabled="exam.sections.length <= 1" @click="examStore.removeSection(exam.id, sec.id)">删除</button>
                </div>
              </div>
            </div>

            <div v-if="sec.questions.length" class="section-questions">
              <div v-for="(q, qIdx) in sec.questions" :key="q.questionUuid" class="exam-question-item">
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
                    @change="examStore.updateQuestionScore(exam.id, sec.id, q.questionUuid, Number(($event.target as HTMLInputElement).value))"
                  />
                  <button class="eq-btn" :disabled="qIdx === 0" @click="examStore.moveQuestion(exam.id, sec.id, qIdx, qIdx - 1)">↑</button>
                  <button class="eq-btn" :disabled="qIdx === sec.questions.length - 1" @click="examStore.moveQuestion(exam.id, sec.id, qIdx, qIdx + 1)">↓</button>
                  <button class="eq-btn" :disabled="sIdx === 0" @click="moveAcross(sec.id, q.questionUuid, sIdx, -1)">←</button>
                  <button class="eq-btn" :disabled="sIdx === exam.sections.length - 1" @click="moveAcross(sec.id, q.questionUuid, sIdx, 1)">→</button>
                  <button class="eq-btn danger-btn" @click="examStore.removeQuestion(exam.id, sec.id, q.questionUuid)">×</button>
                </div>
              </div>
            </div>
            <div v-else class="section-empty">当前大题没有题目。</div>
          </div>

          <button class="add-section-btn" @click="examStore.addSection(exam.id)">+ 添加大题</button>
        </div>

        <div class="editor-footer">
          <router-link :to="`/preview/${exam.id}`" class="footer-btn preview-btn">预览试卷</router-link>
          <button class="footer-btn export-btn" @click="doExportWord" :disabled="exporting">
            {{ exporting ? '导出中…' : '导出 Word' }}
          </button>
        </div>
      </div>

      <div v-else class="state-panel">
        <p>试卷不存在。</p>
        <router-link to="/exams" class="state-link">返回试卷列表</router-link>
      </div>
    </section>

    <QuestionDetailModal
      v-if="detailUuid"
      :question-uuid="detailUuid"
      @close="detailUuid = null"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import LatexPreview from '@/components/LatexPreview.vue'
import QuestionDetailModal from '@/components/QuestionDetailModal.vue'
import { useExamStore, type ExamSection } from '@/stores/exam'
import { useNotificationStore } from '@/stores/notification'
import { useQuestionAssets } from '@/composables/useQuestionAssets'

const route = useRoute()
const examStore = useExamStore()
const notif = useNotificationStore()
const assetHelper = useQuestionAssets()

const assetRenderKey = ref(0)
const exporting = ref(false)
const detailUuid = ref<string | null>(null)

const exam = computed(() => examStore.activeExam)
const totalQuestionCount = computed(() =>
  exam.value?.sections.reduce((sum, section) => sum + section.questions.length, 0) ?? 0
)

onMounted(async () => {
  await loadExam()
})

watch(() => route.params.id, async () => {
  await loadExam()
})

async function loadExam() {
  const id = route.params.id as string
  if (!id) return
  await examStore.setActiveExam(id)
  const uuids = examStore.activeExam?.sections.flatMap((section) => section.questions.map((question) => question.questionUuid)) ?? []
  if (!uuids.length) return
  await assetHelper.loadAssetsForMany(uuids)
  assetRenderKey.value++
}

function onExamMetaInput(field: 'title' | 'subtitle', value: string) {
  if (!exam.value) return
  examStore.updateExamMeta(exam.value.id, { [field]: value })
}

function onExamDurationInput(value: string) {
  if (!exam.value) return
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return
  examStore.updateExamMeta(exam.value.id, { duration: Math.max(1, parsed) })
}

function chineseOrdinal(idx: number): string {
  const nums = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二']
  return nums[idx] ?? String(idx + 1)
}

function sectionScore(section: ExamSection): number {
  return section.questions.reduce((sum, question) => sum + question.score, 0)
}

function globalSeq(sectionIndex: number, questionIndex: number): number {
  if (!exam.value) return questionIndex + 1
  let offset = 0
  for (let i = 0; i < sectionIndex; i++) {
    offset += exam.value.sections[i].questions.length
  }
  return offset + questionIndex + 1
}

function moveAcross(fromSectionId: string, questionUuid: string, sectionIndex: number, delta: -1 | 1) {
  if (!exam.value) return
  const targetSection = exam.value.sections[sectionIndex + delta]
  if (!targetSection) return
  examStore.moveQuestionAcrossSections(exam.value.id, fromSectionId, questionUuid, targetSection.id)
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
  } catch (error: any) {
    notif.log(error?.message ?? '导出失败')
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
.compose-editor {
  flex: 1;
  overflow-y: auto;
}
.editor-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 20px 24px;
}
.state-panel {
  flex: 1;
  display: grid;
  place-items: center;
  text-align: center;
  color: var(--color-text-muted);
}
.state-link {
  color: var(--color-accent);
  text-decoration: none;
}
.exam-meta-bar,
.section-block {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
}
.exam-meta-bar {
  padding: 20px;
}
.exam-title-input,
.exam-subtitle-input,
.section-title-input {
  width: 100%;
  background: transparent;
  color: var(--color-text-primary);
}
.exam-title-input {
  border: none;
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 8px;
}
.exam-subtitle-input {
  border: none;
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: 12px;
}
.exam-meta-row,
.section-actions,
.section-move-btns,
.eq-right,
.editor-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}
.duration-input,
.default-score-input,
.score-input {
  width: 60px;
  text-align: center;
}
.section-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border-light);
  background: var(--color-bg-panel);
}
.section-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.section-seq,
.eq-seq {
  color: var(--color-accent);
  font-weight: 700;
}
.section-title-input {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 6px 8px;
}
.section-questions {
  padding: 4px 0;
}
.exam-question-item {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid var(--color-border-light);
}
.eq-left {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  flex: 1;
  cursor: pointer;
}
.eq-stem {
  flex: 1;
  font-size: 13px;
}
.eq-btn,
.sec-btn {
  min-width: 28px;
  height: 28px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-secondary);
}
.danger-btn {
  color: var(--color-danger);
}
.section-empty {
  padding: 20px;
  text-align: center;
  color: var(--color-text-muted);
}
.add-section-btn,
.footer-btn {
  padding: 10px 20px;
  border-radius: var(--radius-md);
}
.add-section-btn {
  width: 100%;
  border: 2px dashed var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
}
.editor-footer {
  justify-content: flex-end;
}
.footer-btn {
  border: none;
  text-decoration: none;
  cursor: pointer;
}
.preview-btn {
  background: linear-gradient(135deg, var(--color-accent), #a29bfe);
  color: #fff;
}
.export-btn {
  border: 1px solid var(--color-accent);
  background: transparent;
  color: var(--color-accent);
}
</style>
