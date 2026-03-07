<template>
  <div class="ai-analysis-panel">
    <div class="ai-header">
      <h4>AI 分析</h4>
      <button
        v-if="!pending && !result"
        class="ai-trigger-btn"
        :disabled="disabled"
        @click="emit('requestAnalysis')"
      >
        🤖 AI 分析
      </button>
      <span v-if="pending" class="ai-status pending">
        ⏳ 分析中...
      </span>
    </div>

    <div v-if="result" class="ai-result" :class="{ error: !result.status || result.status === 'FAILED' }">
      <!-- Error case -->
      <div v-if="result.status === 'FAILED'" class="ai-error">
        <span class="error-icon">⚠</span>
        <span>{{ result.errorMessage || '分析失败' }}</span>
      </div>

      <!-- Success case -->
      <template v-else>
        <!-- Suggested tags -->
        <div v-if="result.suggestedTags?.length" class="ai-suggestion">
          <label>推荐标签:</label>
          <div class="suggested-tags">
            <span
              v-for="tag in result.suggestedTags"
              :key="tag"
              class="suggested-tag"
            >{{ tagStore.getTagName(tag) }}</span>
          </div>
        </div>

        <!-- Suggested difficulty -->
        <div v-if="result.suggestedDifficulty !== null && result.suggestedDifficulty !== undefined" class="ai-suggestion">
          <label>推荐难度:</label>
          <span class="suggested-difficulty" :class="diffLevel.cssClass">
            {{ Math.round(result.suggestedDifficulty * 100) }}
            ({{ diffLevel.label }})
          </span>
        </div>

        <!-- Reasoning -->
        <div v-if="result.reasoning" class="ai-reasoning">
          <label>推理依据:</label>
          <p>{{ result.reasoning }}</p>
        </div>

        <!-- Apply button -->
        <button
          v-if="result.status === 'SUCCESS'"
          class="ai-apply-btn"
          :disabled="disabled"
          @click="emit('applyRecommendation')"
        >
          ✅ 采纳推荐
        </button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useTagStore } from '@/stores/tag'
import { difficultyLevel } from '@/lib/difficulty'
import type { AiTaskResponse } from '@/api/types'

const props = withDefaults(
  defineProps<{
    pending?: boolean
    result?: AiTaskResponse | null
    disabled?: boolean
  }>(),
  {
    pending: false,
    disabled: false
  }
)

const emit = defineEmits<{
  requestAnalysis: []
  applyRecommendation: []
}>()

const tagStore = useTagStore()

const diffLevel = computed(() => {
  const d = props.result?.suggestedDifficulty
  if (d === null || d === undefined) return { label: '', cssClass: '' }
  return difficultyLevel(Math.round(d * 100))
})
</script>

<style scoped>
.ai-analysis-panel {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--bg-secondary);
}

.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.ai-header h4 {
  margin: 0;
  font-size: 14px;
  color: var(--text-primary);
}

.ai-trigger-btn {
  padding: 4px 12px;
  border: 1px solid var(--accent-primary);
  border-radius: var(--radius-sm);
  background: var(--bg-primary);
  cursor: pointer;
  font-size: 13px;
  color: var(--accent-primary);
  transition: all 0.15s;
}
.ai-trigger-btn:hover:not(:disabled) {
  background: var(--accent-primary);
  color: white;
}
.ai-trigger-btn:disabled {
  opacity: 0.4;
  cursor: default;
}

.ai-status.pending {
  font-size: 13px;
  color: var(--warning);
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.ai-result {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ai-error {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--danger);
  font-size: 13px;
}
.error-icon {
  font-size: 16px;
}

.ai-suggestion {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.ai-suggestion label {
  color: var(--text-secondary);
  min-width: 5em;
}

.suggested-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.suggested-tag {
  padding: 1px 8px;
  background: color-mix(in srgb, var(--accent-primary) 12%, transparent);
  color: var(--accent-primary);
  border-radius: 10px;
  font-size: 12px;
}

.suggested-difficulty {
  font-weight: 600;
}

.ai-reasoning {
  font-size: 12px;
  color: var(--text-secondary);
}
.ai-reasoning label {
  display: block;
  margin-bottom: 2px;
}
.ai-reasoning p {
  margin: 0;
  padding: 4px 8px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-sm);
  line-height: 1.5;
}

.ai-apply-btn {
  align-self: flex-end;
  padding: 4px 16px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--success);
  color: white;
  cursor: pointer;
  font-size: 13px;
  transition: opacity 0.15s;
}
.ai-apply-btn:hover:not(:disabled) {
  opacity: 0.85;
}
.ai-apply-btn:disabled {
  opacity: 0.4;
  cursor: default;
}
</style>
