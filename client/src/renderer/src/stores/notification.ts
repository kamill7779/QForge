/**
 * stores/notification.ts — Notification / log state.
 *
 * Manages event log entries and status bar text.
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface LogEntry {
  ts: string
  msg: string
}

function timeStr(): string {
  const d = new Date()
  return [d.getHours(), d.getMinutes(), d.getSeconds()]
    .map((n) => String(n).padStart(2, '0'))
    .join(':')
}

export const useNotificationStore = defineStore('notification', () => {
  // ── State ──

  const logs = ref<LogEntry[]>([])
  const maxLogs = 200

  // ── Getters ──

  const recentLogs = computed(() => logs.value.slice(0, 50))

  // ── Actions ──

  /** Append a log entry. Most recent first. */
  function log(msg: string): void {
    logs.value.unshift({ ts: timeStr(), msg })
    if (logs.value.length > maxLogs) {
      logs.value.length = maxLogs
    }
  }

  /** Clear all logs. */
  function clear(): void {
    logs.value = []
  }

  function $reset(): void {
    logs.value = []
  }

  return {
    logs,
    recentLogs,
    log,
    clear,
    $reset
  }
})
