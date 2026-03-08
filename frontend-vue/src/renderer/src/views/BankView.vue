<template>
  <div class="bank-view">
    <!-- ───── Sidebar (Filters) ───── -->
    <aside class="bank-sidebar">
      <div class="filter-section">
        <input
          v-model="search"
          class="search-input"
          type="text"
          placeholder="搜索题干 / 标签..."
        />

        <label class="filter-label">年级</label>
        <select v-model="gradeFilter" class="filter-select">
          <option value="">全部年级</option>
          <option
            v-for="opt in gradeOptions"
            :key="opt.tagCode"
            :value="opt.tagCode"
          >{{ opt.tagName }}</option>
        </select>

        <label class="filter-label">知识点</label>
        <select v-model="knowledgeFilter" class="filter-select">
          <option value="">全部知识点</option>
          <option
            v-for="opt in knowledgeOptions"
            :key="opt.tagCode"
            :value="opt.tagCode"
          >{{ opt.tagName }}</option>
        </select>

        <label class="filter-label">难度</label>
        <select v-model="difficultyFilter" class="filter-select">
          <option value="">全部难度</option>
          <option value="easy">简单</option>
          <option value="medium-easy">中等偏易</option>
          <option value="medium">中等</option>
          <option value="hard">较难</option>
          <option value="very-hard">极难</option>
          <option value="unset">未设置</option>
        </select>
      </div>

      <!-- Stats -->
      <div class="filter-stats">
        <span>共 <strong>{{ bankList.length }}</strong> 题</span>
        <span v-if="selectedSet.size" class="selected-count">
          已选 <strong>{{ selectedSet.size }}</strong> 题
        </span>
      </div>
    </aside>

    <!-- ───── Main: Card List ───── -->
    <main class="bank-main" ref="mainScrollRef">
      <div v-if="bankList.length === 0" class="empty-state">
        <p>暂无符合条件的题目</p>
      </div>

      <div v-else class="card-list">
        <template v-for="(entry, idx) in bankList" :key="entry.questionUuid">
          <!-- Question Card -->
          <QuestionCard
            :entry="entry"
            :seq="idx + 1"
            :is-expanded="expandedUuid === entry.questionUuid"
            :is-selected="selectedSet.has(entry.questionUuid)"
            :image-resolver="(ref) => resolveImage(entry, ref)"
            @toggle-detail="toggleDetail(entry.questionUuid)"
            @edit="openEdit(entry.questionUuid)"
            @toggle-select="toggleSelect(entry.questionUuid)"
          />

          <!-- Inline Detail Panel (expanded below the card) -->
          <Transition name="detail-slide">
            <div
              v-if="expandedUuid === entry.questionUuid"
              class="inline-detail"
            >
              <div class="detail-inner">
                <!-- ── Stem ── -->
                <div class="detail-section">
                  <div class="section-header">
                    <h4>题干</h4>
                    <button v-if="!stemEditing" class="btn-sm" @click="startEditStem">编辑</button>
                  </div>
                  <LatexPreview
                    v-if="!stemEditing"
                    :xml="entry.stemText"
                    :image-resolver="(ref) => resolveImage(entry, ref)"
                    :render-key="bankStemImageVersion"
                  />
                  <div v-else class="edit-panel">
                    <StemEditor
                      ref="bankStemEditorRef"
                      :model-value="stemEditDraft"
                      root-tag="stem"
                      :image-resolver="(ref) => resolveImage(entry, ref)"
                      @update:model-value="stemEditDraft = $event"
                    />
                    <LatexPreview
                      :xml="stemEditDraft"
                      compact
                      :image-resolver="(ref) => resolveImage(entry, ref)"
                      :render-key="bankStemImageVersion"
                    />
                    <div class="edit-actions">
                      <button class="btn-primary btn-sm" @click="saveStemEdit">保存</button>
                      <button class="btn-secondary btn-sm" @click="cancelStemEdit">取消</button>
                    </div>
                  </div>
                </div>

                <!-- ── Answer ── -->
                <div class="detail-section">
                  <div class="section-header">
                    <h4>答案</h4>
                    <button v-if="!answerEditing" class="btn-sm" @click="startEditAnswer">编辑</button>
                    <button v-if="!answerEditing && !answerAdding" class="btn-sm" @click="startAddAnswer">+ 新增</button>
                    <button
                      v-if="!answerEditing && !answerAdding && entry.answersServerData.length > 1"
                      class="btn-sm danger"
                      @click="deleteBankAnswer"
                    >删除</button>
                  </div>

                  <AnswerTabNav
                    v-if="entry.answersLocal.length > 0 && !answerEditing && !answerAdding"
                    :answers="entry.answersLocal"
                    :model-value="questionStore.bankAnswerIdx"
                    @update:model-value="questionStore.bankAnswerIdx = $event"
                  />
                  <LatexPreview
                    v-if="!answerEditing && !answerAdding"
                    :xml="entry.answersLocal[questionStore.bankAnswerIdx] ?? ''"
                    placeholder="暂无答案"
                    mode="answer"
                    :image-resolver="(ref) => resolveAnswerImage(entry, ref)"
                    :render-key="bankAnswerImageVersion"
                  />

                  <div v-if="answerEditing" class="edit-panel">
                    <StemEditor
                      ref="bankAnswerEditorRef"
                      :model-value="answerEditDraft"
                      root-tag="answer"
                      :image-resolver="(ref) => resolveAnswerImage(entry, ref)"
                      @update:model-value="answerEditDraft = $event"
                    />
                    <LatexPreview
                      :xml="answerEditDraft"
                      compact
                      mode="answer"
                      :image-resolver="(ref) => resolveAnswerImage(entry, ref)"
                      :render-key="bankAnswerImageVersion"
                    />
                    <div class="edit-actions">
                      <button class="btn-primary btn-sm" @click="saveAnswerEdit">保存</button>
                      <button class="btn-secondary btn-sm" @click="cancelAnswerEdit">取消</button>
                    </div>
                  </div>

                  <div v-if="answerAdding" class="edit-panel">
                    <StemEditor
                      ref="bankAnswerAddRef"
                      :model-value="answerAddDraft"
                      root-tag="answer"
                      :image-resolver="(ref) => resolveAnswerImage(entry, ref)"
                      @update:model-value="answerAddDraft = $event"
                    />
                    <LatexPreview
                      :xml="answerAddDraft"
                      compact
                      mode="answer"
                      :image-resolver="(ref) => resolveAnswerImage(entry, ref)"
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
                    :main-tags="entry.mainTags"
                    :secondary-tags="entry.secondaryTags"
                    @change="onBankTagsChange"
                  />
                </div>

                <!-- ── Difficulty ── -->
                <div class="detail-section">
                  <DifficultySlider
                    :model-value="entry.difficulty"
                    @update:model-value="onBankDifficultyChange"
                  />
                </div>

                <!-- ── AI Analysis ── -->
                <div class="detail-section">
                  <AiAnalysisPanel
                    :pending="questionStore.bankAi.pending.has(entry.questionUuid)"
                    :result="questionStore.bankAi.lastResult?.questionUuid === entry.questionUuid ? questionStore.bankAi.lastResult : null"
                    :disabled="entry.answersServerData.length === 0"
                    disabled-tip="该题目尚无答案，无法进行AI分析"
                    @request-analysis="bankRequestAi"
                    @apply-recommendation="bankApplyAi"
                    @cancel-analysis="bankCancelAi"
                  />
                </div>

                <!-- Close detail -->
                <div class="detail-close-row">
                  <button class="btn-secondary btn-sm" @click="expandedUuid = ''">收起面板</button>
                </div>
              </div>
            </div>
          </Transition>
        </template>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive } from 'vue'
import QuestionCard from '@/components/QuestionCard.vue'
import StemEditor from '@/components/StemEditor.vue'
import LatexPreview from '@/components/LatexPreview.vue'
import TagSection from '@/components/TagSection.vue'
import DifficultySlider from '@/components/DifficultySlider.vue'
import AiAnalysisPanel from '@/components/AiAnalysisPanel.vue'
import AnswerTabNav from '@/components/AnswerTabNav.vue'
import { useAuthStore } from '@/stores/auth'
import { useQuestionStore, stageOf } from '@/stores/question'
import type { QuestionEntry } from '@/stores/question'
import { useTagStore } from '@/stores/tag'
import { useNotificationStore } from '@/stores/notification'
import { toStemXmlPayload, toAnswerXmlPayload, isEmptyXmlContent } from '@/lib/stemXml'
import { difficultyLevel } from '@/lib/difficulty'
import type { InlineImageEntry } from '@/api/types'

const auth = useAuthStore()
const questionStore = useQuestionStore()
const tagStore = useTagStore()
const notif = useNotificationStore()

const mainScrollRef = ref<HTMLElement>()

// ══════════════════════════════════════════════
// Filters
// ══════════════════════════════════════════════

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

const bankList = computed(() => {
  return questionStore.sortedEntries.filter((entry) => {
    if (stageOf(entry) !== 'COMPLETED') return false

    if (search.value) {
      const q = search.value.toLowerCase()
      const inStem = entry.stemText.toLowerCase().includes(q)
      const inTags = entry.secondaryTags.some((t) => t.toLowerCase().includes(q))
      if (!inStem && !inTags) return false
    }

    if (gradeFilter.value) {
      if (!entry.mainTags.some((t) => t.tagCode === gradeFilter.value)) return false
    }

    if (knowledgeFilter.value) {
      if (!entry.mainTags.some((t) => t.tagCode === knowledgeFilter.value)) return false
    }

    if (difficultyFilter.value) {
      if (difficultyFilter.value === 'unset') {
        if (entry.difficulty !== null) return false
      } else {
        if (entry.difficulty === null) return false
        if (difficultyLevel(entry.difficulty).key !== difficultyFilter.value) return false
      }
    }

    return true
  })
})

// ══════════════════════════════════════════════
// Selection (for future exam compose)
// ══════════════════════════════════════════════

const selectedSet = reactive(new Set<string>())

function toggleSelect(uuid: string) {
  if (selectedSet.has(uuid)) {
    selectedSet.delete(uuid)
  } else {
    selectedSet.add(uuid)
  }
}

// ══════════════════════════════════════════════
// Detail Expansion
// ══════════════════════════════════════════════

const expandedUuid = ref('')

function cancelAllEditing() {
  cancelStemEdit()
  cancelAnswerEdit()
  cancelAddAnswer()
}

async function toggleDetail(uuid: string) {
  if (expandedUuid.value === uuid) {
    expandedUuid.value = ''
    cancelAllEditing()
    return
  }
  cancelAllEditing()
  expandedUuid.value = uuid
  questionStore.selectBankQuestion(uuid)

  const entry = questionStore.entries.get(uuid)
  if (entry && !entry.assetsLoaded) {
    try {
      await questionStore.fetchAssets(auth.token, uuid)
    } catch { /* may not have assets */ }
  }
}

function openEdit(uuid: string) {
  if (expandedUuid.value !== uuid) {
    toggleDetail(uuid)
  }
}

// ══════════════════════════════════════════════
// Image Resolvers
// ══════════════════════════════════════════════

function resolveImage(entry: QuestionEntry, refKey: string): string {
  const data = entry.inlineImages[refKey]
  if (!data) return ''
  return data.startsWith('data:') ? data : `data:image/png;base64,${data}`
}

function resolveAnswerImage(entry: QuestionEntry, refKey: string): string {
  const data = entry.answerImages[refKey] ?? entry.inlineImages[refKey]
  if (!data) return ''
  return data.startsWith('data:') ? data : `data:image/png;base64,${data}`
}

const bankStemImageVersion = computed(() => {
  const entry = questionStore.bankSelectedEntry
  return Object.keys(entry?.inlineImages ?? {}).length
})
const bankAnswerImageVersion = computed(() => {
  const entry = questionStore.bankSelectedEntry
  return (
    Object.keys(entry?.answerImages ?? {}).length +
    Object.keys(entry?.inlineImages ?? {}).length
  )
})

// ══════════════════════════════════════════════
// Stem Editing
// ══════════════════════════════════════════════

const stemEditing = ref(false)
const stemEditDraft = ref('')
const bankStemEditorRef = ref<InstanceType<typeof StemEditor> | null>(null)

function startEditStem() {
  const entry = questionStore.bankSelectedEntry
  if (!entry) return
  stemEditDraft.value = entry.stemText
  stemEditing.value = true
}

function cancelStemEdit() {
  stemEditing.value = false
  stemEditDraft.value = ''
}

async function saveStemEdit() {
  const entry = questionStore.bankSelectedEntry
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
    await questionStore.confirmStem(auth.token, entry.questionUuid, {
      stemXml,
      inlineImages: Object.keys(inlineImages).length > 0 ? inlineImages : undefined
    })
    stemEditing.value = false
    notif.log(`更新题干 ${entry.questionUuid.slice(0, 8)}`)
  } catch (e: any) {
    notif.log(`更新题干失败: ${e?.message || e}`)
  }
}

// ══════════════════════════════════════════════
// Answer Editing
// ══════════════════════════════════════════════

const answerEditing = ref(false)
const answerEditDraft = ref('')
const bankAnswerEditorRef = ref<InstanceType<typeof StemEditor> | null>(null)

function startEditAnswer() {
  const entry = questionStore.bankSelectedEntry
  if (!entry) return
  answerEditDraft.value = entry.answersLocal[questionStore.bankAnswerIdx] ?? ''
  answerEditing.value = true
}

function cancelAnswerEdit() {
  answerEditing.value = false
  answerEditDraft.value = ''
}

async function saveAnswerEdit() {
  const entry = questionStore.bankSelectedEntry
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
    await questionStore.updateAnswer(auth.token, entry.questionUuid, serverData.answerUuid, {
      latexText: answerXml,
      inlineImages: Object.keys(inlineImages).length > 0 ? inlineImages : undefined
    })
    answerEditing.value = false
    notif.log(`更新答案 ${entry.questionUuid.slice(0, 8)}`)
  } catch (e: any) {
    notif.log(`更新答案失败: ${e?.message || e}`)
  }
}

// ══════════════════════════════════════════════
// Answer Add
// ══════════════════════════════════════════════

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
  const entry = questionStore.bankSelectedEntry
  if (!entry) return
  if (!answerAddDraft.value?.trim()) {
    notif.log('请先输入答案内容')
    return
  }

  const answerXml = toAnswerXmlPayload(answerAddDraft.value)
  if (!answerXml || isEmptyXmlContent(answerXml, 'answer')) {
    notif.log('答案内容不能为空')
    return
  }

  try {
    await questionStore.addAnswer(auth.token, entry.questionUuid, { latexText: answerXml })
    answerAdding.value = false
    answerAddDraft.value = ''
    questionStore.bankAnswerIdx = entry.answersLocal.length - 1
    notif.log(`新增答案 ${entry.questionUuid.slice(0, 8)}`)
  } catch (e: any) {
    notif.log(`新增答案失败: ${e?.message || e}`)
  }
}

async function deleteBankAnswer() {
  const entry = questionStore.bankSelectedEntry
  if (!entry || entry.answersServerData.length <= 1) return

  const idx = questionStore.bankAnswerIdx
  const serverData = entry.answersServerData[idx]
  if (!serverData) return

  try {
    await questionStore.deleteAnswer(auth.token, entry.questionUuid, serverData.answerUuid)
    questionStore.bankAnswerIdx = Math.max(0, idx - 1)
  } catch (e: any) {
    notif.log(`删除答案失败: ${e?.message || e}`)
  }
}

// ══════════════════════════════════════════════
// Tags
// ══════════════════════════════════════════════

const bankTagRef = ref<InstanceType<typeof TagSection> | null>(null)

async function onBankTagsChange(payload: { tags: string[] }) {
  const entry = questionStore.bankSelectedEntry
  if (!entry || !payload.tags.length) return
  try {
    await questionStore.updateTags(auth.token, entry.questionUuid, payload.tags)
  } catch (e: any) {
    notif.log(`更新标签失败: ${e?.message || e}`)
  }
}

// ══════════════════════════════════════════════
// Difficulty
// ══════════════════════════════════════════════

let bankDiffTimer: ReturnType<typeof setTimeout> | null = null

function onBankDifficultyChange(val: number | null) {
  const entry = questionStore.bankSelectedEntry
  if (!entry || val === null) return
  entry.difficulty = val

  if (bankDiffTimer) clearTimeout(bankDiffTimer)
  bankDiffTimer = setTimeout(async () => {
    await questionStore.updateDifficulty(auth.token, entry.questionUuid, val)
  }, 500)
}

// ══════════════════════════════════════════════
// AI
// ══════════════════════════════════════════════

async function bankRequestAi() {
  const entry = questionStore.bankSelectedEntry
  if (!entry) return
  if (entry.answersServerData.length === 0) {
    notif.log('该题目尚无答案，无法进行AI分析')
    return
  }
  try {
    await questionStore.requestAiAnalysis(auth.token, entry.questionUuid, 'bank')
  } catch (e: unknown) {
    notif.log(e instanceof Error ? e.message : 'AI分析请求失败')
  }
}

function bankCancelAi() {
  const entry = questionStore.bankSelectedEntry
  if (!entry) return
  questionStore.cancelAiAnalysis(entry.questionUuid, 'bank')
}

async function bankApplyAi() {
  const entry = questionStore.bankSelectedEntry
  const ai = questionStore.bankAi
  if (!entry || !ai.lastResult) return
  try {
    await questionStore.applyAiRecommendation(
      auth.token,
      entry.questionUuid,
      ai.lastResult.taskUuid,
      ai.lastResult.suggestedTags ?? undefined,
      ai.lastResult.suggestedDifficulty ?? undefined
    )
  } catch (e: unknown) {
    notif.log(e instanceof Error ? e.message : '采纳AI推荐失败')
  }
}
</script>

<style scoped>
.bank-view {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 0;
  overflow: hidden;
}

/* ══════════════════════════════════════
   Sidebar
   ══════════════════════════════════════ */

.bank-sidebar {
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  background: var(--color-bg-sidebar);
  border-right: 1px solid var(--color-border);
}

.filter-section {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
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

.filter-stats {
  padding: 10px 16px;
  border-top: 1px solid var(--color-border-light);
  font-size: 13px;
  color: var(--color-text-secondary);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-stats strong {
  color: var(--color-accent);
  font-weight: 700;
}

.selected-count {
  color: var(--color-accent);
  font-weight: 500;
}

/* ══════════════════════════════════════
   Main — Card List
   ══════════════════════════════════════ */

.bank-main {
  min-height: 0;
  overflow-y: auto;
  background: var(--color-bg-primary);
  padding: 16px 20px;
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

.card-list {
  max-width: 900px;
  margin: 0 auto;
}

/* ══════════════════════════════════════
   Inline Detail Panel
   ══════════════════════════════════════ */

.inline-detail {
  background: var(--color-bg-card);
  border: 1px solid var(--color-accent);
  border-top: none;
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
  margin-top: -12px;
  margin-bottom: 12px;
  box-shadow: 0 4px 16px var(--color-accent-glow);
  overflow: hidden;
}

.detail-inner {
  padding: 16px 20px;
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
  margin-bottom: 8px;
  gap: 8px;
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

.detail-close-row {
  text-align: center;
  padding-top: 8px;
}

/* ── Transition ── */
.detail-slide-enter-active,
.detail-slide-leave-active {
  transition: all 0.25s ease;
  max-height: 2000px;
}

.detail-slide-enter-from,
.detail-slide-leave-to {
  opacity: 0;
  max-height: 0;
}

/* ══════════════════════════════════════
   Buttons
   ══════════════════════════════════════ */

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
