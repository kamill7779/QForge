<template>
  <div class="sessions-page">
    <section class="sessions-sidebar card">
      <div class="sidebar-head">
        <div>
          <h1 class="page-title">整卷录入</h1>
          <p class="page-subtitle">创建录入会话、上传整卷文件、触发 OCR + 分题，再进入草稿层修订。</p>
        </div>
        <button class="btn btn-primary" @click="createSession">新建会话</button>
      </div>

      <div class="session-list">
        <button
          v-for="session in gaokao.sessions"
          :key="session.sessionUuid"
          class="session-item"
          :class="{ active: gaokao.activeSession?.sessionUuid === session.sessionUuid }"
          @click="openSession(session.sessionUuid)"
        >
          <div class="session-title">{{ session.paperNameGuess || session.sessionUuid.slice(0, 12) }}</div>
          <div class="session-meta">{{ session.status }} · {{ formatDate(session.updatedAt) }}</div>
        </button>
      </div>
    </section>

    <section class="session-main card">
      <template v-if="gaokao.activeSession">
        <div class="detail-head">
          <div>
            <div class="chip">{{ gaokao.activeSession.status }}</div>
            <h2>{{ gaokao.activeSession.paperNameGuess || '未命名录入会话' }}</h2>
            <p>
              学科 {{ gaokao.activeSession.subjectCode || 'MATH' }} · 预计年份 {{ gaokao.activeSession.examYearGuess || '待识别' }} ·
              省份 {{ gaokao.activeSession.provinceCodeGuess || '待识别' }}
            </p>
          </div>
          <div class="head-actions">
            <label class="btn btn-secondary upload-btn">
              选择文件
              <input type="file" multiple accept=".pdf,.png,.jpg,.jpeg" hidden @change="onFileChange" />
            </label>
            <button class="btn btn-secondary" :disabled="selectedFiles.length === 0 || busy" @click="uploadFiles">
              上传 {{ selectedFiles.length > 0 ? `(${selectedFiles.length})` : '' }}
            </button>
            <button class="btn btn-warm" :disabled="busy" @click="triggerOcr">OCR + 分题</button>
            <button class="btn btn-primary" :disabled="busy" @click="enterWorkbench">打开草稿台</button>
          </div>
        </div>

        <div v-if="selectedFiles.length > 0" class="file-strip">
          <span v-for="file in selectedFiles" :key="file.name + file.size" class="chip">{{ file.name }}</span>
        </div>

        <div class="detail-grid">
          <article class="detail-card">
            <h3>业务状态</h3>
            <ul>
              <li>原始文件进入录入草稿态，不直接进入正式题库。</li>
              <li>OCR 与分题结束后才可加载草稿整卷。</li>
              <li>正式发布与物化是两个独立动作。</li>
            </ul>
          </article>

          <article class="detail-card">
            <h3>当前会话信息</h3>
            <dl>
              <div><dt>会话 UUID</dt><dd>{{ gaokao.activeSession.sessionUuid }}</dd></div>
              <div><dt>创建时间</dt><dd>{{ formatDate(gaokao.activeSession.createdAt) }}</dd></div>
              <div><dt>更新时间</dt><dd>{{ formatDate(gaokao.activeSession.updatedAt) }}</dd></div>
              <div><dt>源文件数</dt><dd>{{ gaokao.activeSession.sourceFileUuids.length }}</dd></div>
            </dl>
          </article>
        </div>
      </template>

      <div v-else class="empty-panel">当前没有录入会话，先创建一个会话开始。</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useGaokaoStore } from '@/stores/gaokao'
import { useNotificationStore } from '@/stores/notification'

const router = useRouter()
const gaokao = useGaokaoStore()
const notif = useNotificationStore()

const selectedFiles = ref<File[]>([])
const busy = ref(false)

function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN')
}

async function createSession() {
  busy.value = true
  try {
    const session = await gaokao.createSession()
    notif.push('success', '录入会话已创建')
    router.replace(`/sessions/${session.sessionUuid}`)
  } catch (error) {
    notif.push('error', (error as Error).message)
  } finally {
    busy.value = false
  }
}

async function openSession(sessionUuid: string) {
  try {
    await gaokao.selectSession(sessionUuid)
  } catch (error) {
    notif.push('error', (error as Error).message)
  }
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFiles.value = input.files ? Array.from(input.files) : []
}

async function uploadFiles() {
  busy.value = true
  try {
    await gaokao.uploadFiles(selectedFiles.value)
    notif.push('success', '源文件已上传')
    selectedFiles.value = []
  } catch (error) {
    notif.push('error', (error as Error).message)
  } finally {
    busy.value = false
  }
}

async function triggerOcr() {
  busy.value = true
  try {
    await gaokao.triggerOcrSplit()
    notif.push('success', 'OCR + 分题已触发')
  } catch (error) {
    notif.push('error', (error as Error).message)
  } finally {
    busy.value = false
  }
}

function enterWorkbench() {
  if (!gaokao.activeSession) return
  router.push(`/sessions/${gaokao.activeSession.sessionUuid}`)
}
</script>

<style scoped>
.sessions-page {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 20px;
  height: 100%;
}

.sessions-sidebar,
.session-main {
  padding: 22px;
  overflow: auto;
}

.sidebar-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.session-list {
  display: grid;
  gap: 12px;
  margin-top: 20px;
}

.session-item {
  text-align: left;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 18px;
  background: var(--color-surface-soft);
}

.session-item.active {
  border-color: var(--color-accent);
  background: linear-gradient(135deg, rgba(13, 107, 111, 0.08), rgba(255, 255, 255, 0.9));
}

.session-title {
  font-weight: 700;
}

.session-meta {
  margin-top: 6px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
}

.detail-head h2 {
  margin: 12px 0 8px;
  font-family: var(--font-family);
  font-size: 28px;
}

.detail-head p {
  margin: 0;
  color: var(--color-text-secondary);
}

.head-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.file-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 22px;
}

.detail-card {
  padding: 18px;
  border-radius: 20px;
  background: var(--color-surface-soft);
}

.detail-card h3 {
  margin-top: 0;
}

.detail-card ul {
  padding-left: 18px;
  color: var(--color-text-secondary);
  line-height: 1.8;
}

dl {
  display: grid;
  gap: 10px;
}

dl div {
  display: flex;
  justify-content: space-between;
  gap: 14px;
}

dt {
  color: var(--color-text-muted);
}

.empty-panel {
  display: grid;
  place-items: center;
  height: 100%;
  color: var(--color-text-secondary);
}

@media (max-width: 1200px) {
  .sessions-page {
    grid-template-columns: 1fr;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>