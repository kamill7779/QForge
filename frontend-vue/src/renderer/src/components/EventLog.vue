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
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  overflow: hidden;
}

.log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  border-bottom: 1px solid var(--border-subtle);
}

.log-header h4 {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.clear-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 12px;
  color: var(--text-tertiary);
  padding: 2px 6px;
}
.clear-btn:hover {
  color: var(--danger);
}

.log-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 200px;
  overflow-y: auto;
}

.log-item {
  padding: 3px 10px;
  font-size: 12px;
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  gap: 8px;
}
.log-item:last-child {
  border-bottom: none;
}

.log-ts {
  color: var(--text-tertiary);
  font-family: monospace;
  white-space: nowrap;
}

.log-msg {
  color: var(--text-secondary);
}

.log-empty {
  padding: 12px 10px;
  font-size: 12px;
  color: var(--text-tertiary);
  text-align: center;
  font-style: italic;
}
</style>
