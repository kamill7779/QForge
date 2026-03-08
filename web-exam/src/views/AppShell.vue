<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="topbar-left">
        <div class="logo-mark">Q</div>
        <div class="app-title">QForge</div>
        <nav class="tab-nav">
          <router-link to="/bank" class="tab-link" active-class="active">题库</router-link>
          <router-link to="/exams" class="tab-link" active-class="active">我的试卷</router-link>
          <router-link to="/compose" class="tab-link" active-class="active">组卷</router-link>
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

    <footer class="status-bar">
      <span class="status-user">{{ auth.username || '未登录' }}</span>
      <span class="status-info">
        题库 {{ questionStore.totalCount }} 题 ·
        试卷 {{ examStore.exams.length }} 份
      </span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useQuestionStore } from '@/stores/question'
import { useTagStore } from '@/stores/tag'
import { useExamStore } from '@/stores/exam'
import { useNotificationStore } from '@/stores/notification'
import { registerOn401, unregisterOn401 } from '@/api/client'

const router = useRouter()
const auth = useAuthStore()
const questionStore = useQuestionStore()
const tagStore = useTagStore()
const examStore = useExamStore()
const notif = useNotificationStore()

// 401 interceptor: redirect to login on expired token
let redirecting = false
registerOn401(() => {
  if (redirecting) return
  redirecting = true
  notif.log('登录已过期，请重新登录')
  auth.logout()
  router.replace('/login')
})

// Restore session SYNCHRONOUSLY before any child component mounts
const cred = auth.loadSavedCredentials()
if (cred?.token) {
  auth.restoreSession(cred.username, cred.token)
}

onMounted(async () => {
  if (!auth.isLoggedIn) {
    router.replace('/login')
    return
  }

  // Load data after auth is ready
  try {
    await Promise.all([
      tagStore.fetchCatalog(auth.token),
      questionStore.fetchQuestions()
    ])
  } catch (e) {
    notif.log(`数据同步失败: ${e}`)
  }

  notif.log('欢迎回来，' + auth.username)
})

function logout() {
  questionStore.$reset()
  tagStore.$reset()
  notif.$reset()
  auth.logout()
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
  flex-shrink: 0;
}
.app-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
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
.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 14px;
  height: 28px;
  background: var(--color-bg-footer);
  border-top: 1px solid var(--color-border);
  font-size: 0.78rem;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}
.status-info { flex: 1; text-align: center; }
</style>
