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
            v-if="task.status === 'PROCESSING' && task.progress !== null"
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
    const paths = uploadFiles.value.map((f) => (f as any).path as string)
    await epStore.createTask(auth.token, paths, hasAnswerHint.value)
    uploadFiles.value = []
    hasAnswerHint.value = false
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
    PROCESSING: '解析中',
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
.exam-parse-view {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* ── Sidebar ── */

.ep-sidebar {
  width: 320px;
  min-width: 320px;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-sidebar);
  border-right: 1px solid var(--color-border);
  overflow: hidden;
}

/* Upload */
.upload-panel {
  padding: 12px;
  border-bottom: 1px solid var(--color-border);
}

.upload-area {
  border: 2px dashed var(--color-border);
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
  position: relative;
}

.upload-area:hover,
.upload-area.dragover {
  border-color: var(--color-accent);
  background: rgba(var(--color-accent-rgb, 59, 130, 246), 0.06);
}

.file-input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.upload-hint {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.file-chips {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.file-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  background: var(--color-bg-secondary);
  border-radius: 4px;
  font-size: 12px;
}

.chip-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-primary);
}

.chip-size {
  color: var(--color-text-muted);
  font-size: 11px;
}

.chip-remove {
  background: none;
  border: none;
  color: var(--color-text-muted);
  cursor: pointer;
  font-size: 14px;
  padding: 0 2px;
}

.upload-options {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}

.checkbox-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

/* Task list */
.task-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-border);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.btn-icon {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  padding: 2px 4px;
}

.task-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
  min-height: 0;
}

.task-card {
  padding: 8px 12px;
  cursor: pointer;
  border-bottom: 1px solid var(--color-border-light, #e8eef8);
  transition: background 0.15s;
}

.task-card:hover { background: rgba(45, 108, 223, 0.04); }

.task-card.selected {
  background: rgba(var(--color-accent-rgb, 59, 130, 246), 0.15);
  border-left: 3px solid var(--color-accent);
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.task-uuid {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-primary);
}

.task-status {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
}

.status-pending { background: var(--color-warning-bg, #fff1d6); color: var(--color-warning, #8c6306); }
.status-processing { background: var(--color-info-bg, #e8f0ff); color: var(--color-info, #2f4f8e); }
.status-confirmed { background: var(--color-success-bg, #ddf4e8); color: var(--color-success, #176b3d); }
.status-failed { background: var(--color-danger-bg, #fde3e3); color: var(--color-danger, #af3535); }

.task-meta {
  font-size: 11px;
  color: var(--color-text-muted);
  display: flex;
  gap: 8px;
}

.tag-answer {
  color: var(--color-accent);
}

.progress-bar {
  margin-top: 4px;
  height: 3px;
  background: var(--color-bg-primary);
  border-radius: 2px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--color-accent);
  transition: width 0.3s;
}

.task-error {
  font-size: 11px;
  color: var(--color-danger, #ef4444);
  margin-top: 4px;
}

/* Log */
.ep-log {
  max-height: 160px;
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--color-border);
}

.log-header {
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-muted);
  border-bottom: 1px solid var(--color-border);
}

.log-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px;
}

.log-item {
  font-size: 11px;
  padding: 1px 0;
  color: var(--color-text-muted);
}

.log-ts {
  color: var(--color-text-muted);
  margin-right: 6px;
  font-family: var(--font-mono);
}

/* ── Main ── */

.ep-main {
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

.empty-state.small {
  height: 200px;
}

.session-panel {
  max-width: 1100px;
}

/* Task info card */
.task-info-card {
  padding: 12px;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  margin-bottom: 16px;
}

.info-row {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 4px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.info-uuid {
  font-family: var(--font-mono);
  font-weight: 600;
  color: var(--color-text-primary);
}

.info-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

/* Bubble nav */
.bubble-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 16px;
}

.bubble {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 2px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.bubble.active {
  border-width: 3px;
  font-weight: 700;
}

.bubble.b-preview { border-color: var(--color-text-muted); }
.bubble.b-editing-stem,
.bubble.b-editing-answer { border-color: var(--color-warning, #f59e0b); background: rgba(245, 158, 11, 0.1); }
.bubble.b-ready { border-color: var(--color-info, #3b82f6); background: rgba(59, 130, 246, 0.1); }
.bubble.b-confirmed { border-color: var(--color-success, #22c55e); background: rgba(34, 197, 94, 0.1); color: var(--color-success); }
.bubble.b-skipped { border-color: var(--color-text-muted); background: var(--color-bg-secondary); opacity: 0.5; }
.bubble.b-error { border-color: var(--color-danger, #ef4444); background: rgba(239, 68, 68, 0.1); color: var(--color-danger); }

/* Question focus */
.question-focus {
  /* nothing special */
}

.focus-status {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 12px;
}

.focus-seq {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.focus-type {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--color-bg-secondary);
  color: var(--color-text-secondary);
}

.focus-stage-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
}

.fs-preview { background: var(--color-bg-secondary); color: var(--color-text-secondary); }
.fs-editing-stem,
.fs-editing-answer { background: var(--color-warning-bg, #fff1d6); color: var(--color-warning, #8c6306); }
.fs-ready { background: var(--color-info-bg, #e8f0ff); color: var(--color-info, #2f4f8e); }
.fs-confirmed { background: var(--color-success-bg, #ddf4e8); color: var(--color-success, #176b3d); }
.fs-skipped { background: #e8eef8; color: var(--color-text-muted); }
.fs-error { background: var(--color-danger-bg, #fde3e3); color: var(--color-danger, #af3535); }

.focus-actions {
  margin-left: auto;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

/* Focus content */
.focus-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preview-row {
  display: flex;
  gap: 16px;
}

.preview-section {
  flex: 1;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-secondary);
}

.preview-section h4 {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: 8px;
}

.readonly-stem {
  max-width: 100%;
}

.edit-row {
  display: flex;
  gap: 16px;
}

.edit-section {
  flex: 1;
  min-width: 0;
}

.focus-meta {
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-secondary);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-label {
  font-size: 13px;
  color: var(--color-text-muted);
  min-width: 40px;
}

.meta-select {
  padding: 4px 8px;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  color: var(--color-text-primary);
  font-size: 13px;
}

/* ── Buttons ── */

.btn-primary {
  padding: 6px 14px;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover));
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 13px;
}

.btn-primary:hover { filter: brightness(0.95); }
.btn-primary:disabled { opacity: 0.55; cursor: not-allowed; }

.btn-danger {
  padding: 6px 14px;
  background: transparent;
  color: var(--color-danger, #ef4444);
  border: 1px solid var(--color-danger, #ef4444);
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}

.btn-danger:hover { background: rgba(239, 68, 68, 0.1); }

.btn-sm {
  padding: 3px 10px;
  font-size: 12px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.btn-sm:hover { background: rgba(45, 108, 223, 0.06); }
</style>
