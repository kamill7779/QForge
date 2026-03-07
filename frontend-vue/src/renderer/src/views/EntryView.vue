<template>
  <div class="entry-view">
    <!-- ───── Sidebar ───── -->
    <aside class="entry-sidebar">
      <!-- Create Panel -->
      <div class="create-panel">
        <button class="btn-primary" @click="createSingle" :disabled="creating">{{ creating ? '创建中...' : '+ 新建题目' }}</button>
        <button class="btn-secondary btn-mini" @click="createBatch" :disabled="creating">批量新建</button>
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
              @choice-screenshot="onAnswerChoiceScreenshot"
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

      <!-- COMPLETED: show read-only summary (user navigated from outside) -->
      <div v-else-if="currentStage === 'COMPLETED'" class="work-card completed-card">
        <h3 class="card-title">已完成 · {{ selected.questionUuid.slice(0, 8) }}</h3>
        <p class="completed-hint">此题已录入完成，请前往题库查看详情。</p>
      </div>
    </main>

    <!-- Context Menu -->
    <Teleport to="body">
      <div
        v-if="ctxMenu.visible"
        class="context-menu-backdrop"
        @click.self="hideCtxMenu"
        @contextmenu.prevent="hideCtxMenu"
      >
        <div
          class="context-menu"
          :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
        >
          <div
            v-if="ctxMenu.stage !== 'COMPLETED'"
            class="ctx-item danger"
            @click.stop="onDeleteClick"
          >
            删除题目
          </div>
          <div v-else class="ctx-item disabled">不可删除</div>
        </div>
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
  { key: 'PENDING_ANSWER', label: '待答案' }
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
  uuid: '' as string,
  stage: '' as string
})

function onContextMenu(ev: MouseEvent, entry: QuestionEntry) {
  // Store only primitive values — never store reactive store objects
  ctxMenu.uuid = entry.questionUuid
  ctxMenu.stage = stageOf(entry)
  ctxMenu.x = ev.clientX
  ctxMenu.y = ev.clientY
  ctxMenu.visible = true
}

function hideCtxMenu() {
  ctxMenu.visible = false
}

function onDeleteClick() {
  const uuid = ctxMenu.uuid
  hideCtxMenu()
  if (uuid) deleteAction(uuid)
}

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

const creating = ref(false)

async function createSingle() {
  if (creating.value) return
  creating.value = true
  try {
    await questionStore.createQuestion(auth.token)
  } catch (e: any) {
    notif.log(`创建失败: ${e?.message || e}`)
  } finally {
    creating.value = false
  }
}

async function createBatch() {
  if (creating.value) return
  creating.value = true
  const count = 5
  try {
    for (let i = 0; i < count; i++) {
      await questionStore.createQuestion(auth.token)
    }
    notif.log(`批量新建 ${count} 道题目`)
  } catch (e: any) {
    notif.log(`批量创建失败: ${e?.message || e}`)
  } finally {
    creating.value = false
  }
}

async function deleteAction(uuid: string) {
  try {
    await questionStore.deleteQuestion(auth.token, uuid)
  } catch (e: any) {
    notif.log(`删除失败: ${e?.message || e}`)
  }
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
  window.qforge.screenshot.trigger({ intent: 'ocr' })
}

function triggerAnswerOcr() {
  window.qforge.screenshot.trigger({ intent: 'ocr' })
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

/** Track which choice is awaiting an image screenshot. */
const pendingChoiceImageTarget = ref<{
  blockIndex: number
  choiceIndex: number
  isAnswer: boolean
} | null>(null)

function onScreenshotInsertImage() {
  pendingChoiceImageTarget.value = null
  window.qforge.screenshot.trigger({ intent: 'insert-image' })
}

function onChoiceScreenshot(blockIndex: number, choiceIndex: number) {
  pendingChoiceImageTarget.value = { blockIndex, choiceIndex, isAnswer: false }
  window.qforge.screenshot.trigger({ intent: 'choice-image-manual' })
}

function onAnswerScreenshotInsert() {
  pendingChoiceImageTarget.value = null
  window.qforge.screenshot.trigger({ intent: 'insert-image' })
}

function onAnswerChoiceScreenshot(blockIndex: number, choiceIndex: number) {
  pendingChoiceImageTarget.value = { blockIndex, choiceIndex, isAnswer: true }
  window.qforge.screenshot.trigger({ intent: 'choice-image-manual' })
}

// Handle screenshot result from Electron
let cleanupCaptured: (() => void) | null = null
let cleanupError: (() => void) | null = null

/**
 * Find the next choice slot with empty imageRef.
 * Searches the given editor's blocks for the first choices block
 * with an unfilled choice item.
 */
function findNextEmptyChoiceSlot(
  editorRef: InstanceType<typeof StemEditor> | null
): { blockIndex: number; choiceIndex: number } | null {
  if (!editorRef?.blocks) return null
  const blocks = editorRef.blocks
  for (let bi = 0; bi < blocks.length; bi++) {
    const b = blocks[bi]
    if (b.type === 'choices') {
      for (let ci = 0; ci < b.items.length; ci++) {
        if (!b.items[ci].imageRef) return { blockIndex: bi, choiceIndex: ci }
      }
    }
  }
  return null
}

onMounted(() => {
  cleanupCaptured = window.qforge.screenshot.onCaptured(async (payload: { imageBase64: string; intent?: string }) => {
    const intent = payload.intent ?? 'ocr'

    // ── OCR: auto-create entry if none selected (like old createByShot) ──
    if (intent === 'ocr') {
      let entry = selected.value
      const stage = entry ? stageOf(entry) : null

      // No entry or current entry is COMPLETED → create a new one automatically
      if (!entry || stage === 'COMPLETED') {
        try {
          const uuid = await questionStore.createQuestion(auth.token)
          questionStore.selectQuestion(uuid)
          questionStore.setStageFilter('PENDING_STEM')
          entry = questionStore.entries.get(uuid) ?? null
          if (!entry) {
            notif.log('创建题目失败')
            return
          }
        } catch (e: any) {
          notif.log(`截图录题失败: ${e?.message || e}`)
          return
        }
      }

      try {
        const entryStage = stageOf(entry)
        if (entryStage === 'PENDING_ANSWER') {
          await questionStore.submitOcr(auth.token, entry.questionUuid, 'ANSWER_CONTENT', payload.imageBase64)
        } else {
          // Default to stem OCR (PENDING_STEM)
          entry.stemImageBase64 = payload.imageBase64
          await questionStore.submitOcr(auth.token, entry.questionUuid, 'QUESTION_STEM', payload.imageBase64)
        }
        notif.log('OCR 已提交，等待识别结果…')
      } catch (e: any) {
        notif.log(`OCR 提交失败: ${e?.message || e}`)
      }
      return
    }

    // Below intents require an existing entry
    const entry = selected.value
    if (!entry) {
      notif.log('请先选择一道题目')
      return
    }
    const entryStage = stageOf(entry)

    // ── Insert image: context-aware (stem editor vs answer editor) ──
    if (intent === 'insert-image') {
      const seed = refSeed(entry.questionUuid)
      if (entryStage === 'PENDING_ANSWER') {
        const existingRefs = Object.keys(entry.answerImages)
        const newRef = nextFigureRef(existingRefs)
        const scopedRef = `a${seed}-${newRef}`
        entry.answerImages[scopedRef] = payload.imageBase64
        answerEditorRef.value?.addImageBlock(scopedRef)
        notif.log(`答案插图已插入 ${scopedRef}`)
      } else {
        const existingRefs = Object.keys(entry.inlineImages)
        const newRef = nextFigureRef(existingRefs)
        const scopedRef = `a${seed}-${newRef}`
        entry.inlineImages[scopedRef] = payload.imageBase64
        stemEditorRef.value?.addImageBlock(scopedRef)
        notif.log(`题干插图已插入 ${scopedRef}`)
      }
      return
    }

    // ── Choice image (global shortcut): auto-find next empty slot ──
    if (intent === 'choice-image') {
      const isAnswer = entryStage === 'PENDING_ANSWER'
      const editorRef = isAnswer ? answerEditorRef.value : stemEditorRef.value
      const slot = findNextEmptyChoiceSlot(editorRef)
      if (!slot) {
        notif.log('所有选项已有插图，无空位可填充')
        return
      }
      const seed = refSeed(entry.questionUuid)
      const imageStore = isAnswer ? entry.answerImages : entry.inlineImages
      const existingRefs = Object.keys(imageStore)
      const newRef = nextFigureRef(existingRefs)
      const scopedRef = `a${seed}-${newRef}`
      imageStore[scopedRef] = payload.imageBase64
      editorRef?.setChoiceImageRef(slot.blockIndex, slot.choiceIndex, scopedRef)
      notif.log(`选项 ${editorRef?.blocks?.[slot.blockIndex]?.type === 'choices' ? (editorRef.blocks[slot.blockIndex] as any).items[slot.choiceIndex]?.key : ''} 插图已插入`)
      return
    }

    // ── Choice image (manual click): insert to specific slot ──
    if (intent === 'choice-image-manual') {
      const target = pendingChoiceImageTarget.value
      if (!target) {
        notif.log('当前没有可插图的选项')
        return
      }
      const seed = refSeed(entry.questionUuid)
      const imageStore = target.isAnswer ? entry.answerImages : entry.inlineImages
      const existingRefs = Object.keys(imageStore)
      const newRef = nextFigureRef(existingRefs)
      const scopedRef = `a${seed}-${newRef}`
      imageStore[scopedRef] = payload.imageBase64

      const editorRef = target.isAnswer ? answerEditorRef.value : stemEditorRef.value
      editorRef?.setChoiceImageRef(target.blockIndex, target.choiceIndex, scopedRef)
      notif.log(`选项插图已插入 ${scopedRef}`)
      pendingChoiceImageTarget.value = null
      return
    }
  })

  // Handle screenshot errors
  cleanupError = window.qforge.screenshot.onError((payload: { error: string }) => {
    notif.log(`截图服务异常: ${payload.error || '未知错误'}`)
    pendingChoiceImageTarget.value = null
  })
})

onBeforeUnmount(() => {
  cleanupCaptured?.()
  cleanupError?.()
  cleanupCaptured = null
  cleanupError = null
  if (difficultyTimer) clearTimeout(difficultyTimer)
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
  grid-template-columns: 300px 1fr;
  gap: 0;
  overflow: hidden;
  height: 100%;
}

/* ── Sidebar (light warm) ── */

.entry-sidebar {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0;
  overflow: hidden;
  background: var(--color-bg-sidebar);
  border-right: 1px solid var(--color-border);
}

.create-panel {
  padding: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  border-bottom: 1px solid var(--color-border-light);
}

.stage-filter {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border-light);
}

.filter-btn {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 7px 6px;
  background: var(--color-bg-card);
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: center;
  transition: all var(--transition-fast);
}

.filter-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: var(--color-bg-hover);
}

.filter-btn.active {
  border-color: var(--color-accent);
  background: var(--color-accent-muted);
  color: var(--color-accent);
  font-weight: 600;
}

.count-badge {
  font-size: 11px;
  opacity: 0.6;
}

.question-list-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 0;
}

.question-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px;
}

.question-card {
  border: 1px solid var(--color-border-light);
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--color-bg-card);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.question-card:hover {
  border-color: var(--color-border-strong);
  box-shadow: var(--shadow-sm);
}

.question-card.selected {
  border-color: var(--color-accent);
  background: var(--color-accent-muted);
  box-shadow: inset 3px 0 0 var(--color-accent);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.card-uuid {
  font-weight: 600;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-primary);
  word-break: break-all;
}

.card-badge {
  display: inline-block;
  border-radius: var(--radius-pill);
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 600;
}

.badge-pending-stem { background: var(--color-warning-bg); color: var(--color-warning); }
.badge-pending-answer { background: var(--color-info-bg); color: var(--color-info); }
.badge-completed { background: var(--color-success-bg); color: var(--color-success); }

.card-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--color-text-muted);
  font-size: 11px;
}

.empty-list {
  color: var(--color-text-muted);
  font-size: 13px;
  padding: 20px 12px;
  text-align: center;
}

/* ── Main area (right pane — light) ── */

.entry-main {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0;
  overflow: hidden;
  padding: 20px 24px;
  background: var(--color-bg-primary);
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-muted);
  font-size: 14px;
}

.work-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  padding: 24px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow: hidden;
  animation: fadeInUp 0.3s ease;
}

.card-title {
  margin: 0 0 14px;
  font-size: 18px;
  font-weight: 700;
  font-family: var(--font-display);
  letter-spacing: 0.3px;
  color: var(--color-text-primary);
}

.work-columns {
  display: grid;
  grid-template-columns: minmax(300px, 1.2fr) minmax(240px, 1fr);
  gap: 18px;
  min-height: 0;
  flex: 1;
}

.editor-col {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  overflow-y: auto;
}

.preview-col {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-panel);
  flex: 1;
  min-height: 0;
  padding: 14px;
  overflow: auto;
  white-space: pre-wrap;
  color: var(--color-text-primary);
}

.stem-readonly {
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-panel);
}

.stem-readonly h4,
.section h4 {
  font-size: 12px;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 6px;
  font-weight: 700;
}

.ocr-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.action-row {
  display: flex;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid var(--color-border-light);
}

.answer-history {
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-panel);
}

.answer-history h4 {
  font-size: 12px;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 4px;
}

.answer-item {
  font-size: 12px;
  color: var(--color-text-secondary);
  padding: 2px 0;
}

/* ── Buttons ── */

.btn-primary {
  border: none;
  border-radius: var(--radius-md);
  padding: 9px 18px;
  cursor: pointer;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover));
  box-shadow: 0 2px 8px var(--color-accent-glow);
  transition: all var(--transition-fast);
  letter-spacing: 0.3px;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px var(--color-accent-glow);
}
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }

.btn-secondary {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 9px 18px;
  cursor: pointer;
  font-weight: 500;
  background: transparent;
  color: var(--color-text-secondary);
  transition: all var(--transition-fast);
}

.btn-secondary:hover:not(:disabled) {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: var(--color-accent-muted);
}
.btn-secondary:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-accent {
  border: none;
  border-radius: var(--radius-md);
  padding: 9px 18px;
  cursor: pointer;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, var(--color-success), #00a884);
  box-shadow: 0 2px 8px rgba(0, 184, 148, 0.25);
  transition: all var(--transition-fast);
}

.btn-accent:hover { transform: translateY(-1px); }

.btn-mini {
  padding: 5px 12px;
  border-radius: 8px;
  font-size: 12px;
}

/* ── Context Menu ── */

.context-menu-backdrop {
  position: fixed;
  inset: 0;
  z-index: 9998;
  background: transparent;
}

.context-menu {
  position: fixed;
  z-index: 9999;
  min-width: 200px;
  padding: 6px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-card);
  box-shadow: var(--shadow-lg);
}

.ctx-item {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  border-radius: 6px;
  background: transparent;
  padding: 8px 12px;
  color: var(--color-text-primary);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.ctx-item:hover { background: var(--color-accent-muted); color: var(--color-accent); }
.ctx-item.danger { color: var(--color-danger); }
.ctx-item.danger:hover { background: var(--color-danger-bg); }
.ctx-item.disabled { color: var(--color-text-muted); cursor: default; pointer-events: none; }

.completed-hint {
  color: var(--color-text-secondary);
  font-size: 14px;
  margin: 20px 0;
}
</style>
