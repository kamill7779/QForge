<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="app-title">QForge 录题助手</div>
      <nav class="tab-nav">
        <router-link to="/entry" class="tab-link" active-class="active">录题中心</router-link>
        <router-link to="/bank" class="tab-link" active-class="active">题库</router-link>
        <router-link to="/exam-parse" class="tab-link" active-class="active">试卷解析</router-link>
      </nav>
      <div class="header-actions">
        <span class="username">{{ auth.username }}</span>
        <button class="btn-logout" @click="logout">退出</button>
      </div>
    </header>

    <main class="app-main">
      <router-view />
    </main>

    <StatusBar />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import StatusBar from '@/components/StatusBar.vue'
import { useAuthStore } from '@/stores/auth'
import { useQuestionStore } from '@/stores/question'
import { useTagStore } from '@/stores/tag'
import { useWebSocketStore } from '@/stores/websocket'
import { useExamParseStore } from '@/stores/examParse'
import { useNotificationStore } from '@/stores/notification'

const router = useRouter()
const auth = useAuthStore()
const questionStore = useQuestionStore()
const tagStore = useTagStore()
const wsStore = useWebSocketStore()
const epStore = useExamParseStore()
const notif = useNotificationStore()

/** Auto-save workspace when entries change. */
let saveTimer: ReturnType<typeof setTimeout> | null = null
watch(
  () => questionStore.entries,
  () => {
    if (!auth.username) return
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(() => questionStore.saveWorkspace(auth.username), 1000)
  },
  { deep: true }
)

onMounted(async () => {
  // Step 1: Load Electron config (safe, no redirect needed)
  try {
    await auth.loadConfig()
  } catch (e) {
    notif.log(`读取配置失败: ${e}`)
  }

  // Step 2: Check credentials — only this step triggers redirect to login
  let cred: { username: string; token: string } | null = null
  try {
    cred = await auth.loadSavedCredentials()
  } catch {
    // ignore IPC errors, treat as no credentials
  }
  if (!cred?.token) {
    router.replace('/login')
    return
  }
  auth.restoreSession(cred.username, cred.token)

  // Step 3: Background data loading — failures show as notifications, never redirect
  questionStore.loadWorkspace(auth.username)
  epStore.loadLocalState(auth.username)

  try {
    await Promise.all([
      tagStore.fetchCatalog(auth.token),
      questionStore.syncQuestions(auth.token)
    ])
  } catch (e) {
    notif.log(`数据同步失败: ${e}`)
  }

  // Step 4: WebSocket — failure is non-critical
  try {
    wsStore.connect(auth.config.wsBaseUrl, auth.username, auth.token)
  } catch (e) {
    notif.log(`WebSocket 连接失败: ${e}`)
  }

  notif.log('欢迎回来，' + auth.username)
})

onBeforeUnmount(() => {
  if (saveTimer) clearTimeout(saveTimer)
  wsStore.disconnect()
})

async function logout() {
  wsStore.disconnect()
  questionStore.saveWorkspace(auth.username)
  epStore.saveLocalState(auth.username)
  questionStore.$reset()
  epStore.$reset()
  tagStore.$reset()
  notif.$reset()
  await auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.app-header {
  display: flex;
  align-items: center;
  padding: 0 16px;
  height: 48px;
  background: var(--color-bg-header);
  color: var(--color-text-header);
  gap: 16px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--color-border);
}

.app-title {
  font-size: 16px;
  font-weight: 600;
}

.tab-nav {
  display: flex;
  gap: 4px;
  margin-left: 24px;
}

.tab-link {
  padding: 6px 16px;
  border-radius: 6px;
  color: var(--color-text-secondary);
  text-decoration: none;
  font-size: 14px;
  transition: background 0.15s, color 0.15s;
}

.tab-link:hover {
  background: rgba(255, 255, 255, 0.08);
}

.tab-link.active {
  background: var(--color-accent);
  color: #fff;
}

.header-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.btn-logout {
  padding: 4px 12px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  background: transparent;
  color: #e0e0e0;
  cursor: pointer;
  font-size: 12px;
}

.btn-logout:hover {
  background: rgba(255, 255, 255, 0.08);
}

.app-main {
  flex: 1;
  overflow: hidden;
}
</style>
