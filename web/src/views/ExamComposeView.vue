<template>
  <div class="compose-view">
    <div v-if="composeStore.loading" class="state-panel">加载中…</div>
    <div v-else-if="!compose || !totalQuestionCount" class="state-panel">
      <p>试题篮为空，无法开始组卷。</p>
      <router-link to="/basket" class="state-link">返回试题篮</router-link>
    </div>
    <section v-else class="compose-editor">
      <div class="editor-content">
        <div class="exam-meta-bar">
          <input
            :value="compose.title"
            class="exam-title-input"
            placeholder="试卷标题"
            @input="onMetaInput('title', ($event.target as HTMLInputElement).value)"
          />
          <input
            :value="compose.subtitle"
            class="exam-subtitle-input"
            placeholder="副标题（可选）"
            @input="onMetaInput('subtitle', ($event.target as HTMLInputElement).value)"
          />
          <div class="exam-meta-row">
            <label>考试时长
              <input
                :value="compose.duration"
                type="number"
                min="1"
                class="duration-input"
                @input="onDurationInput(($event.target as HTMLInputElement).value)"
              /> 分钟
            </label>
            <span class="total-score">共 {{ totalQuestionCount }} 题</span>
          </div>
        </div>

        <div class="sections-area">
          <div
            v-for="(section, sectionIndex) in compose.sections"
            :key="section.id"
            class="section-block"
          >
            <div class="section-header">
              <div class="section-title-row">
                <span class="section-seq">{{ chineseOrdinal(sectionIndex) }}、</span>
                <input
                  :value="section.title"
                  class="section-title-input"
                  placeholder="大题标题"
                  @input="composeStore.updateSection(section.id, { title: ($event.target as HTMLInputElement).value })"
                />
              </div>
              <div class="section-actions">
                <label class="default-score-label">默认分值
                  <input
                    type="number"
                    class="default-score-input"
                    :value="section.defaultScore ?? 5"
                    min="0"
                    @change="composeStore.updateSection(section.id, { defaultScore: Math.max(0, Number(($event.target as HTMLInputElement).value)) })"
                  />
                </label>
                <span class="section-stats">{{ section.questions.length }} 题</span>
                <div class="section-move-btns">
                  <button class="sec-btn" :disabled="sectionIndex === 0" @click="composeStore.moveSection(sectionIndex, sectionIndex - 1)">↑</button>
                  <button class="sec-btn" :disabled="sectionIndex === compose.sections.length - 1" @click="composeStore.moveSection(sectionIndex, sectionIndex + 1)">↓</button>
                  <button class="sec-btn" :disabled="compose.sections.length <= 1" @click="composeStore.removeSection(section.id)">删除</button>
                </div>
              </div>
            </div>

            <div v-if="section.questions.length" class="section-questions">
              <div v-for="(question, questionIndex) in section.questions" :key="question.questionUuid" class="exam-question-item">
                <div class="eq-left" @click="openDetail(question)">
                  <span class="eq-seq">{{ globalSeq(sectionIndex, questionIndex) }}.</span>
                  <LatexPreview
                    :xml="question.stemText"
                    :image-resolver="assetHelper.resolverFor(question.questionUuid)"
                    :render-key="assetRenderKey"
                    class="eq-stem"
                  />
                </div>
                <div class="eq-right">
                  <input
                    type="number"
                    class="score-input"
                    :value="question.score"
                    min="0"
                    @change="composeStore.updateQuestionScore(section.id, question.questionUuid, Number(($event.target as HTMLInputElement).value))"
                  />
                  <button class="eq-btn" :disabled="questionIndex === 0" @click="composeStore.moveQuestion(section.id, questionIndex, questionIndex - 1)">↑</button>
                  <button class="eq-btn" :disabled="questionIndex === section.questions.length - 1" @click="composeStore.moveQuestion(section.id, questionIndex, questionIndex + 1)">↓</button>
                  <button class="eq-btn" :disabled="sectionIndex === 0" @click="moveAcross(sectionIndex, question.questionUuid, -1)">←</button>
                  <button class="eq-btn" :disabled="sectionIndex === compose.sections.length - 1" @click="moveAcross(sectionIndex, question.questionUuid, 1)">→</button>
                </div>
              </div>
            </div>
            <div v-else class="section-empty">此大题暂时没有题目，可将其他大题中的题移动到这里。</div>
          </div>

          <button class="add-section-btn" @click="composeStore.addSection()">+ 添加大题</button>
        </div>

        <div class="editor-footer">
          <router-link to="/basket" class="footer-btn secondary-btn">返回试题篮</router-link>
          <button class="footer-btn confirm-btn" :disabled="confirming" @click="confirmCompose">
            {{ confirming ? '确认中…' : '确认组卷并落库' }}
          </button>
        </div>
      </div>
    </section>

    <QuestionDetailModal
      v-if="detailQuestion"
      :question-uuid="detailQuestion.questionUuid"
      :override-stem="detailQuestion.stemText"
      :override-source="detailQuestion.source"
      :override-difficulty="detailQuestion.difficulty"
      @close="detailQuestion = null"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import LatexPreview from '@/components/LatexPreview.vue'
import QuestionDetailModal from '@/components/QuestionDetailModal.vue'
import { useBasketComposeStore, type BasketComposeQuestion } from '@/stores/basketCompose'
import { useBasketStore } from '@/stores/basket'
import { useExamStore } from '@/stores/exam'
import { useNotificationStore } from '@/stores/notification'
import { useQuestionAssets } from '@/composables/useQuestionAssets'

const router = useRouter()
const composeStore = useBasketComposeStore()
const basketStore = useBasketStore()
const examStore = useExamStore()
const notif = useNotificationStore()
const assetHelper = useQuestionAssets()

const assetRenderKey = ref(0)
const confirming = ref(false)
const detailQuestion = ref<BasketComposeQuestion | null>(null)

const compose = computed(() => composeStore.compose)
const totalQuestionCount = computed(() => composeStore.totalQuestionCount)

onMounted(async () => {
  await composeStore.fetchCompose()
  await loadAssets()
})

async function loadAssets() {
  const uuids = compose.value?.sections.flatMap((section) => section.questions.map((question) => question.questionUuid)) ?? []
  if (!uuids.length) return
  await assetHelper.loadAssetsForMany(uuids)
  assetRenderKey.value++
}

function onMetaInput(field: 'title' | 'subtitle', value: string) {
  composeStore.updateMeta({ [field]: value })
}

function onDurationInput(value: string) {
  const duration = Number(value)
  if (!Number.isFinite(duration)) return
  composeStore.updateMeta({ duration: Math.max(1, duration) })
}

function chineseOrdinal(idx: number): string {
  const nums = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二']
  return nums[idx] ?? String(idx + 1)
}

function globalSeq(sectionIndex: number, questionIndex: number): number {
  if (!compose.value) return questionIndex + 1
  let offset = 0
  for (let i = 0; i < sectionIndex; i++) {
    offset += compose.value.sections[i].questions.length
  }
  return offset + questionIndex + 1
}

function moveAcross(sectionIndex: number, questionUuid: string, delta: -1 | 1) {
  if (!compose.value) return
  const fromSection = compose.value.sections[sectionIndex]
  const targetSection = compose.value.sections[sectionIndex + delta]
  if (!fromSection || !targetSection) return
  composeStore.moveQuestionAcrossSections(fromSection.id, questionUuid, targetSection.id)
}

function openDetail(question: BasketComposeQuestion) {
  detailQuestion.value = question
}

async function confirmCompose() {
  confirming.value = true
  try {
    const paperUuid = await composeStore.confirmCompose()
    await basketStore.fetchItems()
    await examStore.fetchExams()
    notif.log('试卷已生成并落库')
    router.push(`/exams/${paperUuid}/edit`)
  } catch (error: any) {
    notif.log(error?.message ?? '确认组卷失败')
  } finally {
    confirming.value = false
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
.exam-meta-row {
  display: flex;
  align-items: center;
  gap: 20px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.duration-input,
.default-score-input,
.score-input {
  width: 60px;
  text-align: center;
}
.sections-area {
  flex: 1;
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
.section-actions,
.section-move-btns,
.eq-right,
.editor-footer {
  display: flex;
  align-items: center;
  gap: 8px;
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
.eq-btn:disabled,
.sec-btn:disabled {
  opacity: 0.35;
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
.confirm-btn {
  background: linear-gradient(135deg, var(--color-accent), #a29bfe);
  color: #fff;
}
.secondary-btn {
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
}
</style>
