<template>
  <div class="exam-parse-view">
    <!-- ───── Sidebar ───── -->
    <aside class="ep-sidebar">
      <!-- Upload Panel -->
      <div class="upload-panel">
        <div
          class="upload-area"
          :class="{ dragover: isDragOver }"
          @click="onUploadClick"
          @dragover.prevent="isDragOver = true"
          @dragleave="isDragOver = false"
          @drop.prevent="onDrop"
        >
          <input
            ref="fileInputRef"
            type="file"
            multiple
            accept=".pdf,.png,.jpg,.jpeg"
            class="file-input"
            @click.stop
            @change="onFileSelect"
          />
          <span class="upload-hint">点击或拖拽上传 PDF / 图片</span>
        </div>

        <!-- File chips -->
        <div v-if="uploadFiles.length > 0" class="file-chips">
          <div v-for="(f, idx) in uploadFiles" :key="idx" class="file-chip">
            <span class="chip-name">{{ f.name }}</span>
            <span class="chip-size">{{ formatSize(f.size) }}</span>
            <button class="chip-remove" @click="removeFile(idx)">&times;</button>
          </div>
        </div>

        <div class="upload-options">
          <label class="checkbox-label">
            <input v-model="hasAnswerHint" type="checkbox" />
            含答案
          </label>
          <button
            class="btn-primary"
            :disabled="uploadFiles.length === 0 || uploading"
            @click="startParse"
          >
            {{ uploading ? '上传中...' : '🚀 开始解析' }}
          </button>
        </div>
      </div>

      <!-- Task List -->
      <div class="task-list-header">
        <span>任务列表</span>
        <button class="btn-icon" @click="refreshTasks" title="刷新">🔄</button>
      </div>
      <div class="task-list">
        <div
          v-for="task in epStore.sortedTasks"
          :key="task.taskUuid"
          class="task-card"
          :class="{ selected: task.taskUuid === epStore.activeTaskUuid }"
          @click="onSelectTask(task.taskUuid)"
        >
          <div class="task-header">
            <span class="task-uuid">{{ task.taskUuid.slice(0, 12) }}</span>
            <span class="task-status" :class="'status-' + task.status.toLowerCase()">
              {{ statusLabel(task.status) }}
            </span>
          </div>
          <div class="task-meta">
            <span>{{ task.fileCount ?? 0 }} 文件</span>
            <span>{{ task.questionCount ?? 0 }} 题</span>
            <span v-if="task.hasAnswerHint" class="tag-answer">含答案</span>
          </div>
          <!-- Progress bar -->
          <div
            v-if="['PROCESSING', 'OCR_PROCESSING', 'SPLITTING', 'GENERATING', 'PENDING'].includes(task.status) && task.progress !== null"
            class="progress-bar"
          >
            <div class="progress-fill" :style="{ width: (task.progress ?? 0) + '%' }"></div>
          </div>
          <div v-if="task.errorMsg" class="task-error">{{ task.errorMsg }}</div>
        </div>
        <div v-if="epStore.sortedTasks.length === 0" class="empty-list">暂无任务</div>
      </div>

      <!-- Log -->
      <div class="ep-log">
        <div class="log-header">
          <span>解析日志</span>
        </div>
        <div class="log-list">
          <div v-for="(l, idx) in epStore.recentLogs" :key="idx" class="log-item">
            <span class="log-ts">{{ l.ts }}</span>
            <span class="log-msg">{{ l.msg }}</span>
          </div>
        </div>
      </div>
    </aside>

    <!-- ───── Main ───── -->
    <main class="ep-main">
      <!-- No task selected -->
      <div v-if="!epStore.activeTask" class="empty-state">
        <p>上传试卷文件或选择左侧任务开始解析</p>
      </div>

      <div v-else class="session-panel">
        <!-- Task header -->
        <div class="task-info-card">
          <div class="info-row">
            <span class="info-uuid">{{ epStore.activeTask.taskUuid.slice(0, 12) }}</span>
            <span class="task-status" :class="'status-' + epStore.activeTask.status.toLowerCase()">
              {{ statusLabel(epStore.activeTask.status) }}
            </span>
          </div>
          <div class="info-row">
            <span>{{ epStore.activeTask.fileCount ?? 0 }} 文件</span>
            <span>{{ epStore.activeTask.questionCount ?? 0 }} 题</span>
            <span v-if="epStore.activeTask.hasAnswerHint" class="tag-answer">含答案</span>
          </div>
          <div class="info-actions">
            <button
              v-if="hasPendingQuestions"
              class="btn-primary"
              @click="confirmAllAction"
            >
              ✅ 全部确认入库
            </button>
            <button
              class="btn-danger"
              @click="deleteTaskAction"
            >
              🗑 删除任务
            </button>
          </div>
        </div>

        <!-- Bubble Navigation -->
        <div v-if="epStore.activeQuestions.length > 0" class="bubble-nav">
          <button
            v-for="q in epStore.activeQuestions"
            :key="q.seqNo"
            class="bubble"
            :class="{
              active: q.seqNo === epStore.activeSeqNo,
              [bubbleClass(q)]: true
            }"
            @click="epStore.selectQuestion(q.seqNo)"
            :title="`第${q.seqNo}题 - ${q.confirmStatus}`"
          >
            {{ q.seqNo }}
          </button>
        </div>

        <!-- Question Focus -->
        <div v-if="epStore.activeQuestion && focusData" class="question-focus">
          <!-- Status bar -->
          <div class="focus-status">
            <span class="focus-seq">第 {{ epStore.activeSeqNo }} 题</span>
            <span v-if="epStore.activeQuestion.questionType" class="focus-type">
              {{ epStore.activeQuestion.questionType }}
            </span>
            <span class="focus-stage-badge" :class="'fs-' + focusData.focusStage.toLowerCase().replace('_', '-')">
              {{ focusStageLabel(focusData.focusStage) }}
            </span>

            <!-- Action buttons -->
            <div class="focus-actions">
              <button class="btn-sm" @click="epStore.prevQuestion()" title="Alt+←">◀ 上一题</button>
              <button class="btn-sm" @click="epStore.nextQuestion()" title="Alt+→">下一题 ▶</button>

              <!-- Context actions -->
              <template v-if="focusData.focusStage === 'PREVIEW' || focusData.focusStage === 'READY'">
                <button class="btn-primary btn-sm" @click="confirmSingleAction">确认入库</button>
                <button class="btn-sm" @click="enterEditStem">编辑题干</button>
                <button class="btn-sm" @click="enterEditAnswer">编辑答案</button>
                <button class="btn-sm" @click="skipAction">跳过</button>
              </template>
              <template v-if="focusData.focusStage === 'ERROR'">
                <button class="btn-sm" @click="enterEditStem">编辑题干</button>
                <button class="btn-sm" @click="skipAction">跳过</button>
              </template>
              <template v-if="focusData.focusStage === 'EDITING_STEM'">
                <button class="btn-primary btn-sm" @click="saveStemEdit">保存题干</button>
                <button class="btn-sm" @click="cancelEdit">取消</button>
              </template>
              <template v-if="focusData.focusStage === 'EDITING_ANSWER'">
                <button class="btn-primary btn-sm" @click="saveAnswerEdit">保存答案</button>
                <button class="btn-sm" @click="cancelEdit">取消</button>
              </template>
              <template v-if="focusData.focusStage === 'SKIPPED'">
                <button class="btn-sm" @click="unskipAction">恢复</button>
              </template>
            </div>
          </div>

          <!-- Content area -->
          <div class="focus-content">
            <!-- PREVIEW / READY / CONFIRMED / ERROR -->
            <template v-if="['PREVIEW', 'READY', 'CONFIRMED', 'SKIPPED', 'ERROR'].includes(focusData.focusStage)">
              <div class="preview-row">
                <div class="preview-section">
                  <h4>题干</h4>
                  <LatexPreview
                    :xml="epStore.activeQuestion.stemXml ?? epStore.activeQuestion.rawStemText ?? ''"
                    placeholder="无题干数据"
                    :image-resolver="resolveEpImage"
                  />
                </div>
                <div class="preview-section">
                  <h4>答案</h4>
                  <LatexPreview
                    :xml="epStore.activeQuestion.answerXml ?? epStore.activeQuestion.rawAnswerText ?? ''"
                    placeholder="无答案数据"
                    mode="answer"
                    :image-resolver="resolveEpImage"
                  />
                </div>
              </div>

              <!-- Tags & Difficulty (non-editing views) -->
              <div v-if="focusData.focusStage !== 'EDITING_STEM' && focusData.focusStage !== 'EDITING_ANSWER'" class="focus-meta">
                <!-- Question type -->
                <div class="meta-row">
                  <label class="meta-label">题型</label>
                  <select
                    v-model="epQuestionType"
                    class="meta-select"
                    :disabled="focusData.focusStage === 'CONFIRMED'"
                    @change="onEpTypeChange"
                  >
                    <option value="">自动</option>
                    <option v-for="[k, v] in questionTypeEntries()" :key="k" :value="k">{{ v }}</option>
                  </select>
                </div>

                <TagSection
                  ref="epTagRef"
                  :main-tags="parsedMainTags"
                  :secondary-tags="parsedSecondaryTags"
                  :readonly="focusData.focusStage === 'CONFIRMED'"
                  @change="onEpTagsChange"
                />

                <DifficultySlider
                  :model-value="epDifficulty"
                  :readonly="focusData.focusStage === 'CONFIRMED'"
                  @update:model-value="onEpDifficultyChange"
                />
              </div>
            </template>

            <!-- EDITING_STEM -->
            <template v-if="focusData.focusStage === 'EDITING_STEM'">
              <div class="edit-row">
                <div class="edit-section">
                  <StemEditor
                    :model-value="stemDraft"
                    root-tag="stem"
                    :image-resolver="resolveEpImage"
                    @update:model-value="stemDraft = $event"
                  />
                </div>
                <div class="edit-section">
                  <LatexPreview
                    :xml="stemDraft"
                    placeholder="题干预览"
                    :image-resolver="resolveEpImage"
                  />
                </div>
              </div>
            </template>

            <!-- EDITING_ANSWER -->
            <template v-if="focusData.focusStage === 'EDITING_ANSWER'">
              <div class="preview-section readonly-stem">
                <h4>题干 (只读)</h4>
                <LatexPreview
                  :xml="epStore.activeQuestion.stemXml ?? epStore.activeQuestion.rawStemText ?? ''"
                  compact
                  :image-resolver="resolveEpImage"
                />
              </div>
              <div class="edit-row">
                <div class="edit-section">
                  <StemEditor
                    :model-value="answerDraft"
                    root-tag="answer"
                    :image-resolver="resolveEpImage"
                    @update:model-value="answerDraft = $event"
                  />
                </div>
                <div class="edit-section">
                  <LatexPreview
                    :xml="answerDraft"
                    placeholder="答案预览"
                    mode="answer"
                    :image-resolver="resolveEpImage"
                  />
                </div>
              </div>
            </template>
          </div>
        </div>

        <div v-else-if="epStore.activeQuestions.length === 0" class="empty-state small">
          <p>暂无解析结果，等待解析中...</p>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import StemEditor from '@/components/StemEditor.vue'
import LatexPreview from '@/components/LatexPreview.vue'
import TagSection from '@/components/TagSection.vue'
import DifficultySlider from '@/components/DifficultySlider.vue'
import { useAuthStore } from '@/stores/auth'
import { useExamParseStore, type FocusStage, type FocusData } from '@/stores/examParse'
import { useNotificationStore } from '@/stores/notification'
import { questionTypeEntries } from '@/lib/questionType'
import type { ExamParseTask, ExamParseQuestion } from '@/api/types'

const auth = useAuthStore()
const epStore = useExamParseStore()
const notif = useNotificationStore()

// ── Upload ──

const fileInputRef = ref<HTMLInputElement | null>(null)
const uploadFiles = ref<File[]>([])
const hasAnswerHint = ref(false)
const isDragOver = ref(false)
const uploading = ref(false)

function onUploadClick() {
  fileInputRef.value?.click()
}

function onFileSelect(ev: Event) {
  const input = ev.target as HTMLInputElement
  if (input.files) {
    uploadFiles.value = [...uploadFiles.value, ...Array.from(input.files)]
    input.value = ''
  }
}

function onDrop(ev: DragEvent) {
  isDragOver.value = false
  if (ev.dataTransfer?.files) {
    uploadFiles.value = [...uploadFiles.value, ...Array.from(ev.dataTransfer.files)]
  }
}

function removeFile(idx: number) {
  uploadFiles.value.splice(idx, 1)
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function startParse() {
  if (uploadFiles.value.length === 0) return
  uploading.value = true
  try {
    // Electron: need file paths, not File objects.
    // In the Electron context, the files have a `path` property.
    const paths = uploadFiles.value
      .map((f) => (f as any).path as string)
      .filter((p): p is string => !!p)
    if (paths.length === 0) {
      notif.log('无法读取文件路径，请重新选择文件')
      return
    }
    await epStore.createTask(auth.token, paths, hasAnswerHint.value)
    uploadFiles.value = []
    hasAnswerHint.value = false
    notif.log('解析任务已提交')
  } catch (err: any) {
    notif.log(`上传失败: ${err.message ?? err}`)
  } finally {
    uploading.value = false
  }
}

// ── Task list ──

async function refreshTasks() {
  await epStore.refreshTaskList(auth.token)
}

async function onSelectTask(taskUuid: string) {
  epStore.selectTask(taskUuid)
  await epStore.refreshQuestions(auth.token, taskUuid)
  // Auto-select first question
  const qs = epStore.activeQuestions
  if (qs.length > 0) {
    epStore.selectQuestion(qs[0].seqNo)
  }
}

// ── Focus data ──

const focusData = computed((): FocusData | null => epStore.activeFocus)

// ── Bubble class ──

function bubbleClass(q: ExamParseQuestion): string {
  if (!epStore.activeTaskUuid) return ''
  const focus = epStore.getFocus(epStore.activeTaskUuid, q.seqNo)
  if (!focus) return 'b-preview'
  const stage = focus.focusStage.toLowerCase().replace('_', '-')
  return `b-${stage}`
}

// ── Question type ──

const epQuestionType = ref('')

watch(
  () => epStore.activeQuestion,
  (q) => {
    epQuestionType.value = q?.questionType ?? ''
  },
  { immediate: true }
)

async function onEpTypeChange() {
  const q = epStore.activeQuestion
  if (!q) return
  await epStore.updateQuestion(auth.token, epStore.activeTaskUuid, q.seqNo, {
    questionType: epQuestionType.value
  })
}

// ── Tags / Difficulty from parsed JSON ──

const epTagRef = ref<InstanceType<typeof TagSection> | null>(null)

const parsedMainTags = computed(() => {
  const q = epStore.activeQuestion
  if (!q?.mainTagsJson) return []
  try {
    return JSON.parse(q.mainTagsJson)
  } catch {
    return []
  }
})

const parsedSecondaryTags = computed(() => {
  const q = epStore.activeQuestion
  if (!q?.secondaryTagsJson) return []
  try {
    return JSON.parse(q.secondaryTagsJson)
  } catch {
    return []
  }
})

const epDifficulty = computed(() => {
  return epStore.activeQuestion?.difficulty ?? null
})

function onEpTagsChange(payload: { tags: string[] }) {
  // Store locally in focus data + will sync to backend on confirm
  const q = epStore.activeQuestion
  if (!q) return
  // Update question JSON directly
  epStore.updateQuestion(auth.token, epStore.activeTaskUuid, q.seqNo, {
    secondaryTagsJson: JSON.stringify(payload.tags)
  })
}

function onEpDifficultyChange(val: number | null) {
  const q = epStore.activeQuestion
  if (!q || val === null) return
  epStore.updateQuestion(auth.token, epStore.activeTaskUuid, q.seqNo, {
    difficulty: String(val)
  })
}

// ── Focus navigation ──

const hasPendingQuestions = computed(() =>
  epStore.activeQuestions.some((q) => q.confirmStatus === 'PENDING')
)

// ── Actions ──

async function confirmAllAction() {
  await epStore.confirmAll(auth.token, epStore.activeTaskUuid)
}

async function deleteTaskAction() {
  if (!confirm('确定删除该任务及所有解析结果？')) return
  await epStore.deleteTask(auth.token, epStore.activeTaskUuid)
}

async function confirmSingleAction() {
  const q = epStore.activeQuestion
  if (!q) return
  await epStore.confirmSingle(auth.token, epStore.activeTaskUuid, q.seqNo)
}

async function skipAction() {
  const q = epStore.activeQuestion
  if (!q) return
  await epStore.skipQuestion(auth.token, epStore.activeTaskUuid, q.seqNo)
}

async function unskipAction() {
  const q = epStore.activeQuestion
  if (!q) return
  await epStore.unskipQuestion(auth.token, epStore.activeTaskUuid, q.seqNo)
}

// ── Editing ──

const stemDraft = ref('')
const answerDraft = ref('')

function enterEditStem() {
  const q = epStore.activeQuestion
  if (!q) return
  stemDraft.value = q.stemXml ?? q.rawStemText ?? ''
  epStore.setFocusStage(epStore.activeTaskUuid, q.seqNo, 'EDITING_STEM')
}

function enterEditAnswer() {
  const q = epStore.activeQuestion
  if (!q) return
  answerDraft.value = q.answerXml ?? q.rawAnswerText ?? ''
  epStore.setFocusStage(epStore.activeTaskUuid, q.seqNo, 'EDITING_ANSWER')
}

function cancelEdit() {
  const q = epStore.activeQuestion
  if (!q) return
  epStore.setFocusStage(epStore.activeTaskUuid, q.seqNo, 'PREVIEW')
}

async function saveStemEdit() {
  const q = epStore.activeQuestion
  if (!q) return
  await epStore.updateQuestion(auth.token, epStore.activeTaskUuid, q.seqNo, {
    stemXml: stemDraft.value
  })
  epStore.setFocusStage(epStore.activeTaskUuid, q.seqNo, 'READY')
}

async function saveAnswerEdit() {
  const q = epStore.activeQuestion
  if (!q) return
  await epStore.updateQuestion(auth.token, epStore.activeTaskUuid, q.seqNo, {
    answerXml: answerDraft.value
  })
  epStore.setFocusStage(epStore.activeTaskUuid, q.seqNo, 'READY')
}

// ── Image resolver for EP ──

function resolveEpImage(refKey: string): string {
  const q = epStore.activeQuestion
  if (!q) return ''
  // Images stored in stemImagesJson / answerImagesJson
  try {
    const stemImages = q.stemImagesJson ? JSON.parse(q.stemImagesJson) : {}
    if (stemImages[refKey]) {
      const data = stemImages[refKey]
      return typeof data === 'string'
        ? data.startsWith('data:') ? data : `data:image/png;base64,${data}`
        : ''
    }
    const ansImages = q.answerImagesJson ? JSON.parse(q.answerImagesJson) : {}
    if (ansImages[refKey]) {
      const data = ansImages[refKey]
      return typeof data === 'string'
        ? data.startsWith('data:') ? data : `data:image/png;base64,${data}`
        : ''
    }
  } catch {
    // malformed JSON
  }
  return ''
}

// ── Keyboard shortcuts ──

function onKeyDown(ev: KeyboardEvent) {
  if (ev.altKey && ev.key === 'ArrowLeft') {
    ev.preventDefault()
    epStore.prevQuestion()
  } else if (ev.altKey && ev.key === 'ArrowRight') {
    ev.preventDefault()
    epStore.nextQuestion()
  } else if (ev.altKey && ev.key === 'e') {
    ev.preventDefault()
    if (focusData.value?.focusStage === 'PREVIEW' || focusData.value?.focusStage === 'READY') {
      enterEditStem()
    }
  } else if (ev.key === 'Escape') {
    if (focusData.value?.focusStage === 'EDITING_STEM' || focusData.value?.focusStage === 'EDITING_ANSWER') {
      cancelEdit()
    }
  }
}

onMounted(() => document.addEventListener('keydown', onKeyDown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeyDown))

// ── Helpers ──

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '等待中',
    OCR_PROCESSING: 'OCR识别中',
    SPLITTING: '拆题中',
    GENERATING: '生成中',
    PROCESSING: '解析中',
    SUCCESS: '已完成',
    PARTIAL_FAILED: '部分失败',
    CONFIRMED: '已确认',
    FAILED: '失败'
  }
  return map[status] ?? status
}

function focusStageLabel(stage: FocusStage): string {
  const map: Record<FocusStage, string> = {
    PREVIEW: '预览',
    EDITING_STEM: '编辑题干',
    EDITING_ANSWER: '编辑答案',
    READY: '待确认',
    CONFIRMED: '已确认',
    SKIPPED: '已跳过',
    ERROR: '错误'
  }
  return map[stage] ?? stage
}
</script>

<style scoped>
/* ===== Layout: grid 280px sidebar ===== */
.exam-parse-view {
  display: grid;
  grid-template-columns: 300px 1fr;
  height: 100%;
  overflow: hidden;
  gap: 0;
}

/* ===== Sidebar (light warm) ===== */
.ep-sidebar {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg-sidebar);
}

/* Upload panel */
.upload-panel {
  padding: 14px;
  border-bottom: 1px solid var(--color-border-light);
}

.upload-area {
  border: 2px dashed var(--color-border-strong);
  border-radius: var(--radius-md);
  padding: 22px 16px;
  text-align: center;
  cursor: pointer;
  transition: all var(--transition-fast);
  position: relative;
  background: var(--color-bg-card);
}

.upload-area:hover,
.upload-area.dragover {
  border-color: var(--color-accent);
  background: var(--color-bg-hover);
}

.file-input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.upload-hint {
  font-size: 0.85rem;
  color: var(--color-text-muted);
}

/* File chips */
.file-chips {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.file-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  background: var(--color-accent-muted);
  border-radius: 8px;
  font-size: 0.82rem;
  color: var(--color-accent);
}

.chip-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-size {
  color: var(--color-text-muted);
  font-size: 0.75rem;
}

.chip-remove {
  background: none;
  border: none;
  color: var(--color-text-muted);
  cursor: pointer;
  font-size: 0.95rem;
  padding: 0 2px;
  border-radius: 50%;
  transition: all var(--transition-fast);
}
.chip-remove:hover { color: var(--color-danger); background: var(--color-danger-bg); }

.upload-options {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
}

.checkbox-label {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
}

/* ===== Task list ===== */
.task-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-bottom: 1px solid var(--color-border-light);
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.btn-icon {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 0.9rem;
  padding: 2px 4px;
  color: var(--color-text-muted);
  transition: color var(--transition-fast);
}
.btn-icon:hover { color: var(--color-accent); }

.task-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-card {
  padding: 10px 12px;
  cursor: pointer;
  border: 1px solid var(--color-border-light);
  border-radius: 8px;
  background: var(--color-bg-card);
  transition: all var(--transition-fast);
}

.task-card:hover {
  border-color: var(--color-border-strong);
  box-shadow: var(--shadow-sm);
}

.task-card.selected {
  border-color: var(--color-accent);
  background: var(--color-accent-muted);
  box-shadow: inset 3px 0 0 var(--color-accent);
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.task-uuid {
  font-family: var(--font-mono);
  font-size: 0.82rem;
  color: var(--color-text-primary);
  font-weight: 600;
}

.task-status {
  font-size: 0.72rem;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  font-weight: 600;
}

.status-pending { background: var(--color-warning-bg); color: var(--color-warning); }
.status-processing { background: rgba(108, 92, 231, 0.10); color: var(--color-accent); }
.status-success { background: var(--color-success-bg); color: var(--color-success); }
.status-confirmed { background: rgba(108, 92, 231, 0.10); color: var(--color-accent); }
.status-failed { background: var(--color-danger-bg); color: var(--color-danger); }

.task-meta {
  font-size: 0.78rem;
  color: var(--color-text-muted);
  display: flex;
  gap: 8px;
}

.tag-answer {
  color: var(--color-accent);
  font-weight: 600;
}

/* Progress bar */
.progress-bar {
  margin-top: 6px;
  height: 5px;
  background: var(--color-bg-secondary);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--color-accent), #a29bfe);
  border-radius: 3px;
  transition: width 0.3s;
}

.task-error {
  font-size: 0.78rem;
  color: var(--color-danger);
  margin-top: 4px;
  padding: 4px 8px;
  background: var(--color-danger-bg);
  border-radius: 6px;
  border-left: 3px solid var(--color-danger);
}

/* ===== Log ===== */
.ep-log {
  max-height: 160px;
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--color-border-light);
}

.log-header {
  padding: 8px 14px;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--color-text-muted);
  border-bottom: 1px solid var(--color-border-light);
  text-transform: uppercase;
  letter-spacing: 0.6px;
}

.log-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 10px;
}

.log-item {
  font-size: 0.75rem;
  padding: 2px 0;
  color: var(--color-text-muted);
}

.log-ts {
  color: var(--color-text-muted);
  opacity: 0.6;
  margin-right: 6px;
  font-family: var(--font-mono);
}

/* ===== Main (light pane) ===== */
.ep-main {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  background: var(--color-bg-primary);
}

/* Empty state */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-muted);
  font-size: 1rem;
  gap: 10px;
}

.empty-state.small {
  height: 200px;
}

.session-panel {
  max-width: 1100px;
}

/* ===== Task info card ===== */
.task-info-card {
  padding: 16px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  margin-bottom: 16px;
  animation: fadeInUp 0.3s ease;
}

.info-row {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 4px;
  font-size: 0.88rem;
  color: var(--color-text-secondary);
}

.info-uuid {
  font-family: var(--font-mono);
  font-weight: 700;
  color: var(--color-accent);
  font-size: 0.92rem;
}

.info-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

/* ===== Bubble nav ===== */
.bubble-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-border-light);
  margin-bottom: 16px;
}

.bubble {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 2px solid transparent;
  background: var(--color-bg-panel);
  color: var(--color-text-muted);
  font-size: 0.85rem;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.18s ease;
  padding: 0;
  position: relative;
}

.bubble:hover { transform: scale(1.08); }

.bubble.b-preview { background: var(--color-bg-panel); color: var(--color-text-muted); }
.bubble.b-editing-stem,
.bubble.b-editing-answer { background: var(--color-warning-bg); color: var(--color-warning); border-color: var(--color-warning); }
.bubble.b-ready { background: rgba(108, 92, 231, 0.1); color: var(--color-accent); border-color: var(--color-accent); }
.bubble.b-confirmed { background: var(--color-success-bg); color: var(--color-success); border-color: var(--color-success); }
.bubble.b-skipped { background: var(--color-bg-panel); color: var(--color-text-muted); text-decoration: line-through; opacity: 0.5; }
.bubble.b-error { background: var(--color-danger-bg); color: var(--color-danger); border-color: var(--color-danger); }

.bubble.active {
  transform: scale(1.26);
  box-shadow: 0 0 0 3px var(--color-accent-glow), 0 2px 8px rgba(0,0,0,0.1);
  z-index: 2;
}

/* ===== Question focus ===== */
.question-focus {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  min-height: 0;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

/* Focus status bar */
.focus-status {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 14px 20px;
  background: var(--color-bg-panel);
  border-bottom: 1px solid var(--color-border-light);
}

.focus-seq {
  font-weight: 800;
  font-size: 1.1rem;
  color: var(--color-accent);
  font-family: var(--font-display);
}

.focus-type {
  display: inline-flex;
  align-items: center;
  font-size: 0.78rem;
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  font-weight: 600;
  background: var(--color-accent-muted);
  color: var(--color-accent);
  letter-spacing: 0.3px;
}

.focus-stage-badge {
  font-size: 0.78rem;
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  font-weight: 600;
}

.fs-preview { background: var(--color-bg-panel); color: var(--color-text-muted); }
.fs-editing-stem,
.fs-editing-answer { background: var(--color-warning-bg); color: var(--color-warning); }
.fs-ready { background: rgba(108, 92, 231, 0.1); color: var(--color-accent); }
.fs-confirmed { background: var(--color-success-bg); color: var(--color-success); }
.fs-skipped { background: var(--color-bg-panel); color: var(--color-text-muted); }
.fs-error { background: var(--color-danger-bg); color: var(--color-danger); }

.focus-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* ===== Focus content ===== */
.focus-content {
  flex: 1;
  padding: 18px 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.preview-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  min-height: 200px;
}

.preview-section {
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-panel);
}

.preview-section h4 {
  font-size: 0.72rem;
  color: var(--color-text-muted);
  margin-bottom: 6px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.readonly-stem {
  max-width: 100%;
  max-height: 180px;
  overflow-y: auto;
}

.edit-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  min-height: 320px;
}

.edit-section {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* Focus meta (tags, difficulty) */
.focus-meta {
  padding: 16px 20px 12px;
  border-top: 1px solid var(--color-border-light);
  background: var(--color-bg-panel);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.meta-label {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--color-text-muted);
  min-width: 48px;
  text-align: right;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.meta-select {
  padding: 5px 10px;
  background: var(--color-bg-input);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  color: var(--color-text-primary);
  font-size: 0.85rem;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}
.meta-select:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 2px var(--color-accent-glow);
  outline: none;
}

/* ===== Buttons ===== */
.btn-primary {
  padding: 7px 16px;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover));
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 0.88rem;
  font-weight: 600;
  transition: all var(--transition-fast);
  box-shadow: 0 2px 8px var(--color-accent-glow);
  letter-spacing: 0.3px;
}
.btn-primary:hover { transform: translateY(-1px); box-shadow: 0 4px 16px var(--color-accent-glow); }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }

.btn-danger {
  padding: 7px 16px;
  background: var(--color-danger-bg);
  color: var(--color-danger);
  border: 1px solid rgba(214, 48, 49, 0.2);
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 0.88rem;
  font-weight: 600;
  transition: all var(--transition-fast);
}
.btn-danger:hover { background: rgba(214, 48, 49, 0.15); }

.btn-sm {
  padding: 5px 12px;
  font-size: 0.82rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-weight: 500;
  transition: all var(--transition-fast);
}
.btn-sm:hover { border-color: var(--color-accent); color: var(--color-accent); }

@media (max-width: 1060px) {
  .preview-row,
  .edit-row {
    grid-template-columns: 1fr;
  }
}
</style>
