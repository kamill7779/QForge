<template>
  <div class="bank-view">
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
      <span class="bank-count">共 {{ questionStore.totalCount }} 题</span>
    </div>

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
      </template>
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
import { ref, onMounted } from 'vue'
import QuestionCard from '@/components/QuestionCard.vue'
import LatexPreview from '@/components/LatexPreview.vue'
import { useQuestionStore } from '@/stores/question'
import { useExamStore } from '@/stores/exam'
import { useNotificationStore } from '@/stores/notification'
import { difficultyLabel } from '@/lib/difficulty'
import type { QuestionEntry } from '@/stores/question'

const questionStore = useQuestionStore()
const examStore = useExamStore()
const notif = useNotificationStore()

const keyword = ref('')
const detailQuestion = ref<QuestionEntry | null>(null)
const listRef = ref<HTMLElement>()

onMounted(() => {
  if (!questionStore.questions.length) {
    questionStore.fetchQuestions()
  }
})

function doSearch() {
  if (keyword.value.trim()) {
    questionStore.searchQuestions(keyword.value.trim())
  } else {
    questionStore.clearSearch()
  }
}

function refresh() {
  keyword.value = ''
  questionStore.clearSearch()
  questionStore.fetchQuestions()
}

function toggleDetail(q: QuestionEntry) {
  detailQuestion.value = detailQuestion.value?.questionUuid === q.questionUuid ? null : q
}

function toggleSelect(q: QuestionEntry) {
  if (!examStore.activeExam) {
    // Auto-create exam if none exists
    examStore.createExam()
    notif.log('已自动创建新试卷')
  }
  const exam = examStore.activeExam!
  if (examStore.isQuestionInExam(q.questionUuid)) {
    // Remove from first section that contains it
    for (const sec of exam.sections) {
      if (sec.questions.some((item) => item.questionUuid === q.questionUuid)) {
        examStore.removeQuestion(exam.id, sec.id, q.questionUuid)
        break
      }
    }
  } else {
    // Add to first section
    const sec = exam.sections[0]
    if (sec) {
      examStore.addQuestionToSection(exam.id, sec.id, q)
    }
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
  flex-direction: column;
  height: 100%;
  overflow: hidden;
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
.bank-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}
.bank-loading,
.bank-loading-more,
.bank-end {
  text-align: center;
  padding: 24px;
  color: var(--color-text-muted);
  font-size: 13px;
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
.modal-header h3 { font-size: 16px; font-weight: 600; }
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
.modal-close:hover { background: var(--color-danger-bg); color: var(--color-danger); }
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
.tag-pill.main { background: var(--color-info-bg); color: var(--color-info); }
.tag-pill.sec { background: var(--color-bg-secondary); color: var(--color-text-secondary); }
.detail-diff {
  font-size: 13px;
  color: var(--color-text-secondary);
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
