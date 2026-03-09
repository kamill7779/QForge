import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export interface NotificationEntry {
  id: number
  level: 'info' | 'success' | 'warning' | 'error'
  message: string
}

let seed = 1

export const useNotificationStore = defineStore('notification', () => {
  const items = ref<NotificationEntry[]>([])

  function push(level: NotificationEntry['level'], message: string) {
    const entry = { id: seed++, level, message }
    items.value.unshift(entry)
    if (items.value.length > 6) {
      items.value.length = 6
    }
    window.setTimeout(() => {
      remove(entry.id)
    }, 3200)
  }

  function remove(id: number) {
    items.value = items.value.filter((item) => item.id !== id)
  }

  const recent = computed(() => items.value)

  return { recent, push, remove }
})