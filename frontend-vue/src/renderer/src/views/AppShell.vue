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
  overflow: hidden;
}

.app-header {
  height: 62px;
  border-bottom: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 16px;
  flex-shrink: 0;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-mark {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--color-accent);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
}

.app-title {
  font-size: 33px;
  line-height: 1;
  font-weight: 800;
  letter-spacing: 0.3px;
}

.tab-nav {
  display: flex;
  gap: 4px;
  margin-left: 8px;
}

.tab-link {
  border: 1px solid transparent;
  border-radius: 8px;
  padding: 6px 14px;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  cursor: pointer;
  transition: all 0.15s ease;
}

.tab-link:hover {
  background: #eaf0ff;
  color: var(--color-accent);
  text-decoration: none;
}

.tab-link.active {
  background: #e5eeff;
  color: var(--color-accent);
  font-weight: 600;
  border-color: var(--color-accent);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.user-pill {
  border-radius: 999px;
  background: #ecf2ff;
  color: #27488c;
  padding: 4px 10px;
}

.btn-logout {
  border: 1px solid #c8d5eb;
  border-radius: 10px;
  padding: 4px 12px;
  background: transparent;
  color: #274989;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
}

.btn-logout:hover {
  filter: brightness(0.98);
  background: #e9f0ff;
}

.app-main {
  flex: 1;
  overflow: hidden;
}
</style>
