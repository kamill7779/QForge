<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="topbar-left">
        <div class="logo-mark">Q</div>
        <div class="app-title">QForge</div>
        <nav class="tab-nav">
          <router-link to="/entry" class="tab-link" active-class="active">录题中心</router-link>
          <router-link to="/bank" class="tab-link" active-class="active">题库</router-link>
          <router-link to="/exam-parse" class="tab-link" active-class="active">试卷解析</router-link>
        </nav>
      </div>
      <div class="header-actions">
        <span class="user-pill">{{ auth.username }}</span>
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

/**
 * Auto-save workspace when entries change.
 * Watch the map SIZE + dirtyCounter as lightweight proxies — avoids expensive
 * deep traversal of all entries (which may include large base64 image data).
 */
let saveTimer: ReturnType<typeof setTimeout> | null = null
watch(
  () => questionStore.entries.size + questionStore.dirtyCounter,
  () => {
    if (!auth.username) return
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(() => questionStore.saveWorkspace(auth.username), 2000)
  }
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
  overflow: hidden;
  background: var(--color-bg-primary);
}

.app-header {
  height: 52px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-header);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  flex-shrink: 0;
  position: relative;
  z-index: 100;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-mark {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--color-accent), #a29bfe);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 15px;
  letter-spacing: -0.5px;
  flex-shrink: 0;
}

.app-title {
  font-family: var(--font-display);
  font-size: 18px;
  line-height: 1;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: var(--color-text-primary);
}

.tab-nav {
  display: flex;
  gap: 2px;
  margin-left: 16px;
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md);
  padding: 3px;
}

.tab-link {
  border: none;
  border-radius: 7px;
  padding: 5px 18px;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 500;
  text-decoration: none;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.tab-link:hover {
  background: var(--color-bg-hover);
  color: var(--color-accent);
  text-decoration: none;
}

.tab-link.active {
  background: var(--color-accent);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(var(--color-accent-rgb), 0.25);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.user-pill {
  border-radius: var(--radius-pill);
  background: var(--color-bg-secondary);
  color: var(--color-text-secondary);
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 500;
}

.btn-logout {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 4px 12px;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  transition: all var(--transition-fast);
}

.btn-logout:hover {
  background: var(--color-danger-bg);
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.app-main {
  flex: 1;
  overflow: hidden;
  display: flex;
  min-height: 0;
}

.app-main :deep(> *) {
  flex: 1;
  min-height: 0;
}
</style>
