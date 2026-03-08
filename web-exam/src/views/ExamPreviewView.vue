<template>
  <div class="preview-view">
    <div class="preview-toolbar no-print">
      <router-link :to="exam ? `/compose/${exam.id}` : '/exams'" class="back-btn">
        ← 返回编辑
      </router-link>
      <span class="toolbar-title">试卷预览</span>
      <div class="toolbar-actions">
        <button class="tool-btn" @click="doPrint">🖨 打印</button>
        <button class="tool-btn export-word-btn" @click="doExportWord" :disabled="exporting">
          {{ exporting ? '导出中…' : '📄 导出 Word' }}
        </button>
      </div>
    </div>

    <!-- Loading state -->
    <div v-if="loading" class="preview-loading">
      加载中…
    </div>

    <div v-else-if="exam" class="paper-wrapper">
      <article class="paper-page">
        <!-- Paper header -->
        <header class="paper-header">
          <h1 class="paper-title">{{ exam.title || '未命名试卷' }}</h1>
          <p v-if="exam.subtitle" class="paper-subtitle">{{ exam.subtitle }}</p>
          <div class="paper-meta">
            <span v-if="exam.duration">考试时间: {{ exam.duration }} 分钟</span>
            <span>总分: {{ exam.totalScore }} 分</span>
          </div>
          <div class="paper-info-line">
            <span>姓名: _______________</span>
            <span>班级: _______________</span>
            <span>学号: _______________</span>
            <span>得分: _______________</span>
          </div>
        </header>

        <!-- Sections -->
        <section
          v-for="(sec, sIdx) in exam.sections"
          :key="sec.id"
          class="paper-section"
        >
          <h2 class="section-title">
            {{ sectionNumber(sIdx) }}、{{ sec.title }}
            <span class="section-score-hint">（共 {{ sec.questions.length }} 小题，{{ sectionScore(sec) }} 分）</span>
          </h2>

          <div
            v-for="(q, qIdx) in sec.questions"
            :key="q.questionUuid"
            class="paper-question"
          >
            <div class="pq-header">
              <span class="pq-num">{{ globalSeq(sIdx, qIdx) }}.</span>
              <span class="pq-score">（{{ q.score }} 分）</span>
            </div>
            <div class="pq-body">
              <LatexPreview
                :xml="q.snapshot.stemText"
                :image-resolver="assetHelper.resolverFor(q.questionUuid)"
                :render-key="assetRenderKey"
                class="pq-stem"
              />
            </div>
            <!-- Answer area -->
            <div class="pq-answer-area"></div>
          </div>
        </section>
      </article>
    </div>

    <div v-else class="no-exam">
      <p>试卷不存在</p>
      <router-link to="/exams" class="back-link">返回试卷列表</router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import LatexPreview from '@/components/LatexPreview.vue'
import { useExamStore } from '@/stores/exam'
import { useNotificationStore } from '@/stores/notification'
import { useQuestionAssets } from '@/composables/useQuestionAssets'
import type { ExamSection } from '@/stores/exam'

const route = useRoute()
const examStore = useExamStore()
const notif = useNotificationStore()
const assetHelper = useQuestionAssets()

const assetRenderKey = ref(0)
const exporting = ref(false)
const loading = ref(true)

onMounted(async () => {
  const id = route.params.id as string
  if (id) {
    // Ensure exam is loaded (handles page refresh)
    await examStore.setActiveExam(id)
    // Load image assets for all questions
    const e = examStore.activeExam
    if (e) {
      const uuids = e.sections.flatMap(s => s.questions.map(q => q.questionUuid))
      assetHelper.loadAssetsForMany(uuids).then(() => {
        assetRenderKey.value++
      })
    }
  }
  loading.value = false
})

const exam = computed(() => examStore.activeExam)

const CN_NUMBERS = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十']

function sectionNumber(idx: number): string {
  return CN_NUMBERS[idx] ?? String(idx + 1)
}

function sectionScore(sec: ExamSection): number {
  return sec.questions.reduce((s, q) => s + q.score, 0)
}

function globalSeq(sIdx: number, qIdx: number): number {
  if (!exam.value) return qIdx + 1
  let seq = 0
  for (let i = 0; i < sIdx; i++) {
    seq += exam.value.sections[i].questions.length
  }
  return seq + qIdx + 1
}

function doPrint() {
  window.print()
}

async function doExportWord() {
  if (!exam.value) return
  exporting.value = true
  try {
    await examStore.exportWord(exam.value.id, true)
    notif.log('试卷已导出')
  } catch (e: any) {
    notif.log(e?.message ?? '导出失败')
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.preview-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--color-bg-base);
}

/* ── Toolbar ── */
.preview-toolbar {
  display: flex;
  align-items: center;
  padding: 10px 24px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-card);
  gap: 16px;
  flex-shrink: 0;
}
.back-btn {
  color: var(--color-text-secondary);
  text-decoration: none;
  font-size: 13px;
  padding: 6px 12px;
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
}
.back-btn:hover {
  background: var(--color-bg-hover);
  color: var(--color-accent);
}
.toolbar-title {
  flex: 1;
  font-weight: 600;
  color: var(--color-text-primary);
}
.toolbar-actions {
  display: flex;
  gap: 8px;
}
.tool-btn {
  padding: 8px 20px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg-card);
  color: var(--color-text-secondary);
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-fast);
}
.tool-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}
.export-word-btn {
  border-color: var(--color-accent);
  background: var(--color-accent-muted);
  color: var(--color-accent);
}
.export-word-btn:hover {
  background: var(--color-accent);
  color: #fff;
}
.export-word-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.preview-loading {
  flex: 1;
  display: grid;
  place-items: center;
  color: var(--color-text-muted);
  font-size: 14px;
}

/* ── Paper wrapper ── */
.paper-wrapper {
  flex: 1;
  overflow-y: auto;
  display: flex;
  justify-content: center;
  padding: 32px 16px;
}

.paper-page {
  background: #fff;
  width: 210mm;
  max-width: 100%;
  padding: 40px 48px;
  box-shadow: 0 2px 24px rgba(0, 0, 0, 0.08);
  border-radius: 4px;
}

/* ── Paper header ── */
.paper-header {
  text-align: center;
  padding-bottom: 24px;
  border-bottom: 2px solid #333;
  margin-bottom: 24px;
}
.paper-title {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 4px;
  color: #111;
  margin-bottom: 4px;
}
.paper-subtitle {
  font-size: 15px;
  color: #555;
  margin-bottom: 8px;
}
.paper-meta {
  display: flex;
  justify-content: center;
  gap: 32px;
  font-size: 13px;
  color: #333;
  margin-bottom: 16px;
}
.paper-info-line {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #333;
  padding-top: 12px;
  border-top: 1px solid #ccc;
}

/* ── Sections & Questions ── */
.paper-section {
  margin-bottom: 28px;
}
.section-title {
  font-size: 16px;
  font-weight: 700;
  color: #111;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ddd;
}
.section-score-hint {
  font-size: 13px;
  font-weight: 400;
  color: #666;
}

.paper-question {
  margin-bottom: 20px;
  page-break-inside: avoid;
}
.pq-header {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 4px;
}
.pq-num {
  font-weight: 700;
  font-size: 14px;
  color: #111;
}
.pq-score {
  font-size: 12px;
  color: #888;
}
.pq-body {
  padding-left: 20px;
  font-size: 14px;
  line-height: 1.8;
  color: #222;
}
.pq-answer-area {
  min-height: 40px;
}

.no-exam {
  flex: 1;
  display: grid;
  place-items: center;
  text-align: center;
  color: var(--color-text-muted);
}
.back-link {
  color: var(--color-accent);
  text-decoration: none;
  margin-top: 12px;
}

/* ── Print styles ── */
@media print {
  .no-print { display: none !important; }
  .preview-view { background: #fff; }
  .paper-wrapper { padding: 0; overflow: visible; }
  .paper-page {
    box-shadow: none;
    border-radius: 0;
    max-width: 100%;
    width: 100%;
    padding: 20mm 15mm;
  }
}
</style>
