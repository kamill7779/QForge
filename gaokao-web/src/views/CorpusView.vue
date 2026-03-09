<template>
  <div class="corpus-page">
    <section class="paper-list card">
      <div class="panel-head">
        <div>
          <h1 class="page-title">正式语料库</h1>
          <p class="page-subtitle">这里只展示已发布到高考数学正式语料层的数据，不直接进入组卷运行链路。</p>
        </div>
        <button class="btn btn-secondary" @click="refreshCorpus">刷新</button>
      </div>

      <div class="paper-items">
        <button
          v-for="paper in gaokao.corpusPapers"
          :key="paper.paperUuid"
          class="paper-item"
          :class="{ active: gaokao.activeCorpusPaper?.paperUuid === paper.paperUuid }"
          @click="openPaper(paper.paperUuid)"
        >
          <div class="paper-name">{{ paper.paperName }}</div>
          <div class="paper-meta">{{ paper.examYear || '未知年份' }} · {{ paper.provinceCode || '未知省份' }}</div>
        </button>
      </div>
    </section>

    <section class="paper-detail card">
      <template v-if="gaokao.activeCorpusPaper">
        <div class="detail-head">
          <div>
            <span class="chip">{{ gaokao.activeCorpusPaper.status || 'PUBLISHED' }}</span>
            <h2>{{ gaokao.activeCorpusPaper.paperName }}</h2>
            <p>{{ gaokao.activeCorpusPaper.examYear }} · {{ gaokao.activeCorpusPaper.provinceCode }} · {{ gaokao.activeCorpusPaper.subjectCode }}</p>
          </div>
        </div>

        <div class="question-layout">
          <div class="question-list">
            <button
              v-for="question in gaokao.activeCorpusPaper.questions"
              :key="question.questionUuid"
              class="question-item"
              :class="{ active: gaokao.activeCorpusQuestion?.questionUuid === question.questionUuid }"
              @click="openQuestion(question.questionUuid)"
            >
              <div>第 {{ question.questionNo || '?' }} 题</div>
              <small>{{ question.questionTypeCode || '未标题型' }} · 难度 {{ question.difficultyLevel || '-' }}</small>
            </button>
          </div>

          <div class="question-detail" v-if="gaokao.activeCorpusQuestion">
            <div class="detail-meta">
              <span class="chip">{{ gaokao.activeCorpusQuestion.questionTypeCode || '未标题型' }}</span>
              <span class="chip">难度 {{ gaokao.activeCorpusQuestion.difficultyLevel || '-' }}</span>
              <span class="chip">推理步数 {{ gaokao.activeCorpusQuestion.reasoningStepCount ?? '-' }}</span>
            </div>
            <LatexPreview :xml="gaokao.activeCorpusQuestion.stemXml || ''" placeholder="暂无题干 XML" />
            <div class="action-bar">
              <button class="btn btn-primary" @click="materialize">物化到正式题库</button>
            </div>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>

<script setup lang="ts">
import LatexPreview from '@/components/LatexPreview.vue'
import { useGaokaoStore } from '@/stores/gaokao'
import { useNotificationStore } from '@/stores/notification'

const gaokao = useGaokaoStore()
const notif = useNotificationStore()

async function refreshCorpus() {
  try {
    await gaokao.loadCorpus()
  } catch (error) {
    notif.push('error', (error as Error).message)
  }
}

async function openPaper(paperUuid: string) {
  try {
    await gaokao.selectCorpusPaper(paperUuid)
  } catch (error) {
    notif.push('error', (error as Error).message)
  }
}

async function openQuestion(questionUuid: string) {
  try {
    await gaokao.selectCorpusQuestion(questionUuid)
  } catch (error) {
    notif.push('error', (error as Error).message)
  }
}

async function materialize() {
  if (!gaokao.activeCorpusQuestion) return
  try {
    await gaokao.materializeQuestion(gaokao.activeCorpusQuestion.questionUuid)
    notif.push('success', '该高考题已提交物化到正式题库链路')
  } catch (error) {
    notif.push('error', (error as Error).message)
  }
}
</script>

<style scoped>
.corpus-page {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 20px;
  height: 100%;
}

.paper-list,
.paper-detail {
  padding: 22px;
  overflow: auto;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.paper-items,
.question-list {
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.paper-item,
.question-item {
  text-align: left;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid var(--color-border);
  background: var(--color-surface-soft);
}

.paper-item.active,
.question-item.active {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}

.paper-name {
  font-weight: 700;
}

.paper-meta,
.question-item small,
.detail-head p {
  color: var(--color-text-secondary);
}

.detail-head h2 {
  margin: 12px 0 8px;
  font-family: var(--font-family);
}

.question-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 18px;
  margin-top: 18px;
}

.question-detail {
  display: grid;
  gap: 16px;
}

.detail-meta,
.action-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 1200px) {
  .corpus-page,
  .question-layout {
    grid-template-columns: 1fr;
  }
}
</style>