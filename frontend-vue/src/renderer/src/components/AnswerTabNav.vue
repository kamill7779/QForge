<template>
  <div class="answer-tab-nav">
    <div class="tab-buttons">
      <button
        v-for="(_, i) in answers"
        :key="i"
        class="answer-tab"
        :class="{ active: i === modelValue }"
        @click="emit('update:modelValue', i)"
      >解法{{ i + 1 }}</button>
    </div>
    <div class="tab-pagination">
      <button
        class="nav-btn"
        :disabled="modelValue <= 0"
        @click="emit('update:modelValue', modelValue - 1)"
      >‹</button>
      <span class="page-info">{{ modelValue + 1 }} / {{ answers.length }}</span>
      <button
        class="nav-btn"
        :disabled="modelValue >= answers.length - 1"
        @click="emit('update:modelValue', modelValue + 1)"
      >›</button>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    answers: string[]
    modelValue: number
  }>(),
  {}
)

const emit = defineEmits<{
  'update:modelValue': [index: number]
}>()
</script>

<style scoped>
.answer-tab-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;
}

.tab-buttons {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.answer-tab {
  padding: 3px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--bg-primary);
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  transition: all 0.15s;
}
.answer-tab.active {
  background: var(--accent-primary);
  color: white;
  border-color: var(--accent-primary);
}
.answer-tab:hover:not(.active) {
  border-color: var(--accent-primary);
}

.tab-pagination {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.nav-btn {
  background: none;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  cursor: pointer;
  padding: 1px 8px;
  font-size: 16px;
  color: var(--text-secondary);
}
.nav-btn:disabled {
  opacity: 0.3;
  cursor: default;
}
.nav-btn:hover:not(:disabled) {
  border-color: var(--accent-primary);
}
</style>
