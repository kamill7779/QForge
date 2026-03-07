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
  gap: 8px;
  flex-wrap: wrap;
}

.answer-tab {
  padding: 4px 10px;
  border: 1px solid #cbd9f0;
  border-radius: 999px;
  background: #f6f9ff;
  cursor: pointer;
  font-size: 0.82rem;
  color: #38578f;
  transition: all 0.15s;
}
.answer-tab.active {
  border-color: var(--color-accent);
  background: #e8efff;
  color: #1f4cb1;
  font-weight: 600;
}
.answer-tab:hover:not(.active) {
  border-color: #b8c9e6;
  background: #eef3ff;
}

.tab-pagination {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.82rem;
  color: #6a7fa0;
}

.nav-btn {
  background: none;
  border: 1px solid #cbd9f0;
  border-radius: 999px;
  cursor: pointer;
  padding: 2px 8px;
  font-size: 1rem;
  color: #38578f;
}
.nav-btn:disabled {
  opacity: 0.3;
  cursor: default;
}
.nav-btn:hover:not(:disabled) {
  border-color: var(--color-accent);
  background: #eef3ff;
}
</style>
