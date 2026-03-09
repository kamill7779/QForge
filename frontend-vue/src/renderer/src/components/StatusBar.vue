<template>
  <footer class="status-bar">
    <span class="status-user">
      <span class="ws-indicator" :class="{ connected: wsConnected }" />
      {{ username || '未登录' }}
    </span>
    <span class="status-connection">
      {{ wsConnected ? '已连接' : '未连接' }}
    </span>
    <span class="status-stage-summary">
      待录题干 {{ stageCounts.PENDING_STEM }} ·
      待录答案 {{ stageCounts.PENDING_ANSWER }} ·
      已完成 {{ stageCounts.COMPLETED }}
    </span>
    <span class="status-shortcut">
      Alt+N 新建 · Alt+S 确认 · Alt+D 完成
    </span>
  </footer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useQuestionStore } from '@/stores/question'
import { useWebSocketStore } from '@/stores/websocket'

const auth = useAuthStore()
const questionStore = useQuestionStore()
const ws = useWebSocketStore()

const username = computed(() => auth.username)
const wsConnected = computed(() => ws.connected)
const stageCounts = computed(() => questionStore.stageCounts)
</script>

<style scoped>
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

.status-user {
  display: flex;
  align-items: center;
  gap: 4px;
}

.ws-indicator {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-danger);
}
.ws-indicator.connected {
  background: var(--color-success);
}

.status-connection {
  border-radius: var(--radius-pill);
  padding: 2px 8px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-card);
  font-size: 0.72rem;
  color: var(--color-text-muted);
}

.status-stage-summary {
  flex: 1;
  text-align: center;
}

.status-shortcut {
  opacity: 0.5;
  font-size: 0.72rem;
}
</style>
