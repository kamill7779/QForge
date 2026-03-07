<template>
  <div class="entry-view">
    <!-- ───── Sidebar ───── -->
    <aside class="entry-sidebar">
      <!-- Create Panel -->
      <div class="create-panel">
        <button class="btn-primary" @click="createSingle">+ 新建题目</button>
        <button class="btn-secondary" @click="createBatch">批量新建</button>
      </div>

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
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* ── Sidebar ── */

.entry-sidebar {
  width: 280px;
  min-width: 280px;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-sidebar);
  border-right: 1px solid var(--color-border);
  overflow: hidden;
}

.create-panel {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid var(--color-border);
}

.stage-filter {
  display: flex;
  gap: 4px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-border);
}

.filter-btn {
  flex: 1;
  padding: 4px 8px;
  font-size: 12px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: center;
}

.filter-btn.active {
  background: var(--color-accent);
  color: #fff;
  border-color: var(--color-accent);
}

.count-badge {
  font-size: 11px;
  opacity: 0.7;
}

.question-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

.question-card {
  padding: 8px 12px;
  cursor: pointer;
  border-bottom: 1px solid var(--color-border-light, rgba(255,255,255,0.04));
  transition: background 0.15s;
}

.question-card:hover {
  background: rgba(255, 255, 255, 0.04);
}

.question-card.selected {
  background: rgba(var(--color-accent-rgb, 59, 130, 246), 0.15);
  border-left: 3px solid var(--color-accent);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.card-uuid {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--color-text-primary);
}

.card-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
}

.badge-pending-stem { background: var(--color-warning, #f59e0b); color: #000; }
.badge-pending-answer { background: var(--color-info, #3b82f6); color: #fff; }
.badge-completed { background: var(--color-success, #22c55e); color: #fff; }

.card-info {
  font-size: 11px;
  color: var(--color-text-muted);
  display: flex;
  gap: 8px;
}

.empty-list {
  padding: 24px;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 13px;
}

/* ── Main ── */

.entry-main {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-muted);
  font-size: 15px;
}

.work-card {
  max-width: 1200px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--color-text-primary);
}

.work-columns {
  display: flex;
  gap: 16px;
}

.editor-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preview-col {
  flex: 1;
  min-width: 0;
  padding: 12px;
  background: var(--color-bg-secondary);
  border-radius: 8px;
  border: 1px solid var(--color-border);
  overflow-y: auto;
  max-height: calc(100vh - 180px);
}

.stem-readonly {
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-bg-secondary);
}

.stem-readonly h4,
.section h4 {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: 8px;
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
  border: 1px solid var(--color-border);
  border-radius: 6px;
}

.answer-history h4 {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: 4px;
}

.answer-item {
  font-size: 12px;
  color: var(--color-text-secondary);
  padding: 2px 0;
}

/* ── Buttons ── */

.btn-primary {
  padding: 6px 14px;
  background: var(--color-accent);
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}

.btn-primary:hover { opacity: 0.9; }
.btn-primary:disabled { opacity: 0.4; cursor: default; }

.btn-secondary {
  padding: 6px 14px;
  background: transparent;
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}

.btn-secondary:hover { background: rgba(255,255,255,0.06); }
.btn-secondary:disabled { opacity: 0.4; cursor: default; }

.btn-accent {
  padding: 6px 14px;
  background: var(--color-success, #22c55e);
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}

/* ── Context Menu ── */

.context-menu {
  position: fixed;
  background: var(--color-bg-elevated, #2d2d2d);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.4);
  min-width: 120px;
  z-index: 9999;
  padding: 4px;
}

.ctx-item {
  padding: 6px 12px;
  font-size: 13px;
  cursor: pointer;
  border-radius: 4px;
  color: var(--color-text-primary);
}

.ctx-item:hover { background: rgba(255,255,255,0.08); }
.ctx-item.danger { color: var(--color-danger, #ef4444); }
.ctx-item.disabled { color: var(--color-text-muted); cursor: default; pointer-events: none; }
</style>
