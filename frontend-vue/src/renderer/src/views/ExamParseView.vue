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
/* ===== Layout: grid 340px sidebar ===== */
.exam-parse-view {
  display: grid;
  grid-template-columns: 340px 1fr;
  height: 100%;
  overflow: hidden;
  gap: 0;
}

/* ===== Sidebar ===== */
.ep-sidebar {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-right: 1px solid #ccd8ef;
  background: #f8faff;
}

/* Upload panel */
.upload-panel {
  padding: 14px;
  border-bottom: 1px solid #d2ddf1;
}

.upload-area {
  border: 2px dashed #bcc9dd;
  border-radius: 12px;
  padding: 22px 16px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
  position: relative;
  background: #fff;
}

.upload-area:hover,
.upload-area.dragover {
  border-color: var(--color-accent);
  background: rgba(45, 108, 223, 0.04);
}

.file-input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.upload-hint {
  font-size: 0.88rem;
  color: #6a7fa0;
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
  background: #edf2fb;
  border-radius: 8px;
  font-size: 0.82rem;
  color: #2a4a80;
}

.chip-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-size {
  color: #8a9bba;
  font-size: 0.75rem;
}

.chip-remove {
  background: none;
  border: none;
  color: #8a9bba;
  cursor: pointer;
  font-size: 0.95rem;
  padding: 0 2px;
  border-radius: 50%;
  transition: color 0.15s, background 0.15s;
}
.chip-remove:hover { color: #c45656; background: rgba(196, 86, 86, 0.1); }

.upload-options {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
}

.checkbox-label {
  font-size: 0.85rem;
  color: #3a5b97;
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
  border-bottom: 1px solid #d2ddf1;
  font-size: 0.88rem;
  font-weight: 700;
  color: #1f355c;
}

.btn-icon {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 0.9rem;
  padding: 2px 4px;
  color: #6a7fa0;
}
.btn-icon:hover { color: var(--color-accent); }

.task-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 10px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.task-card {
  padding: 10px 12px;
  cursor: pointer;
  border: 1px solid #d2ddf1;
  border-radius: 10px;
  background: #fbfdff;
  transition: all 0.15s;
}

.task-card:hover {
  background: #eef3ff;
  border-color: #b8c9e6;
}

.task-card.selected {
  border-color: var(--color-accent);
  background: #eaf1ff;
  box-shadow: 0 0 0 2px rgba(45, 108, 223, 0.12);
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
  color: #1f355c;
  font-weight: 600;
}

.task-status {
  font-size: 0.72rem;
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 600;
}

.status-pending { background: #fff1d6; color: #8c6306; }
.status-processing { background: #e8f0ff; color: #2f4f8e; }
.status-confirmed { background: #c8e6c9; color: #1b5e20; }
.status-failed { background: #fde3e3; color: #af3535; }

.task-meta {
  font-size: 0.78rem;
  color: #6a7fa0;
  display: flex;
  gap: 8px;
}

.tag-answer {
  color: var(--color-accent);
  font-weight: 600;
}

/* Progress bar: 7px with gradient */
.progress-bar {
  margin-top: 6px;
  height: 7px;
  background: #e8ecf1;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4a9eff, #2d6cdf);
  border-radius: 4px;
  transition: width 0.3s;
}

.task-error {
  font-size: 0.78rem;
  color: #c45656;
  margin-top: 4px;
  padding: 4px 8px;
  background: #fef0f0;
  border-radius: 6px;
  border-left: 3px solid #f56c6c;
}

/* ===== Log ===== */
.ep-log {
  max-height: 160px;
  display: flex;
  flex-direction: column;
  border-top: 1px solid #d2ddf1;
}

.log-header {
  padding: 8px 14px;
  font-size: 0.8rem;
  font-weight: 700;
  color: #6a7fa0;
  border-bottom: 1px solid #d2ddf1;
}

.log-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 10px;
}

.log-item {
  font-size: 0.75rem;
  padding: 2px 0;
  color: #6a7fa0;
}

.log-ts {
  color: #8a9bba;
  margin-right: 6px;
  font-family: var(--font-mono);
}

/* ===== Main ===== */
.ep-main {
  flex: 1;
  overflow-y: auto;
  padding: 14px 18px;
  background: var(--color-bg-primary);
}

/* Empty state */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #8a9bba;
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
  padding: 14px 16px;
  background: #ffffff;
  border: 1px solid #d2ddf1;
  border-radius: 10px;
  box-shadow: var(--shadow-soft);
  margin-bottom: 16px;
}

.info-row {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 4px;
  font-size: 0.88rem;
  color: #3a5b97;
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

/* ===== Bubble nav: 36px, 8px gap ===== */
.bubble-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 14px 0;
  border-bottom: 1px solid #d2ddf1;
  margin-bottom: 16px;
}

.bubble {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 2px solid transparent;
  background: #e8ecf1;
  color: #606d80;
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

.bubble:hover { transform: scale(1.08); opacity: 0.9; }

.bubble.b-preview { background: #e8ecf1; color: #606d80; }
.bubble.b-editing-stem,
.bubble.b-editing-answer { background: #fff4d4; color: #b07d10; border-color: #f0c040; }
.bubble.b-ready { background: #d4f5d0; color: #2e7d22; border-color: #81c784; }
.bubble.b-confirmed { background: #c8e6c9; color: #1b5e20; border-color: #66bb6a; }
.bubble.b-skipped { background: #f5f5f5; color: #bdbdbd; text-decoration: line-through; }
.bubble.b-error { background: #ffcdd2; color: #c62828; border-color: #e57373; }

.bubble.active {
  transform: scale(1.26);
  box-shadow: 0 0 0 3px rgba(45, 108, 223, 0.35), 0 2px 8px rgba(0,0,0,0.1);
  z-index: 2;
}

/* ===== Question focus ===== */
.question-focus {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  min-height: 0;
  background: #fff;
  border: 1px solid #d2ddf1;
  border-radius: 10px;
}

/* Focus status bar with gradient */
.focus-status {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 14px 20px;
  background: linear-gradient(135deg, #f8fafd 0%, #eef2f9 100%);
  border-bottom: 1px solid #d2ddf1;
}

.focus-seq {
  font-weight: 800;
  font-size: 1.1rem;
  color: var(--color-accent);
}

.focus-type {
  display: inline-flex;
  align-items: center;
  font-size: 0.8rem;
  padding: 2px 10px;
  border-radius: 12px;
  font-weight: 600;
  background: #e3ecf9;
  color: #2d6cdf;
  letter-spacing: 0.3px;
}

.focus-stage-badge {
  font-size: 0.78rem;
  padding: 2px 10px;
  border-radius: 999px;
  font-weight: 600;
}

.fs-preview { background: #e8ecf1; color: #606d80; }
.fs-editing-stem,
.fs-editing-answer { background: #fff1d6; color: #8c6306; }
.fs-ready { background: #e8f0ff; color: #2f4f8e; }
.fs-confirmed { background: #c8e6c9; color: #1b5e20; }
.fs-skipped { background: #f5f5f5; color: #bdbdbd; }
.fs-error { background: #fde3e3; color: #af3535; }

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
  border: 1px solid #d2ddf1;
  border-radius: 10px;
  background: #f7faff;
}

.preview-section h4 {
  font-size: 0.75rem;
  color: #8a9bba;
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
  border-top: 1px solid #d2ddf1;
  background: #fafbfd;
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
  color: #8a9bba;
  min-width: 48px;
  text-align: right;
}

.meta-select {
  padding: 5px 10px;
  background: #fff;
  border: 1px solid #c9d6ed;
  border-radius: 6px;
  color: #1f355c;
  font-size: 0.85rem;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.meta-select:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 2px rgba(45, 108, 223, 0.12);
  outline: none;
}

/* ===== Buttons (original .btn system) ===== */
.btn-primary {
  padding: 7px 16px;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover));
  color: #fff;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 0.88rem;
  font-weight: 600;
  transition: filter 0.15s, box-shadow 0.15s;
}
.btn-primary:hover { filter: brightness(0.95); box-shadow: 0 2px 8px rgba(45, 108, 223, 0.2); }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-danger {
  padding: 7px 16px;
  background: #fff1f1;
  color: #b73333;
  border: 1px solid #e8c0c0;
  border-radius: 10px;
  cursor: pointer;
  font-size: 0.88rem;
  font-weight: 600;
  transition: background 0.15s;
}
.btn-danger:hover { background: #fde3e3; }

.btn-sm {
  padding: 4px 12px;
  font-size: 0.82rem;
  border: 1px solid #c8d5eb;
  border-radius: 10px;
  background: #e9f0ff;
  color: #274989;
  cursor: pointer;
  font-weight: 500;
  transition: background 0.15s;
}
.btn-sm:hover { background: #dce7fc; }

@media (max-width: 1060px) {
  .preview-row,
  .edit-row {
    grid-template-columns: 1fr;
  }
}
</style>
