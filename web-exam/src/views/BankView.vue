<template>
  <div class="bank-view">
    <!-- Left: Tree Sidebar -->
    <aside class="bank-sidebar">
      <div class="sidebar-header">
        <span class="sidebar-title">分类筛选</span>
        <button
          v-if="questionStore.activeFilterCount > 0"
          class="sidebar-clear"
          @click="questionStore.clearAllFilters()"
        >
          清除 ({{ questionStore.activeFilterCount }})
        </button>
      </div>

      <div class="sidebar-search">
        <input
          v-model="sidebarSearch"
          type="text"
          class="sidebar-search-input"
          placeholder="搜索分类…"
        />
      </div>

      <div class="sidebar-tree">
        <div v-for="branch in filteredTree" :key="branch.key" class="tree-branch">
          <div class="tree-branch-header" @click="toggleBranch(branch.key)">
            <span class="tree-expand-icon">{{ expandedBranches.has(branch.key) ? '▾' : '▸' }}</span>
            <span class="tree-branch-label">{{ branch.label }}</span>
            <span class="tree-branch-count">{{ branch.count }}</span>
          </div>
          <div v-show="expandedBranches.has(branch.key)" class="tree-leaves">
            <div
              v-for="leaf in branch.children"
              :key="leaf.key"
              class="tree-leaf"
              :class="{ active: isLeafActive(leaf) }"
              @click="onLeafClick(leaf)"
            >
              <span class="tree-leaf-label">{{ leaf.label }}</span>
              <span class="tree-leaf-count">{{ leaf.count }}</span>
            </div>
          </div>
        </div>
        <div v-if="!filteredTree.length" class="sidebar-empty">暂无分类数据</div>
      </div>
    </aside>

    <!-- Right: Main content -->
    <div class="bank-main">
      <!-- Toolbar -->
      <div class="bank-toolbar">
        <input
          v-model="keyword"
          type="text"
          class="search-input"
          placeholder="搜索题目…"
          @keydown.enter="doSearch"
        />
        <button class="toolbar-btn" @click="doSearch">搜索</button>
        <button class="toolbar-btn" @click="refresh">刷新</button>
        <span class="bank-count">
          {{ questionStore.filteredCount }} / {{ questionStore.totalCount }} 题
        </span>
      </div>

      <!-- Active filter pills -->
      <div v-if="questionStore.activeFilterCount > 0" class="active-filters">
        <span
          v-if="questionStore.filterGrade"
          class="filter-pill"
          @click="questionStore.setDimensionFilter('grade', questionStore.filterGrade)"
        >
          年级: {{ getGradeLabel(questionStore.filterGrade) }} ×
        </span>
        <span
          v-if="questionStore.filterKnowledge"
          class="filter-pill"
          @click="questionStore.setDimensionFilter('knowledge', questionStore.filterKnowledge)"
        >
          知识点: {{ getKnowledgeLabel(questionStore.filterKnowledge) }} ×
        </span>
        <span
          v-if="questionStore.filterDifficulty"
          class="filter-pill"
          @click="questionStore.setDimensionFilter('difficulty', questionStore.filterDifficulty)"
        >
          难度: {{ questionStore.filterDifficulty }} ×
        </span>
        <span
          v-if="questionStore.filterSource"
          class="filter-pill"
          @click="questionStore.setDimensionFilter('source', questionStore.filterSource)"
        >
          来源: {{ questionStore.filterSource }} ×
        </span>
      </div>

      <!-- Question list -->
      <div class="bank-list" ref="listRef" @scroll="onScroll">
        <div v-if="questionStore.loading && !questionStore.questions.length" class="bank-loading">
          加载中…
        </div>
        <template v-else>
          <QuestionCard
            v-for="(q, i) in questionStore.questions"
            :key="q.questionUuid"
            :entry="q"
            :seq="i + 1"
            :is-selected="examStore.selectedQuestionUuids.has(q.questionUuid)"
            @toggle-detail="toggleDetail(q)"
            @toggle-select="toggleSelect(q)"
          />
          <div v-if="questionStore.loading" class="bank-loading-more">加载更多…</div>
          <div v-if="!questionStore.hasMore && questionStore.questions.length" class="bank-end">
            —— 已加载全部 ——
          </div>
          <div v-if="!questionStore.loading && !questionStore.questions.length" class="bank-empty">
            {{ questionStore.activeFilterCount > 0 ? '当前筛选条件下没有题目' : '题库为空' }}
          </div>
        </template>
      </div>

      <!-- Cart status bar -->
      <div v-if="examStore.activeExam" class="cart-status-bar">
        <span class="cart-icon">🛒</span>
        <span class="cart-label">{{ examStore.activeExam.title }}</span>
        <span class="cart-count">
          已选 {{ cartQuestionCount }} 题 / {{ examStore.activeExam.totalScore }} 分
        </span>
        <router-link :to="'/compose/' + examStore.activeExam.id" class="cart-go-btn">
          去组卷 →
        </router-link>
      </div>
    </div>

    <!-- Question detail modal -->
    <Teleport to="body">
      <div v-if="detailQuestion" class="modal-overlay" @click.self="detailQuestion = null">
        <div class="modal-card">
          <div class="modal-header">
            <h3>题目详情</h3>
            <button class="modal-close" @click="detailQuestion = null">×</button>
          </div>
          <div class="modal-body">
            <LatexPreview :xml="detailQuestion.stemText" />
            <div class="detail-meta">
              <div v-if="detailQuestion.mainTags.length" class="detail-tags">
                <span v-for="t in detailQuestion.mainTags" :key="t.tagCode" class="tag-pill main">{{ t.tagName }}</span>
              </div>
              <div v-if="detailQuestion.secondaryTags.length" class="detail-tags">
                <span v-for="st in detailQuestion.secondaryTags" :key="st" class="tag-pill sec">{{ st }}</span>
              </div>
              <div v-if="detailQuestion.difficulty !== null" class="detail-diff">
                难度: {{ difficultyLabel(detailQuestion.difficulty) }}
              </div>
              <!-- Source display & edit -->
              <div class="detail-source">
                <label class="detail-source-label">来源:</label>
                <input
                  v-model="editSource"
                  type="text"
                  class="detail-source-input"
                  list="source-options"
                  placeholder="输入或选择来源…"
                  @change="saveSource"
                  @blur="saveSource"
                />
                <datalist id="source-options">
                  <option v-for="s in questionStore.sourceOptions" :key="s" :value="s" />
                </datalist>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button
              class="btn-action primary"
              @click="toggleSelect(detailQuestion!); detailQuestion = null"
            >
              {{ examStore.selectedQuestionUuids.has(detailQuestion.questionUuid) ? '取消选题' : '+ 选入试卷' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import QuestionCard from '@/components/QuestionCard.vue'
import LatexPreview from '@/components/LatexPreview.vue'
import { useQuestionStore } from '@/stores/question'
import { useExamStore } from '@/stores/exam'
import { useNotificationStore } from '@/stores/notification'
import { difficultyLabel } from '@/lib/difficulty'
import type { QuestionEntry, TreeNode } from '@/stores/question'

const questionStore = useQuestionStore()
const examStore = useExamStore()
const notif = useNotificationStore()

const keyword = ref('')
const detailQuestion = ref<QuestionEntry | null>(null)
const listRef = ref<HTMLElement>()
const editSource = ref('')
const sidebarSearch = ref('')

// Track which tree branches are expanded
const expandedBranches = ref(new Set<string>(['grade', 'knowledge', 'difficulty', 'source']))

const cartQuestionCount = computed(() => {
  const exam = examStore.activeExam
  if (!exam) return 0
  return exam.sections.reduce((s, sec) => s + sec.questions.length, 0)
})

// Filter category tree based on sidebar search
const filteredTree = computed(() => {
  const tree = questionStore.categoryTree
  if (!sidebarSearch.value) return tree
  const kw = sidebarSearch.value.toLowerCase()
  return tree
    .map((branch) => ({
      ...branch,
      children: (branch.children || []).filter((leaf) =>
        leaf.label.toLowerCase().includes(kw)
      )
    }))
    .filter((branch) => branch.children && branch.children.length > 0)
})

onMounted(async () => {
  if (!questionStore.allQuestions.length) {
    await questionStore.fetchQuestions()
  }
  // Auto-create default exam if none
  if (!examStore.activeExam && !examStore.exams.length) {
    await examStore.fetchExams()
    if (!examStore.exams.length) {
      await examStore.createExam('我的选题篮')
      notif.log('已自动创建选题篮')
    } else if (!examStore.activeExamId) {
      await examStore.setActiveExam(examStore.exams[0].id)
    }
  }
})

// Sync editSource when detail modal opens
watch(detailQuestion, (q) => {
  editSource.value = q?.source || '未分类'
})

function toggleBranch(key: string) {
  if (expandedBranches.value.has(key)) {
    expandedBranches.value.delete(key)
  } else {
    expandedBranches.value.add(key)
  }
}

function isLeafActive(leaf: TreeNode): boolean {
  if (!leaf.dimension || !leaf.value) return false
  switch (leaf.dimension) {
    case 'grade': return questionStore.filterGrade === leaf.value
    case 'knowledge': return questionStore.filterKnowledge === leaf.value
    case 'difficulty': return questionStore.filterDifficulty === leaf.value
    case 'source': return questionStore.filterSource === leaf.value
  }
  return false
}

function onLeafClick(leaf: TreeNode) {
  if (leaf.dimension && leaf.value) {
    questionStore.setDimensionFilter(leaf.dimension, leaf.value)
  }
}

function getGradeLabel(code: string): string {
  for (const branch of questionStore.categoryTree) {
    if (branch.key === 'grade') {
      const leaf = branch.children?.find((c) => c.value === code)
      if (leaf) return leaf.label
    }
  }
  return code
}

function getKnowledgeLabel(code: string): string {
  for (const branch of questionStore.categoryTree) {
    if (branch.key === 'knowledge') {
      const leaf = branch.children?.find((c) => c.value === code)
      if (leaf) return leaf.label
    }
  }
  return code
}

function doSearch() {
  if (keyword.value.trim()) {
    questionStore.searchQuestions(keyword.value.trim())
  } else {
    questionStore.clearSearch()
  }
}

function refresh() {
  keyword.value = ''
  questionStore.clearAllFilters()
  questionStore.fetchQuestions()
}

function toggleDetail(q: QuestionEntry) {
  detailQuestion.value = detailQuestion.value?.questionUuid === q.questionUuid ? null : q
}

async function toggleSelect(q: QuestionEntry) {
  if (!examStore.activeExam) {
    await examStore.createExam('我的选题篮')
    notif.log('已自动创建选题篮')
  }
  const exam = examStore.activeExam
  if (!exam) return
  if (examStore.isQuestionInExam(q.questionUuid)) {
    for (const sec of exam.sections) {
      if (sec.questions.some((item) => item.questionUuid === q.questionUuid)) {
        examStore.removeQuestion(exam.id, sec.id, q.questionUuid)
        break
      }
    }
  } else {
    const sec = exam.sections[0]
    if (sec) {
      examStore.addQuestionToSection(exam.id, sec.id, q)
    }
  }
}

async function saveSource() {
  if (!detailQuestion.value) return
  const newSource = editSource.value.trim() || '未分类'
  if (newSource === detailQuestion.value.source) return
  try {
    await questionStore.updateSource(detailQuestion.value.questionUuid, newSource)
    detailQuestion.value.source = newSource
    notif.log('来源已更新为「' + newSource + '」')
  } catch {
    notif.log('更新来源失败')
  }
}

function onScroll() {
  if (!listRef.value) return
  const { scrollTop, scrollHeight, clientHeight } = listRef.value
  if (scrollHeight - scrollTop - clientHeight < 200) {
    questionStore.loadMore()
  }
}
</script>

<style scoped>
.bank-view {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* Sidebar */
.bank-sidebar {
  width: 240px;
  min-width: 200px;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg-panel);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-shrink: 0;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px 8px;
  border-bottom: 1px solid var(--color-border-light);
}

.sidebar-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.sidebar-clear {
  font-size: 11px;
  color: var(--color-accent);
  background: none;
  border: none;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: var(--radius-sm);
}

.sidebar-clear:hover {
  background: var(--color-accent-muted);
}

.sidebar-search {
  padding: 8px 10px;
}

.sidebar-search-input {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: 12px;
  background: var(--color-bg-card);
  color: var(--color-text-primary);
}

.sidebar-search-input::placeholder {
  color: var(--color-text-muted);
}

.sidebar-tree {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

.tree-branch {
  margin-bottom: 2px;
}

.tree-branch-header {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  transition: background var(--transition-fast);
}

.tree-branch-header:hover {
  background: var(--color-bg-secondary);
}

.tree-expand-icon {
  width: 14px;
  font-size: 10px;
  text-align: center;
  color: var(--color-text-muted);
}

.tree-branch-label {
  flex: 1;
}

.tree-branch-count {
  font-size: 11px;
  color: var(--color-text-muted);
  font-weight: 400;
}

.tree-leaves {
  padding-left: 8px;
}

.tree-leaf {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 12px 5px 24px;
  cursor: pointer;
  font-size: 12px;
  color: var(--color-text-secondary);
  border-radius: var(--radius-sm);
  margin: 0 4px;
  transition: all var(--transition-fast);
}

.tree-leaf:hover {
  background: var(--color-bg-secondary);
  color: var(--color-text-primary);
}

.tree-leaf.active {
  background: var(--color-accent-muted);
  color: var(--color-accent);
  font-weight: 600;
}

.tree-leaf-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-leaf-count {
  font-size: 10px;
  color: var(--color-text-muted);
  margin-left: 6px;
  flex-shrink: 0;
}

.tree-leaf.active .tree-leaf-count {
  color: var(--color-accent);
}

.sidebar-empty {
  padding: 20px;
  text-align: center;
  font-size: 12px;
  color: var(--color-text-muted);
}

/* Main area */
.bank-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.bank-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-card);
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  max-width: 400px;
}

.toolbar-btn {
  padding: 8px 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-panel);
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  transition: all var(--transition-fast);
}

.toolbar-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: var(--color-accent-muted);
}

.bank-count {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-left: auto;
}

/* Active filter pills */
.active-filters {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 20px;
  background: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border-light);
  flex-shrink: 0;
  flex-wrap: wrap;
}

.filter-pill {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-weight: 500;
  background: var(--color-accent-muted);
  color: var(--color-accent);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.filter-pill:hover {
  background: var(--color-accent);
  color: #fff;
}

.bank-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

.bank-loading,
.bank-loading-more,
.bank-end,
.bank-empty {
  text-align: center;
  padding: 24px;
  color: var(--color-text-muted);
  font-size: 13px;
}

/* Cart status bar */
.cart-status-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 20px;
  border-top: 1px solid var(--color-border);
  background: var(--color-bg-card);
  flex-shrink: 0;
}

.cart-icon {
  font-size: 16px;
}

.cart-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.cart-count {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.cart-go-btn {
  margin-left: auto;
  padding: 5px 14px;
  border-radius: var(--radius-md);
  font-size: 12px;
  font-weight: 600;
  background: var(--color-accent);
  color: #fff;
  text-decoration: none;
  transition: background var(--transition-fast);
}

.cart-go-btn:hover {
  background: var(--color-accent-hover);
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: grid;
  place-items: center;
  z-index: 1000;
  animation: fadeIn 0.2s ease;
}

.modal-card {
  width: min(700px, 90vw);
  max-height: 80vh;
  background: var(--color-bg-card);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: fadeInUp 0.3s ease;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}

.modal-header h3 {
  font-size: 16px;
  font-weight: 600;
}

.modal-close {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: var(--color-bg-secondary);
  color: var(--color-text-secondary);
  font-size: 16px;
  cursor: pointer;
  display: grid;
  place-items: center;
}

.modal-close:hover {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.detail-meta {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 500;
}

.tag-pill.main {
  background: var(--color-info-bg);
  color: var(--color-info);
}

.tag-pill.sec {
  background: var(--color-bg-secondary);
  color: var(--color-text-secondary);
}

.detail-diff {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.detail-source {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.detail-source-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.detail-source-input {
  flex: 1;
  max-width: 280px;
  padding: 5px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: 13px;
  background: var(--color-bg-panel);
  color: var(--color-text-primary);
}

.detail-source-input:focus {
  border-color: var(--color-accent);
  outline: none;
}

.modal-footer {
  padding: 12px 20px;
  border-top: 1px solid var(--color-border);
  display: flex;
  justify-content: flex-end;
}

.btn-action {
  padding: 8px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  transition: all var(--transition-fast);
}

.btn-action.primary {
  background: var(--color-accent);
  color: #fff;
  border-color: var(--color-accent);
}

.btn-action.primary:hover {
  background: var(--color-accent-hover);
}
</style>
