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
  padding: 0 10px;
  height: 28px;
  background: linear-gradient(180deg, #edf4ff, #dce9ff);
  border-top: 1px solid #c6d6ee;
  font-size: 0.82rem;
  color: #2a4a82;
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
  background: #af3535;
}
.ws-indicator.connected {
  background: #176b3d;
}

.status-connection {
  border-radius: 999px;
  padding: 2px 8px;
  border: 1px solid #c8d8ef;
  background: #f5f9ff;
  font-size: 0.75rem;
}

.status-stage-summary {
  flex: 1;
  text-align: center;
}

.status-shortcut {
  opacity: 0.6;
  font-size: 0.75rem;
}
</style>
