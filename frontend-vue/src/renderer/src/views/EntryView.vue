<template>
  <div class="entry-view">
    <!-- ───── Sidebar ───── -->
    <aside class="entry-sidebar">
      <!-- Create Panel -->
      <div class="create-panel">
        <button class="btn-primary" @click="createSingle">+ 新建题目</button>
        <button class="btn-secondary btn-mini" @click="createBatch">批量新建</button>
      </div>

      <!-- Task List Card -->
      <div class="question-list-card">
        <!-- Stage Filter -->
        <div class="stage-filter">
          <button
            v-for="s in stages"
            :key="s.key"
            class="filter-btn"
            :class="{ active: questionStore.stageFilter === s.key }"
            @click="questionStore.setStageFilter(s.key)"
          >
            {{ s.label }}
            <span class="count-badge">{{ questionStore.stageCounts[s.key] }}</span>
          </button>
        </div>

        <!-- Question List -->
        <div class="question-list">
        <div
          v-for="entry in questionStore.filteredEntries"
          :key="entry.questionUuid"
          class="question-card"
          :class="{
            selected: entry.questionUuid === questionStore.selectedQuestionUuid,
            ['stage-' + stageOf(entry).toLowerCase().replace('_', '-')]: true
          }"
          @click="onSelectQuestion(entry.questionUuid)"
          @contextmenu.prevent="onContextMenu($event, entry)"
        >
          <div class="card-header">
            <span class="card-uuid">{{ entry.questionUuid.slice(0, 8) }}</span>
            <span class="card-badge" :class="'badge-' + stageOf(entry).toLowerCase().replace('_', '-')">
              {{ stageLabel(stageOf(entry)) }}
            </span>
          </div>
          <div class="card-info">
            <span v-if="entry.answerCount">{{ entry.answerCount }} 个解法</span>
            <span v-if="entry.mainTags.length" class="card-tags">
              {{ entry.mainTags.map(t => t.tagName).join(' · ') }}
            </span>
          </div>
        </div>
        <div v-if="questionStore.filteredEntries.length === 0" class="empty-list">
          暂无题目
        </div>
      </div>
      </div><!-- end question-list-card -->

      <!-- Event Log -->
      <EventLog :logs="notif.recentLogs" @clear="notif.clear()" />
    </aside>

    <!-- ───── Main Content ───── -->
    <main class="entry-main">
      <!-- Empty state -->
      <div v-if="!selected" class="empty-state">
        <p>从左侧选择或新建一道题目</p>
      </div>

      <!-- PENDING_STEM: Stem Work Card -->
      <div v-else-if="currentStage === 'PENDING_STEM'" class="work-card stem-work-card">
        <h3 class="card-title">题干录入 · {{ selected.questionUuid.slice(0, 8) }}</h3>

        <div class="work-columns">
          <!-- Editor -->
          <div class="editor-col">
            <StemEditor
              ref="stemEditorRef"
              :model-value="selected.stemDraft"
              root-tag="stem"
              :screenshot-enabled="true"
              :image-resolver="resolveStemImage"
              @update:model-value="onStemDraftChange"
              @screenshot="onScreenshotInsertImage"
              @choice-screenshot="onChoiceScreenshot"
            />

            <!-- Main Tags -->
            <TagSection
              ref="stemTagRef"
              :main-tags="selected.mainTags"
              :secondary-tags="selected.secondaryTags"
              @change="onStemTagsChange"
            />

            <!-- OCR controls -->
            <div class="ocr-row">
              <button class="btn-secondary" @click="triggerStemOcr" :disabled="stemOcrPending">
                {{ stemOcrPending ? 'OCR识别中...' : '📷 截图OCR' }}
              </button>
              <button
                v-if="stemOcrResult"
                class="btn-accent"
                @click="useStemOcrResult"
              >
                使用OCR结果
              </button>
            </div>

            <div class="action-row">
              <button class="btn-primary" @click="confirmStem" :disabled="!selected.stemDraft.trim()">
                ✅ 确认题干
              </button>
            </div>
          </div>

          <!-- Preview -->
          <div class="preview-col">
            <LatexPreview
              :xml="selected.stemDraft"
              placeholder="编辑题干后预览"
              :image-resolver="resolveStemImage"
            />
          </div>
        </div>
      </div>

      <!-- PENDING_ANSWER: Answer Work Card -->
      <div v-else-if="currentStage === 'PENDING_ANSWER'" class="work-card answer-work-card">
        <h3 class="card-title">答案录入 · {{ selected.questionUuid.slice(0, 8) }}</h3>

        <div class="work-columns">
          <div class="editor-col">
            <!-- Stem preview (readonly) -->
            <div class="stem-readonly">
              <h4>题干</h4>
              <LatexPreview
                :xml="selected.stemText"
                compact
                :image-resolver="resolveStemImage"
              />
            </div>

            <!-- Answer editor -->
            <h4>答案</h4>
            <StemEditor
              ref="answerEditorRef"
              :model-value="selected.answerDraft"
              root-tag="answer"
              :screenshot-enabled="true"
              :image-resolver="resolveAnswerImage"
              @update:model-value="onAnswerDraftChange"
              @screenshot="onAnswerScreenshotInsert"
            />

            <!-- Answer OCR -->
            <div class="ocr-row">
              <button class="btn-secondary" @click="triggerAnswerOcr" :disabled="answerOcrPending">
                {{ answerOcrPending ? 'OCR识别中...' : '📷 答案OCR' }}
              </button>
              <button
                v-if="answerOcrResult"
                class="btn-accent"
                @click="useAnswerOcrResult"
              >
                使用OCR结果
              </button>
            </div>

            <!-- Tags & Difficulty -->
            <TagSection
              ref="answerTagRef"
              :main-tags="selected.mainTags"
              :secondary-tags="selected.secondaryTags"
              @change="onAnswerTagsChange"
            />
            <DifficultySlider
              :model-value="selected.difficulty"
              @update:model-value="onDifficultyChange"
            />

            <!-- AI Analysis -->
            <AiAnalysisPanel
              :pending="questionStore.entryAi.pending.has(selected.questionUuid)"
              :result="questionStore.entryAi.lastResult?.questionUuid === selected.questionUuid ? questionStore.entryAi.lastResult : null"
              :disabled="!selected.stemConfirmed"
              @request-analysis="requestAi"
              @apply-recommendation="applyAi"
            />

            <!-- Answer history -->
            <div v-if="selected.answersLocal.length" class="answer-history">
              <h4>已录入答案 ({{ selected.answersLocal.length }})</h4>
              <div v-for="(ans, idx) in selected.answersLocal" :key="idx" class="answer-item">
                <span>解法 {{ idx + 1 }}</span>
              </div>
            </div>

            <!-- Actions -->
            <div class="action-row">
              <button class="btn-secondary" @click="addAnswerAction">
                + 添加答案
              </button>
              <button class="btn-primary" @click="completeAction" :disabled="selected.answerCount === 0">
                ✅ 完成录入
              </button>
            </div>
          </div>

          <!-- Preview -->
          <div class="preview-col">
            <LatexPreview
              :xml="selected.answerDraft"
              placeholder="编辑答案后预览"
              mode="answer"
              :image-resolver="resolveAnswerImage"
            />
          </div>
        </div>
      </div>

      <!-- COMPLETED: Completed Card -->
      <div v-else-if="currentStage === 'COMPLETED'" class="work-card completed-card">
        <h3 class="card-title">已完成 · {{ selected.questionUuid.slice(0, 8) }}</h3>

        <div class="work-columns">
          <div class="editor-col">
            <!-- Stem preview -->
            <div class="section">
              <h4>题干</h4>
              <LatexPreview :xml="selected.stemText" :image-resolver="resolveStemImage" />
            </div>

            <!-- Tags (readonly) -->
            <TagSection
              :main-tags="selected.mainTags"
              :secondary-tags="selected.secondaryTags"
              :readonly="true"
            />

            <!-- Difficulty (readonly) -->
            <DifficultySlider
              :model-value="selected.difficulty"
              :readonly="true"
            />
          </div>

          <div class="preview-col">
            <!-- Answer tabs -->
            <AnswerTabNav
              v-if="selected.answersLocal.length > 0"
              :answers="selected.answersLocal"
              :model-value="selected.answerViewIndex"
              @update:model-value="onAnswerTabChange"
            />
            <LatexPreview
              :xml="selected.answersLocal[selected.answerViewIndex] ?? ''"
              placeholder="无答案"
              mode="answer"
              :image-resolver="resolveAnswerImage"
            />
          </div>
        </div>
      </div>
    </main>

    <!-- Context Menu -->
    <Teleport to="body">
      <div
        v-if="ctxMenu.visible"
        class="context-menu"
        :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
        @click="ctxMenu.visible = false"
      >
        <div
          v-if="ctxMenu.entry && stageOf(ctxMenu.entry) === 'PENDING_STEM' && ctxMenu.entry.answerCount === 0"
          class="ctx-item danger"
          @click="deleteAction(ctxMenu.entry!.questionUuid)"
        >
          删除题目
        </div>
        <div v-else class="ctx-item disabled">不可删除</div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, onBeforeUnmount } from 'vue'
import StemEditor from '@/components/StemEditor.vue'
import LatexPreview from '@/components/LatexPreview.vue'
import TagSection from '@/components/TagSection.vue'
import DifficultySlider from '@/components/DifficultySlider.vue'
import AiAnalysisPanel from '@/components/AiAnalysisPanel.vue'
import AnswerTabNav from '@/components/AnswerTabNav.vue'
import EventLog from '@/components/EventLog.vue'
import { useAuthStore } from '@/stores/auth'
import { useQuestionStore, stageOf, type QuestionEntry, type QuestionStage } from '@/stores/question'
import { useNotificationStore } from '@/stores/notification'
import { toStemXmlPayload, toAnswerXmlPayload } from '@/lib/stemXml'
import { refSeed, nextFigureRef } from '@/lib/imageRef'
import type { InlineImageEntry } from '@/api/types'

const auth = useAuthStore()
const questionStore = useQuestionStore()
const notif = useNotificationStore()

const stemEditorRef = ref<InstanceType<typeof StemEditor> | null>(null)
const answerEditorRef = ref<InstanceType<typeof StemEditor> | null>(null)
const stemTagRef = ref<InstanceType<typeof TagSection> | null>(null)
const answerTagRef = ref<InstanceType<typeof TagSection> | null>(null)

// ── Computed ──

const selected = computed(() => questionStore.selectedEntry)
const currentStage = computed(() => (selected.value ? stageOf(selected.value) : null))

const stages: Array<{ key: QuestionStage; label: string }> = [
  { key: 'PENDING_STEM', label: '待题干' },
  { key: 'PENDING_ANSWER', label: '待答案' },
  { key: 'COMPLETED', label: '已完成' }
]

// ── OCR State ──

const stemOcrPending = computed(() => {
  if (!selected.value) return false
  return selected.value.lastOcrStatus === 'PENDING' || selected.value.lastOcrStatus === 'PROCESSING'
})

const stemOcrResult = computed(() => {
  if (!selected.value?.lastOcrTaskUuid) return null
  const task = questionStore.ocrTasks.get(selected.value.lastOcrTaskUuid)
  return task?.status === 'SUCCESS' ? task.recognizedText ?? null : null
})

const answerOcrPending = computed(() => {
  if (!selected.value) return false
  return (
    selected.value.lastAnswerOcrStatus === 'PENDING' ||
    selected.value.lastAnswerOcrStatus === 'PROCESSING'
  )
})

const answerOcrResult = computed(() => {
  if (!selected.value?.lastAnswerOcrTaskUuid) return null
  const task = questionStore.ocrTasks.get(selected.value.lastAnswerOcrTaskUuid)
  return task?.status === 'SUCCESS' ? task.recognizedText ?? null : null
})

// ── Context Menu ──

const ctxMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  entry: null as QuestionEntry | null
})

function onContextMenu(ev: MouseEvent, entry: QuestionEntry) {
  ctxMenu.visible = true
  ctxMenu.x = ev.clientX
  ctxMenu.y = ev.clientY
  ctxMenu.entry = entry
}

function hideCtxMenu() {
  ctxMenu.visible = false
}

onMounted(() => document.addEventListener('click', hideCtxMenu))
onBeforeUnmount(() => document.removeEventListener('click', hideCtxMenu))

// ── Image Resolvers ──

function resolveStemImage(refKey: string): string {
  const entry = selected.value
  if (!entry) return ''
  const data = entry.inlineImages[refKey]
  if (!data) return ''
  return data.startsWith('data:') ? data : `data:image/png;base64,${data}`
}

function resolveAnswerImage(refKey: string): string {
  const entry = selected.value
  if (!entry) return ''
  const data = entry.answerImages[refKey] ?? entry.inlineImages[refKey]
  if (!data) return ''
  return data.startsWith('data:') ? data : `data:image/png;base64,${data}`
}

// ── Create / Delete ──

async function createSingle() {
  await questionStore.createQuestion(auth.token)
}

async function createBatch() {
  const count = 5
  for (let i = 0; i < count; i++) {
    await questionStore.createQuestion(auth.token)
  }
  notif.log(`批量新建 ${count} 道题目`)
}

async function deleteAction(uuid: string) {
  await questionStore.deleteQuestion(auth.token, uuid)
}

// ── Select ──

async function onSelectQuestion(uuid: string) {
  questionStore.selectQuestion(uuid)
  const entry = questionStore.entries.get(uuid)
  if (entry && !entry.assetsLoaded) {
    try {
      await questionStore.fetchAssets(auth.token, uuid)
    } catch {
      // assets may not be available for new questions
    }
  }
}

// ── Stem Editing ──

function onStemDraftChange(xml: string) {
  if (!selected.value) return
  selected.value.stemDraft = xml
}

function onStemTagsChange(_payload: { tags: string[] }) {
  // Save draft tags — actual API call happens at confirmStem
}

async function confirmStem() {
  const entry = selected.value
  if (!entry) return

  const stemXml = toStemXmlPayload(entry.stemDraft)
  if (!stemXml) {
    notif.log('题干内容无效')
    return
  }

  // Collect inline images
  const inlineImages: Record<string, InlineImageEntry> = {}
  for (const [key, data] of Object.entries(entry.inlineImages)) {
    inlineImages[key] = {
      imageData: data.startsWith('data:') ? data.split(',')[1] ?? data : data
    }
  }

  await questionStore.confirmStem(auth.token, entry.questionUuid, {
    stemXml,
    inlineImages: Object.keys(inlineImages).length > 0 ? inlineImages : undefined
  })

  // Update tags if modified
  const tagPayload = stemTagRef.value?.toPayload?.()
  if (tagPayload?.tags.length) {
    await questionStore.updateTags(auth.token, entry.questionUuid, tagPayload.tags)
  }
}

// ── Answer Editing ──

function onAnswerDraftChange(xml: string) {
  if (!selected.value) return
  selected.value.answerDraft = xml
}

function onAnswerTagsChange(_payload: { tags: string[] }) {
  // Tags saved on complete or explicitly
}

let difficultyTimer: ReturnType<typeof setTimeout> | null = null

function onDifficultyChange(val: number | null) {
  if (!selected.value || val === null) return
  selected.value.difficulty = val
  if (difficultyTimer) clearTimeout(difficultyTimer)
  difficultyTimer = setTimeout(async () => {
    if (!selected.value) return
    await questionStore.updateDifficulty(auth.token, selected.value.questionUuid, val)
  }, 600)
}

async function addAnswerAction() {
  const entry = selected.value
  if (!entry) return
  const answerXml = toAnswerXmlPayload(entry.answerDraft)
  if (!answerXml) {
    notif.log('答案内容无效')
    return
  }

  // Collect answer images
  const inlineImages: Record<string, InlineImageEntry> = {}
  for (const [key, data] of Object.entries(entry.answerImages)) {
    inlineImages[key] = {
      imageData: data.startsWith('data:') ? data.split(',')[1] ?? data : data
    }
  }

  await questionStore.addAnswer(auth.token, entry.questionUuid, {
    latexText: answerXml,
    inlineImages: Object.keys(inlineImages).length > 0 ? inlineImages : undefined
  })

  // Clear answer draft
  entry.answerDraft = ''
  entry.answerImages = {}
}

async function completeAction() {
  const entry = selected.value
  if (!entry) return

  // Save tags if modified
  const tagPayload = answerTagRef.value?.toPayload?.()
  if (tagPayload?.tags.length) {
    await questionStore.updateTags(auth.token, entry.questionUuid, tagPayload.tags)
  }

  await questionStore.completeQuestion(auth.token, entry.questionUuid)
}

// ── OCR ──

function triggerStemOcr() {
  questionStore.screenshotIntent = 'question-ocr'
  window.qforge.screenshot.trigger({ intent: 'question-ocr' })
}

function triggerAnswerOcr() {
  questionStore.screenshotIntent = 'answer-ocr'
  window.qforge.screenshot.trigger({ intent: 'answer-ocr' })
}

function useStemOcrResult() {
  if (!selected.value || !stemOcrResult.value) return
  selected.value.stemDraft = stemOcrResult.value
}

function useAnswerOcrResult() {
  if (!selected.value || !answerOcrResult.value) return
  selected.value.answerDraft = answerOcrResult.value
}

// ── Screenshot callbacks ──

function onScreenshotInsertImage() {
  questionStore.screenshotIntent = 'insert-image'
  window.qforge.screenshot.trigger({ intent: 'insert-image' })
}

function onChoiceScreenshot() {
  window.qforge.screenshot.trigger({ intent: 'choice-image-auto' })
}

function onAnswerScreenshotInsert() {
  questionStore.screenshotIntent = 'insert-image'
  window.qforge.screenshot.trigger({ intent: 'insert-image' })
}

// Handle screenshot result from Electron
onMounted(() => {
  window.qforge.screenshot.onCaptured(async (payload: { imageBase64: string; intent?: string }) => {
    const entry = selected.value
    if (!entry) return

    const intent = payload.intent ?? questionStore.screenshotIntent

    if (intent === 'question-ocr') {
      entry.stemImageBase64 = payload.imageBase64
      await questionStore.submitOcr(auth.token, entry.questionUuid, 'QUESTION_STEM', payload.imageBase64)
    } else if (intent === 'answer-ocr') {
      await questionStore.submitOcr(auth.token, entry.questionUuid, 'ANSWER_CONTENT', payload.imageBase64)
    } else if (intent === 'insert-image') {
      const seed = refSeed(entry.questionUuid)
      const existingRefs = Object.keys(entry.inlineImages)
      const newRef = nextFigureRef(existingRefs)
      const scopedRef = `a${seed}-${newRef}`
      entry.inlineImages[scopedRef] = payload.imageBase64
      notif.log(`插入图片 ${scopedRef}`)
    }
  })
})

// ── AI ──

async function requestAi() {
  if (!selected.value) return
  await questionStore.requestAiAnalysis(auth.token, selected.value.questionUuid, 'entry')
}

async function applyAi() {
  const entry = selected.value
  const ai = questionStore.entryAi
  if (!entry || !ai.lastResult || !ai.taskUuid) return
  await questionStore.applyAiRecommendation(
    auth.token,
    entry.questionUuid,
    ai.taskUuid,
    ai.lastResult.suggestedTags ?? undefined,
    ai.lastResult.suggestedDifficulty ?? undefined
  )
}

// ── Answer Tabs (completed) ──

function onAnswerTabChange(idx: number) {
  if (!selected.value) return
  selected.value.answerViewIndex = idx
}

// ── Helpers ──

function stageLabel(stage: QuestionStage): string {
  const map: Record<QuestionStage, string> = {
    PENDING_STEM: '待题干',
    PENDING_ANSWER: '待答案',
    COMPLETED: '已完成'
  }
  return map[stage] ?? stage
}
</script>

<style scoped>
.entry-view {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 328px 1fr;
  gap: 12px;
  padding: 12px;
  overflow: hidden;
}

/* ── Sidebar (left pane) ── */

.entry-sidebar {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: hidden;
}

.create-panel {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  box-shadow: var(--shadow-soft);
  padding: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-hint {
  width: 100%;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.stage-filter {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.filter-btn {
  border: 1px solid #c9d5ec;
  border-radius: 8px;
  padding: 8px 6px;
  background: #f6f9ff;
  color: #3a5b97;
  cursor: pointer;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: center;
}

.filter-btn.active {
  border-color: var(--color-accent);
  background: #e5eeff;
  color: #1f4cb1;
  font-weight: 600;
}

.count-badge {
  font-size: 11px;
  opacity: 0.7;
}

.question-list-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  box-shadow: var(--shadow-soft);
  padding: 14px;
}

.question-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.question-card {
  border: 1px solid #d2ddf1;
  border-radius: 10px;
  padding: 10px;
  background: #fbfdff;
  cursor: pointer;
  transition: background 0.12s;
}

.question-card:hover {
  background: #f0f5ff;
}

.question-card.selected {
  border-color: var(--color-accent);
  background: #eaf1ff;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.card-uuid {
  font-weight: 600;
  word-break: break-all;
}

.card-badge {
  display: inline-block;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
}

.badge-pending-stem { background: var(--color-warning-bg); color: var(--color-warning); }
.badge-pending-answer { background: var(--color-info-bg); color: var(--color-info); }
.badge-completed { background: var(--color-success-bg); color: var(--color-success); }

.card-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.empty-list {
  color: var(--color-text-secondary);
  font-size: 13px;
  padding: 6px;
}

/* ── Main area (right pane) ── */

.entry-main {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: hidden;
  padding-right: 2px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.work-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  box-shadow: var(--shadow-soft);
  padding: 14px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow: hidden;
}

.card-title {
  margin: 0 0 10px;
  font-size: 16px;
  font-weight: 600;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 7px 12px;
  color: var(--color-text-secondary);
  font-size: 13px;
  margin-bottom: 10px;
}

.work-columns {
  display: grid;
  grid-template-columns: minmax(280px, 1.2fr) minmax(220px, 1fr);
  gap: 10px;
  min-height: 0;
  flex: 1;
}

.editor-col {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
}

.preview-col {
  border: 1px solid #ccd8ef;
  border-radius: 10px;
  background: #f7faff;
  flex: 1;
  min-height: 0;
  padding: 12px;
  overflow: auto;
  white-space: pre-wrap;
  color: #1f355c;
}

.stem-readonly {
  padding: 12px;
  border: 1px solid #dbe4f4;
  border-radius: 12px;
  background: #f7faff;
}

.stem-readonly h4,
.section h4 {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}

.ocr-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.action-row {
  display: flex;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--color-border);
}

.answer-history {
  padding: 8px;
  border: 1px solid #d4def1;
  border-radius: 8px;
  background: #fcfdff;
}

.answer-history h4 {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 4px;
}

.answer-item {
  font-size: 12px;
  color: var(--color-text-secondary);
  padding: 2px 0;
}

/* ── Buttons (原版 .btn 体系) ── */

.btn-primary {
  border: 1px solid transparent;
  border-radius: 10px;
  padding: 9px 14px;
  cursor: pointer;
  font-weight: 500;
  color: #fff;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover));
}

.btn-primary:hover { filter: brightness(0.98); }
.btn-primary:disabled { opacity: 0.55; cursor: not-allowed; }

.btn-secondary {
  border: 1px solid #c8d5eb;
  border-radius: 10px;
  padding: 9px 14px;
  cursor: pointer;
  font-weight: 500;
  background: transparent;
  color: #274989;
}

.btn-secondary:hover { filter: brightness(0.98); background: #e9f0ff; }
.btn-secondary:disabled { opacity: 0.55; cursor: not-allowed; }

.btn-accent {
  border: 1px solid transparent;
  border-radius: 10px;
  padding: 9px 14px;
  cursor: pointer;
  font-weight: 500;
  color: #fff;
  background: linear-gradient(135deg, #22c55e, #16a34a);
}

.btn-mini {
  padding: 4px 10px;
  border-radius: 8px;
  font-size: 12px;
}

/* ── Context Menu ── */

.context-menu {
  position: fixed;
  z-index: 9999;
  min-width: 210px;
  padding: 6px;
  border: 1px solid #c7d6ef;
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 12px 30px rgba(23, 48, 90, 0.18);
}

.ctx-item {
  display: block;
  width: 100%;
  text-align: left;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  padding: 8px 10px;
  color: #1f3660;
  cursor: pointer;
}

.ctx-item:hover { background: #eef4ff; }
.ctx-item.danger { color: #b73333; }
.ctx-item.danger:hover { background: #fdecec; }
.ctx-item.disabled { color: var(--color-text-muted); cursor: default; pointer-events: none; }
</style>
