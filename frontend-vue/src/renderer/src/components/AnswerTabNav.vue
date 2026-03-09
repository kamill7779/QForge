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
  gap: 6px;
  flex-wrap: wrap;
}

.answer-tab {
  padding: 4px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: transparent;
  cursor: pointer;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
  transition: all var(--transition-fast);
}
.answer-tab.active {
  border-color: var(--color-accent);
  background: var(--color-accent-muted);
  color: var(--color-accent);
  font-weight: 600;
}
.answer-tab:hover:not(.active) {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

.tab-pagination {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.82rem;
  color: var(--color-text-muted);
}

.nav-btn {
  background: none;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  cursor: pointer;
  padding: 2px 8px;
  font-size: 1rem;
  color: var(--color-text-secondary);
  transition: all var(--transition-fast);
}
.nav-btn:disabled {
  opacity: 0.3;
  cursor: default;
}
.nav-btn:hover:not(:disabled) {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: var(--color-accent-muted);
}
</style>
