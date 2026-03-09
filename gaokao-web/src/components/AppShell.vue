<template>
  <div class="app-shell">
    <aside class="side-nav">
      <div class="brand-block">
        <div class="brand-mark">高</div>
        <div>
          <div class="brand-title">QForge</div>
          <div class="brand-subtitle">高考数学录题台</div>
        </div>
      </div>

      <nav class="nav-list">
        <router-link to="/sessions" class="nav-item" active-class="active">整卷录入</router-link>
        <router-link to="/corpus" class="nav-item" active-class="active">正式语料</router-link>
        <router-link to="/photo-query" class="nav-item" active-class="active">单题拍照检索</router-link>
      </nav>

      <div class="side-footer">
        <div class="user-card">
          <div class="user-label">当前用户</div>
          <div class="user-name">{{ auth.username }}</div>
        </div>
        <button class="btn btn-secondary" @click="logout">退出登录</button>
      </div>
    </aside>

    <section class="main-shell">
      <header class="top-strip">
        <div>
          <div class="strip-title">高考数学正式录题工作台</div>
          <div class="strip-subtitle">草稿层、正式语料层、正式组卷层严格隔离</div>
        </div>
        <div class="strip-pills">
          <span class="chip">录入会话 {{ gaokao.sessions.length }}</span>
          <span class="chip">正式语料 {{ gaokao.corpusTotal }}</span>
          <span class="chip">{{ importMetaEnvLabel }}</span>
        </div>
      </header>

      <main class="content-shell">
        <router-view />
      </main>

      <div class="toast-stack">
        <div v-for="item in notif.recent" :key="item.id" class="toast" :class="item.level">
          {{ item.message }}
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { registerOn401, unregisterOn401 } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useGaokaoStore } from '@/stores/gaokao'
import { useNotificationStore } from '@/stores/notification'

const router = useRouter()
const auth = useAuthStore()
const gaokao = useGaokaoStore()
const notif = useNotificationStore()

const importMetaEnvLabel = computed(() =>
  import.meta.env.VITE_GAOKAO_MOCK === 'true' ? 'Mock 接口模式' : '真实接口模式'
)

registerOn401(() => {
  auth.logout()
  notif.push('warning', '登录状态已失效，请重新登录')
  router.replace('/login')
})

onMounted(async () => {
  try {
    await Promise.all([gaokao.refreshSessions(), gaokao.loadCorpus()])
  } catch (error) {
    notif.push('warning', `初始化数据失败: ${(error as Error).message}`)
  }
})

onUnmounted(() => {
  unregisterOn401()
})

function logout() {
  auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  height: 100%;
}

.side-nav {
  display: flex;
  flex-direction: column;
  padding: 26px;
  background: linear-gradient(180deg, #f7f0e4, #efe4d2);
  border-right: 1px solid rgba(199, 180, 158, 0.75);
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-mark {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  border-radius: 18px;
  background: linear-gradient(135deg, var(--color-accent), #2b8d91);
  color: #fff;
  font-family: var(--font-family);
  font-size: 24px;
}

.brand-title {
  font-family: var(--font-family);
  font-size: 24px;
}

.brand-subtitle {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.nav-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 34px;
}

.nav-item {
  padding: 14px 16px;
  border-radius: 16px;
  color: var(--color-text-secondary);
  background: rgba(255, 255, 255, 0.45);
  transition: all var(--transition-fast);
}

.nav-item:hover,
.nav-item.active {
  color: #fff;
  background: linear-gradient(135deg, var(--color-accent), #19777b);
  box-shadow: var(--shadow-card);
}

.side-footer {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.user-card {
  padding: 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.58);
}

.user-label {
  font-size: 12px;
  color: var(--color-text-muted);
}

.user-name {
  margin-top: 6px;
  font-weight: 700;
}

.main-shell {
  min-width: 0;
  display: flex;
  flex-direction: column;
  position: relative;
}

.top-strip {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 26px 14px;
}

.strip-title {
  font-family: var(--font-family);
  font-size: 22px;
}

.strip-subtitle {
  margin-top: 6px;
  color: var(--color-text-secondary);
}

.strip-pills {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-wrap: wrap;
}

.content-shell {
  flex: 1;
  min-height: 0;
  padding: 0 26px 26px;
}

.toast-stack {
  position: fixed;
  top: 18px;
  right: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.toast {
  min-width: 220px;
  max-width: 360px;
  padding: 12px 14px;
  border-radius: 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-card);
}

.toast.success { border-color: rgba(45, 125, 70, 0.25); }
.toast.warning { border-color: rgba(163, 107, 20, 0.3); }
.toast.error { border-color: rgba(179, 58, 58, 0.3); }

@media (max-width: 1080px) {
  .app-shell {
    grid-template-columns: 1fr;
  }

  .side-nav {
    border-right: 0;
    border-bottom: 1px solid rgba(199, 180, 158, 0.75);
  }
}
</style>