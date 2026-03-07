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
  gap: 16px;
  padding: 0 12px;
  height: 24px;
  background: var(--bg-tertiary);
  border-top: 1px solid var(--border-subtle);
  font-size: 11px;
  color: var(--text-tertiary);
}

.status-user {
  display: flex;
  align-items: center;
  gap: 4px;
}

.ws-indicator {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--danger);
}
.ws-indicator.connected {
  background: var(--success);
}

.status-stage-summary {
  flex: 1;
  text-align: center;
}

.status-shortcut {
  opacity: 0.7;
}
</style>
