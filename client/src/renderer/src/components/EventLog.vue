<template>
  <div class="event-log">
    <div class="log-header">
      <h4>系统消息</h4>
      <button v-if="logs.length" class="clear-btn" @click="emit('clear')">清空</button>
    </div>
    <ul class="log-list">
      <li v-for="(entry, i) in logs" :key="i" class="log-item">
        <span class="log-ts">{{ entry.ts }}</span>
        <span class="log-msg">{{ entry.msg }}</span>
      </li>
      <li v-if="!logs.length" class="log-empty">暂无消息</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import type { LogEntry } from '@/stores/notification'

defineProps<{
  logs: LogEntry[]
}>()

const emit = defineEmits<{
  clear: []
}>()
</script>

<style scoped>
.event-log {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-card);
  overflow: hidden;
  box-shadow: var(--shadow-soft);
}

.log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-border-light);
  background: var(--color-bg-panel);
}

.log-header h4 {
  margin: 0;
  font-size: 0.82rem;
  color: var(--color-text-primary);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.clear-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 0.82rem;
  color: var(--color-text-muted);
  padding: 2px 6px;
  transition: color var(--transition-fast);
}
.clear-btn:hover {
  color: var(--color-danger);
}

.log-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 200px;
  min-height: 60px;
  overflow-y: auto;
}

.log-item {
  padding: 4px 12px;
  font-size: 0.82rem;
  border-bottom: 1px solid var(--color-border-light);
  display: flex;
  gap: 8px;
}
.log-item:last-child {
  border-bottom: none;
}

.log-ts {
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  white-space: nowrap;
  font-size: 0.78rem;
}

.log-msg {
  color: var(--color-text-secondary);
}

.log-empty {
  padding: 12px 10px;
  font-size: 0.82rem;
  color: var(--color-text-muted);
  text-align: center;
  font-style: italic;
}
</style>
