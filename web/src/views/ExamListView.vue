<template>
  <div class="exam-list-view">
    <div class="exam-list-toolbar">
      <h2>我的试卷</h2>
      <button class="btn-create" @click="createNew">+ 新建试卷</button>
    </div>

    <div class="exam-grid" v-if="examStore.examList.length">
      <div
        v-for="exam in examStore.examList"
        :key="exam.id"
        class="exam-card"
        @click="openCompose(exam.id)"
      >
        <div class="exam-card-title">{{ exam.title }}</div>
        <div class="exam-card-subtitle">{{ exam.subtitle || '暂无副标题' }}</div>
        <div class="exam-card-meta">
          <span>{{ exam.questionCount }} 题</span>
          <span>{{ exam.totalScore }} 分</span>
          <span>{{ formatDate(exam.updatedAt) }}</span>
        </div>
        <div class="exam-card-actions" @click.stop>
          <button class="card-action-btn" @click="openPreview(exam.id)">预览</button>
          <button class="card-action-btn" @click="duplicate(exam.id)">复制</button>
          <button class="card-action-btn danger" @click="confirmDelete(exam.id)">删除</button>
        </div>
      </div>
    </div>

    <div v-else class="exam-empty">
      <div class="empty-icon">📝</div>
      <p>还没有试卷，点击上方按钮创建第一份试卷吧！</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useExamStore } from '@/stores/exam'

const router = useRouter()
const examStore = useExamStore()

onMounted(() => {
  examStore.fetchExams()
})

async function createNew() {
  const exam = await examStore.createExam()
  router.push(`/exams/${exam.id}/edit`)
}

async function openCompose(id: string) {
  await examStore.setActiveExam(id)
  router.push(`/exams/${id}/edit`)
}

function openPreview(id: string) {
  router.push(`/preview/${id}`)
}

async function duplicate(id: string) {
  await examStore.duplicateExam(id)
}

async function confirmDelete(id: string) {
  if (confirm('确定删除这份试卷吗？此操作不可撤销。')) {
    await examStore.deleteExam(id)
  }
}

function formatDate(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
</script>

<style scoped>
.exam-list-view {
  padding: 24px;
  overflow-y: auto;
  height: 100%;
}
.exam-list-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.exam-list-toolbar h2 {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
}
.btn-create {
  padding: 10px 24px;
  border: none;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-accent), #a29bfe);
  color: #fff;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  box-shadow: 0 4px 16px var(--color-accent-glow);
  transition: all var(--transition-fast);
}
.btn-create:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px var(--color-accent-glow);
}

.exam-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}
.exam-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  padding: 20px;
  cursor: pointer;
  transition: all var(--transition-fast);
}
.exam-card:hover {
  border-color: var(--color-accent);
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}
.exam-card-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin-bottom: 4px;
}
.exam-card-subtitle {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: 12px;
}
.exam-card-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-bottom: 12px;
}
.exam-card-actions {
  display: flex;
  gap: 8px;
}
.card-action-btn {
  padding: 4px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition-fast);
}
.card-action-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}
.card-action-btn.danger:hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
  background: var(--color-danger-bg);
}

.exam-empty {
  text-align: center;
  padding: 80px 20px;
  color: var(--color-text-muted);
}
.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}
</style>
