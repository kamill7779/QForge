<template>
  <div class="bank-view">
    <!-- ───── Sidebar ───── -->
    <aside class="bank-sidebar">
      <!-- Search -->
      <div class="filter-section">
        <input
          v-model="search"
          class="search-input"
          type="text"
          placeholder="搜索题干 / 标签..."
          @input="applyFilter"
        />
      </div>

      <!-- Filters -->
      <div class="filter-section">
        <label class="filter-label">年级</label>
        <select v-model="gradeFilter" class="filter-select" @change="applyFilter">
          <option value="">全部年级</option>
          <option
            v-for="opt in gradeOptions"
            :key="opt.tagCode"
            :value="opt.tagCode"
          >
            {{ opt.tagName }}
          </option>
        </select>
      </div>

      <div class="filter-section">
        <label class="filter-label">知识点</label>
        <select v-model="knowledgeFilter" class="filter-select" @change="applyFilter">
          <option value="">全部知识点</option>
          <option
            v-for="opt in knowledgeOptions"
            :key="opt.tagCode"
            :value="opt.tagCode"
          >
            {{ opt.tagName }}
          </option>
        </select>
      </div>

      <div class="filter-section">
        <label class="filter-label">难度</label>
        <select v-model="difficultyFilter" class="filter-select" @change="applyFilter">
          <option value="">全部难度</option>
          <option value="easy">简单</option>
          <option value="medium-easy">中等偏易</option>
          <option value="medium">中等</option>
          <option value="hard">较难</option>
          <option value="very-hard">极难</option>
          <option value="unset">未设置</option>
        </select>
      </div>

      <!-- Count -->
      <div class="bank-count">共 {{ bankList.length }} 题</div>

      <!-- List -->
      <div class="bank-list">
        <div
          v-for="entry in bankList"
          :key="entry.questionUuid"
          class="bank-card"
          :class="{ selected: entry.questionUuid === questionStore.bankSelectedUuid }"
          @click="onSelectBank(entry.questionUuid)"
        >
          <div class="card-header">
            <span class="card-uuid">{{ entry.questionUuid.slice(0, 8) }}</span>
            <span v-if="entry.difficulty !== null" class="diff-badge" :class="diffClass(entry.difficulty)">
              {{ diffLabel(entry.difficulty) }}
            </span>
          </div>
          <div class="card-info">
            <span>{{ entry.answerCount }} 个解法</span>
            <span v-if="entry.mainTags.length" class="card-tags">
              {{ entry.mainTags.map(t => t.tagName).join(' · ') }}
            </span>
          </div>
        </div>
        <div v-if="bankList.length === 0" class="empty-list">暂无题目</div>
      </div>
    </aside>

    <!-- ───── Detail Panel ───── -->
    <main class="bank-main">
      <div v-if="!bankEntry" class="empty-state">
        <p>从左侧选择一道题目查看详情</p>
      </div>

      <div v-else class="bank-detail">
        <h3 class="detail-title">{{ bankEntry.questionUuid.slice(0, 8) }}</h3>

        <!-- ── Stem Section ── -->
        <div class="detail-section">
          <div class="section-header">
            <h4>题干</h4>
            <button
              v-if="!stemEditing"
              class="btn-sm"
              @click="startEditStem"
            >
              编辑
            </button>
          </div>

          <!-- Stem display -->
          <LatexPreview
            v-if="!stemEditing"
            :xml="bankEntry.stemText"
            :image-resolver="resolveStemImage"
          />

          <!-- Stem editor -->
          <div v-else class="edit-panel">
            <StemEditor
              ref="bankStemEditorRef"
              :model-value="stemEditDraft"
              root-tag="stem"
              :image-resolver="resolveStemImage"
              @update:model-value="stemEditDraft = $event"
            />
            <LatexPreview
              :xml="stemEditDraft"
              compact
              :image-resolver="resolveStemImage"
            />
            <div class="edit-actions">
              <button class="btn-primary btn-sm" @click="saveStemEdit">保存</button>
              <button class="btn-secondary btn-sm" @click="cancelStemEdit">取消</button>
            </div>
          </div>
        </div>

        <!-- ── Answer Section ── -->
        <div class="detail-section">
          <div class="section-header">
            <h4>答案</h4>
            <button
              v-if="!answerEditing"
              class="btn-sm"
              @click="startEditAnswer"
            >
              编辑
            </button>
            <button
              v-if="!answerEditing && !answerAdding"
              class="btn-sm"
              @click="startAddAnswer"
            >
              + 新增
            </button>
            <button
              v-if="!answerEditing && !answerAdding && bankEntry.answersServerData.length > 1"
              class="btn-sm danger"
              @click="deleteBankAnswer"
            >
              删除
            </button>
          </div>

          <!-- Answer tabs + display -->
          <AnswerTabNav
            v-if="bankEntry.answersLocal.length > 0 && !answerEditing && !answerAdding"
            :answers="bankEntry.answersLocal"
            :model-value="questionStore.bankAnswerIdx"
            @update:model-value="questionStore.bankAnswerIdx = $event"
          />
          <LatexPreview
            v-if="!answerEditing && !answerAdding"
            :xml="bankEntry.answersLocal[questionStore.bankAnswerIdx] ?? ''"
            placeholder="暂无答案"
            mode="answer"
            :image-resolver="resolveAnswerImage"
          />

          <!-- Answer editor -->
          <div v-if="answerEditing" class="edit-panel">
            <StemEditor
              ref="bankAnswerEditorRef"
              :model-value="answerEditDraft"
              root-tag="answer"
              :image-resolver="resolveAnswerImage"
              @update:model-value="answerEditDraft = $event"
            />
            <LatexPreview
              :xml="answerEditDraft"
              compact
              mode="answer"
              :image-resolver="resolveAnswerImage"
            />
            <div class="edit-actions">
              <button class="btn-primary btn-sm" @click="saveAnswerEdit">保存</button>
              <button class="btn-secondary btn-sm" @click="cancelAnswerEdit">取消</button>
            </div>
          </div>

          <!-- Answer add -->
          <div v-if="answerAdding" class="edit-panel">
            <StemEditor
              ref="bankAnswerAddRef"
              :model-value="answerAddDraft"
              root-tag="answer"
              :image-resolver="resolveAnswerImage"
              @update:model-value="answerAddDraft = $event"
            />
            <LatexPreview
              :xml="answerAddDraft"
              compact
              mode="answer"
              :image-resolver="resolveAnswerImage"
            />
            <div class="edit-actions">
              <button class="btn-primary btn-sm" @click="saveNewAnswer">保存</button>
              <button class="btn-secondary btn-sm" @click="cancelAddAnswer">取消</button>
            </div>
          </div>
        </div>

        <!-- ── Tags ── -->
        <div class="detail-section">
          <TagSection
            ref="bankTagRef"
            :main-tags="bankEntry.mainTags"
            :secondary-tags="bankEntry.secondaryTags"
            @change="onBankTagsChange"
          />
        </div>

        <!-- ── Difficulty ── -->
        <div class="detail-section">
          <DifficultySlider
            :model-value="bankEntry.difficulty"
            @update:model-value="onBankDifficultyChange"
          />
        </div>

        <!-- ── AI Analysis ── -->
        <div class="detail-section">
          <AiAnalysisPanel
            :pending="questionStore.bankAi.pending.has(bankEntry.questionUuid)"
            :result="questionStore.bankAi.lastResult?.questionUuid === bankEntry.questionUuid ? questionStore.bankAi.lastResult : null"
            @request-analysis="bankRequestAi"
            @apply-recommendation="bankApplyAi"
          />
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import StemEditor from '@/components/StemEditor.vue'
import LatexPreview from '@/components/LatexPreview.vue'
import TagSection from '@/components/TagSection.vue'
import DifficultySlider from '@/components/DifficultySlider.vue'
import AiAnalysisPanel from '@/components/AiAnalysisPanel.vue'
import AnswerTabNav from '@/components/AnswerTabNav.vue'
import { useAuthStore } from '@/stores/auth'
import { useQuestionStore, stageOf } from '@/stores/question'
import { useTagStore } from '@/stores/tag'
import { useNotificationStore } from '@/stores/notification'
import { toStemXmlPayload, toAnswerXmlPayload } from '@/lib/stemXml'
import { difficultyLevel } from '@/lib/difficulty'
import type { InlineImageEntry } from '@/api/types'

const auth = useAuthStore()
const questionStore = useQuestionStore()
const tagStore = useTagStore()
const notif = useNotificationStore()

// ── Filters ──

const search = ref('')
const gradeFilter = ref('')
const knowledgeFilter = ref('')
const difficultyFilter = ref('')

const gradeOptions = computed(() => {
  const cat = tagStore.mainCategories.find((c) => c.categoryCode === 'MAIN_GRADE')
  return cat?.options ?? []
})

const knowledgeOptions = computed(() => {
  const cat = tagStore.mainCategories.find((c) => c.categoryCode === 'MAIN_KNOWLEDGE')
  return cat?.options ?? []
})

/** Filtered bank list — only READY entries matching filters. */
const bankList = computed(() => {
  return questionStore.sortedEntries.filter((entry) => {
    if (stageOf(entry) !== 'COMPLETED') return false

    // Search
    if (search.value) {
      const q = search.value.toLowerCase()
      const inStem = entry.stemText.toLowerCase().includes(q)
      const inTags = entry.secondaryTags.some((t) => t.toLowerCase().includes(q))
      if (!inStem && !inTags) return false
    }

    // Grade
    if (gradeFilter.value) {
      const match = entry.mainTags.some((t) => t.tagCode === gradeFilter.value)
      if (!match) return false
    }

    // Knowledge
    if (knowledgeFilter.value) {
      const match = entry.mainTags.some((t) => t.tagCode === knowledgeFilter.value)
      if (!match) return false
    }

    // Difficulty
    if (difficultyFilter.value) {
      if (difficultyFilter.value === 'unset') {
        if (entry.difficulty !== null) return false
      } else {
        if (entry.difficulty === null) return false
        const level = difficultyLevel(entry.difficulty)
        if (level.key !== difficultyFilter.value) return false
      }
    }

    return true
  })
})

function applyFilter() {
  // Filters are reactive, no action needed
}

// ── Selection ──

const bankEntry = computed(() => questionStore.bankSelectedEntry)

async function onSelectBank(uuid: string) {
  questionStore.selectBankQuestion(uuid)
  cancelStemEdit()
  cancelAnswerEdit()
  cancelAddAnswer()
  const entry = questionStore.entries.get(uuid)
  if (entry && !entry.assetsLoaded) {
    try {
      await questionStore.fetchAssets(auth.token, uuid)
    } catch {
      // may not have assets
    }
  }
}

// ── Image Resolvers ──

function resolveStemImage(refKey: string): string {
  const entry = bankEntry.value
  if (!entry) return ''
  const data = entry.inlineImages[refKey]
  if (!data) return ''
  return data.startsWith('data:') ? data : `data:image/png;base64,${data}`
}

function resolveAnswerImage(refKey: string): string {
  const entry = bankEntry.value
  if (!entry) return ''
  const data = entry.answerImages[refKey] ?? entry.inlineImages[refKey]
  if (!data) return ''
  return data.startsWith('data:') ? data : `data:image/png;base64,${data}`
}

// ── Stem Editing ──

const stemEditing = ref(false)
const stemEditDraft = ref('')
const bankStemEditorRef = ref<InstanceType<typeof StemEditor> | null>(null)

function startEditStem() {
  if (!bankEntry.value) return
  stemEditDraft.value = bankEntry.value.stemText
  stemEditing.value = true
}

function cancelStemEdit() {
  stemEditing.value = false
  stemEditDraft.value = ''
}

async function saveStemEdit() {
  const entry = bankEntry.value
  if (!entry) return

  const stemXml = toStemXmlPayload(stemEditDraft.value)
  if (!stemXml) {
    notif.log('题干内容无效')
    return
  }

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

  stemEditing.value = false
  notif.log(`更新题干 ${entry.questionUuid.slice(0, 8)}`)
}

// ── Answer Editing ──

const answerEditing = ref(false)
const answerEditDraft = ref('')
const bankAnswerEditorRef = ref<InstanceType<typeof StemEditor> | null>(null)

function startEditAnswer() {
  const entry = bankEntry.value
  if (!entry) return
  const idx = questionStore.bankAnswerIdx
  answerEditDraft.value = entry.answersLocal[idx] ?? ''
  answerEditing.value = true
}

function cancelAnswerEdit() {
  answerEditing.value = false
  answerEditDraft.value = ''
}

async function saveAnswerEdit() {
  const entry = bankEntry.value
  if (!entry) return

  const answerXml = toAnswerXmlPayload(answerEditDraft.value)
  if (!answerXml) {
    notif.log('答案内容无效')
    return
  }

  const idx = questionStore.bankAnswerIdx
  const serverData = entry.answersServerData[idx]
  if (!serverData) return

  const inlineImages: Record<string, InlineImageEntry> = {}
  for (const [key, data] of Object.entries(entry.answerImages)) {
    inlineImages[key] = {
      imageData: data.startsWith('data:') ? data.split(',')[1] ?? data : data
    }
  }

  await questionStore.updateAnswer(auth.token, entry.questionUuid, serverData.answerUuid, {
    latexText: answerXml,
    inlineImages: Object.keys(inlineImages).length > 0 ? inlineImages : undefined
  })

  answerEditing.value = false
  notif.log(`更新答案 ${entry.questionUuid.slice(0, 8)}`)
}

// ── Answer Add ──

const answerAdding = ref(false)
const answerAddDraft = ref('')
const bankAnswerAddRef = ref<InstanceType<typeof StemEditor> | null>(null)

function startAddAnswer() {
  answerAddDraft.value = ''
  answerAdding.value = true
}

function cancelAddAnswer() {
  answerAdding.value = false
  answerAddDraft.value = ''
}

async function saveNewAnswer() {
  const entry = bankEntry.value
  if (!entry) return

  const answerXml = toAnswerXmlPayload(answerAddDraft.value)
  if (!answerXml) {
    notif.log('答案内容无效')
    return
  }

  await questionStore.addAnswer(auth.token, entry.questionUuid, {
    latexText: answerXml
  })

  answerAdding.value = false
  answerAddDraft.value = ''
  questionStore.bankAnswerIdx = entry.answersLocal.length - 1
  notif.log(`新增答案 ${entry.questionUuid.slice(0, 8)}`)
}

async function deleteBankAnswer() {
  const entry = bankEntry.value
  if (!entry || entry.answersServerData.length <= 1) return

  const idx = questionStore.bankAnswerIdx
  const serverData = entry.answersServerData[idx]
  if (!serverData) return

  await questionStore.deleteAnswer(auth.token, entry.questionUuid, serverData.answerUuid)
  questionStore.bankAnswerIdx = Math.max(0, idx - 1)
}

// ── Tags ──

const bankTagRef = ref<InstanceType<typeof TagSection> | null>(null)

async function onBankTagsChange(payload: { tags: string[] }) {
  const entry = bankEntry.value
  if (!entry || !payload.tags.length) return
  await questionStore.updateTags(auth.token, entry.questionUuid, payload.tags)
}

// ── Difficulty ──

let bankDiffTimer: ReturnType<typeof setTimeout> | null = null

function onBankDifficultyChange(val: number | null) {
  const entry = bankEntry.value
  if (!entry || val === null) return
  entry.difficulty = val

  if (bankDiffTimer) clearTimeout(bankDiffTimer)
  bankDiffTimer = setTimeout(async () => {
    await questionStore.updateDifficulty(auth.token, entry.questionUuid, val)
  }, 500)
}

// ── AI ──

async function bankRequestAi() {
  if (!bankEntry.value) return
  await questionStore.requestAiAnalysis(auth.token, bankEntry.value.questionUuid, 'bank')
}

async function bankApplyAi() {
  const entry = bankEntry.value
  const ai = questionStore.bankAi
  if (!entry || !ai.lastResult || !ai.taskUuid) return
  await questionStore.applyAiRecommendation(
    auth.token,
    entry.questionUuid,
    ai.taskUuid,
    ai.lastResult.suggestedTags ?? undefined,
    ai.lastResult.suggestedDifficulty ?? undefined
  )
}

// ── Helpers ──

function diffLabel(d: number): string {
  return difficultyLevel(d).label
}

function diffClass(d: number): string {
  return difficultyLevel(d).cssClass
}
</script>

<style scoped>
.bank-view {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* ── Sidebar ── */

.bank-sidebar {
  width: 300px;
  min-width: 300px;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-sidebar);
  border-right: 1px solid var(--color-border);
  overflow: hidden;
}

.filter-section {
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-border);
}

.filter-label {
  display: block;
  font-size: 11px;
  color: var(--color-text-muted);
  margin-bottom: 4px;
}

.search-input,
.filter-select {
  width: 100%;
  padding: 6px 8px;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  color: var(--color-text-primary);
  font-size: 13px;
}

.bank-count {
  padding: 6px 12px;
  font-size: 12px;
  color: var(--color-text-muted);
  border-bottom: 1px solid var(--color-border);
}

.bank-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

.bank-card {
  padding: 8px 12px;
  cursor: pointer;
  border-bottom: 1px solid var(--color-border-light, rgba(255,255,255,0.04));
  transition: background 0.15s;
}

.bank-card:hover {
  background: rgba(255, 255, 255, 0.04);
}

.bank-card.selected {
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

.diff-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
}

.d-easy { background: var(--color-difficulty-easy, #22c55e); color: #fff; }
.d-medium-easy { background: var(--color-difficulty-medium-easy, #84cc16); color: #000; }
.d-medium { background: var(--color-difficulty-medium, #eab308); color: #000; }
.d-hard { background: var(--color-difficulty-hard, #f97316); color: #fff; }
.d-very-hard { background: var(--color-difficulty-very-hard, #ef4444); color: #fff; }

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

.bank-main {
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

.bank-detail {
  max-width: 900px;
}

.detail-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  font-family: var(--font-mono);
  color: var(--color-text-primary);
}

.detail-section {
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.section-header h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.edit-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.edit-actions {
  display: flex;
  gap: 8px;
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

.btn-sm {
  padding: 3px 10px;
  font-size: 12px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.btn-sm:hover { background: rgba(255,255,255,0.06); }
.btn-sm.danger { color: var(--color-danger, #ef4444); border-color: var(--color-danger, #ef4444); }
</style>
