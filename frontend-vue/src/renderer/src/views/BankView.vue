<template>
  <div class="bank-view">
    <!-- ───── Sidebar ───── -->
    <aside class="bank-sidebar">
      <!-- Search + Filters Card -->
      <div class="filter-section">
        <input
          v-model="search"
          class="search-input"
          type="text"
          placeholder="搜索题干 / 标签..."
          @input="applyFilter"
        />

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

      <!-- List Card -->
      <div class="bank-list-card">
        <div class="bank-count">共 {{ bankList.length }} 题</div>
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
      </div><!-- end bank-list-card -->
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
            :render-key="bankStemImageVersion"
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
              :render-key="bankStemImageVersion"
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
            :render-key="bankAnswerImageVersion"
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
              :render-key="bankAnswerImageVersion"
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
              :render-key="bankAnswerImageVersion"
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
            :disabled="bankEntry.answersServerData.length === 0"
            disabled-tip="该题目尚无答案，无法进行AI分析"
            @request-analysis="bankRequestAi"
            @apply-recommendation="bankApplyAi"
            @cancel-analysis="bankCancelAi"
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
import { toStemXmlPayload, toAnswerXmlPayload, isEmptyXmlContent } from '@/lib/stemXml'
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

// Lightweight image-version signals for LatexPreview :render-key
const bankStemImageVersion = computed(() => Object.keys(bankEntry.value?.inlineImages ?? {}).length)
const bankAnswerImageVersion = computed(() =>
  Object.keys(bankEntry.value?.answerImages ?? {}).length +
  Object.keys(bankEntry.value?.inlineImages ?? {}).length
)

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
  if (!stemXml || isEmptyXmlContent(stemXml, 'stem')) {
    notif.log('题干内容不能为空')
    return
  }

  const inlineImages: Record<string, InlineImageEntry> = {}
  for (const [key, data] of Object.entries(entry.inlineImages)) {
    inlineImages[key] = {
      imageData: data.startsWith('data:') ? data.split(',')[1] ?? data : data
    }
  }

  try {
    console.log('[bank/saveStem] 提交', entry.questionUuid.slice(0, 8))
    await questionStore.confirmStem(auth.token, entry.questionUuid, {
      stemXml,
      inlineImages: Object.keys(inlineImages).length > 0 ? inlineImages : undefined
    })
    stemEditing.value = false
    notif.log(`更新题干 ${entry.questionUuid.slice(0, 8)}`)
  } catch (e: any) {
    console.error('[bank/saveStem] 失败', e)
    notif.log(`更新题干失败: ${e?.message || e}`)
  }
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

  try {
    console.log('[bank/saveAnswer] 提交', entry.questionUuid.slice(0, 8))
    await questionStore.updateAnswer(auth.token, entry.questionUuid, serverData.answerUuid, {
      latexText: answerXml,
      inlineImages: Object.keys(inlineImages).length > 0 ? inlineImages : undefined
    })
    answerEditing.value = false
    notif.log(`更新答案 ${entry.questionUuid.slice(0, 8)}`)
  } catch (e: any) {
    console.error('[bank/saveAnswer] 失败', e)
    notif.log(`更新答案失败: ${e?.message || e}`)
  }
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
  if (!answerAddDraft.value || !answerAddDraft.value.trim()) {
    notif.log('请先输入答案内容')
    return
  }

  const answerXml = toAnswerXmlPayload(answerAddDraft.value)
  if (!answerXml) {
    notif.log('答案内容无效')
    return
  }
  if (isEmptyXmlContent(answerXml, 'answer')) {
    notif.log('答案内容不能为空')
    return
  }

  try {
    console.log('[bank/addAnswer] 提交', entry.questionUuid.slice(0, 8))
    await questionStore.addAnswer(auth.token, entry.questionUuid, {
      latexText: answerXml
    })
    answerAdding.value = false
    answerAddDraft.value = ''
    questionStore.bankAnswerIdx = entry.answersLocal.length - 1
    notif.log(`新增答案 ${entry.questionUuid.slice(0, 8)}`)
  } catch (e: any) {
    console.error('[bank/addAnswer] 失败', e)
    notif.log(`新增答案失败: ${e?.message || e}`)
  }
}

async function deleteBankAnswer() {
  const entry = bankEntry.value
  if (!entry || entry.answersServerData.length <= 1) return

  const idx = questionStore.bankAnswerIdx
  const serverData = entry.answersServerData[idx]
  if (!serverData) return

  try {
    await questionStore.deleteAnswer(auth.token, entry.questionUuid, serverData.answerUuid)
    questionStore.bankAnswerIdx = Math.max(0, idx - 1)
  } catch (e: any) {
    console.error('[bank/deleteAnswer] 失败', e)
    notif.log(`删除答案失败: ${e?.message || e}`)
  }
}

// ── Tags ──

const bankTagRef = ref<InstanceType<typeof TagSection> | null>(null)

async function onBankTagsChange(payload: { tags: string[] }) {
  const entry = bankEntry.value
  if (!entry || !payload.tags.length) return
  try {
    await questionStore.updateTags(auth.token, entry.questionUuid, payload.tags)
  } catch (e: any) {
    console.error('[bank/tags] 失败', e)
    notif.log(`更新标签失败: ${e?.message || e}`)
  }
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
  if (bankEntry.value.answersServerData.length === 0) {
    notif.log('该题目尚无答案，无法进行AI分析')
    return
  }
  try {
    await questionStore.requestAiAnalysis(auth.token, bankEntry.value.questionUuid, 'bank')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : 'AI分析请求失败'
    notif.log(msg)
  }
}

function bankCancelAi() {
  if (!bankEntry.value) return
  questionStore.cancelAiAnalysis(bankEntry.value.questionUuid, 'bank')
}

async function bankApplyAi() {
  const entry = bankEntry.value
  const ai = questionStore.bankAi
  if (!entry || !ai.lastResult || !ai.taskUuid) return
  try {
    await questionStore.applyAiRecommendation(
      auth.token,
      entry.questionUuid,
      ai.taskUuid,
      ai.lastResult.suggestedTags ?? undefined,
      ai.lastResult.suggestedDifficulty ?? undefined
    )
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '采纳AI推荐失败'
    notif.log(msg)
  }
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
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 0;
  overflow: hidden;
}

/* ── Sidebar (light warm) ── */

.bank-sidebar {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0;
  overflow: hidden;
  background: var(--color-bg-sidebar);
  border-right: 1px solid var(--color-border);
}

.filter-section {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-bottom: 1px solid var(--color-border-light);
}

.filter-label {
  display: block;
  font-size: 12px;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.6px;
  font-weight: 600;
  margin-bottom: 2px;
}

.search-input,
.filter-select {
  width: 100%;
  padding: 8px 12px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  color: var(--color-text-primary);
  font-size: 13px;
  outline: none;
  transition: all var(--transition-fast);
}

.search-input:focus,
.filter-select:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px var(--color-accent-muted);
}

.filter-select option {
  background: var(--color-bg-card);
  color: var(--color-text-primary);
}

.bank-list-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 0;
}

.bank-count {
  font-size: 12px;
  color: var(--color-text-muted);
  padding: 10px 16px;
  border-bottom: 1px solid var(--color-border-light);
}

.bank-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px;
}

.bank-card {
  border: 1px solid var(--color-border-light);
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--color-bg-card);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.bank-card:hover {
  border-color: var(--color-border-strong);
  box-shadow: var(--shadow-sm);
}

.bank-card.selected {
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

.diff-badge {
  display: inline-block;
  min-width: 48px;
  text-align: center;
  border-radius: var(--radius-pill);
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 600;
}

.d-easy { background: var(--color-d-easy-bg); color: var(--color-d-easy); }
.d-medium-easy { background: var(--color-d-medium-easy-bg); color: var(--color-d-medium-easy); }
.d-medium { background: var(--color-d-medium-bg); color: var(--color-d-medium); }
.d-hard { background: var(--color-d-hard-bg); color: var(--color-d-hard); }
.d-very-hard { background: var(--color-d-very-hard-bg); color: var(--color-d-very-hard); }

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

/* ── Main (right pane) ── */

.bank-main {
  min-height: 0;
  overflow: hidden;
  background: var(--color-bg-primary);
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-muted);
  font-size: 14px;
  text-align: center;
  padding: 80px 20px;
}

.bank-detail {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  padding: 20px;
  height: calc(100% - 32px);
  margin: 16px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  animation: fadeInUp 0.3s ease;
}

.detail-title {
  font-size: 18px;
  font-weight: 700;
  font-family: var(--font-display);
  letter-spacing: 0.3px;
  margin: 0 0 16px;
  color: var(--color-text-primary);
}

.detail-section {
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--color-border-light);
}

.detail-section:last-child {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.section-header h4 {
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 1px;
  color: var(--color-text-muted);
  margin: 0;
}

.edit-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 8px;
}

.edit-actions {
  display: flex;
  gap: 8px;
  margin-top: 6px;
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

.btn-primary:hover { transform: translateY(-1px); box-shadow: 0 4px 16px var(--color-accent-glow); }
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

.btn-secondary:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: var(--color-accent-muted);
}

.btn-sm {
  padding: 5px 12px;
  border-radius: 8px;
  font-size: 12px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-weight: 500;
  transition: all var(--transition-fast);
}

.btn-sm:hover { border-color: var(--color-accent); color: var(--color-accent); }
.btn-sm.danger { color: var(--color-danger); border-color: var(--color-danger-bg); background: var(--color-danger-bg); }
.btn-sm.danger:hover { background: #fde3e3; }
</style>
