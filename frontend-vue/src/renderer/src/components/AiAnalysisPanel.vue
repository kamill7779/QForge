<template>
  <div class="ai-analysis-panel">
    <div class="ai-header">
      <h4>AI 分析</h4>
      <button
        v-if="!pending && !result"
        class="ai-trigger-btn"
        :disabled="disabled"
        :title="disabled ? disabledTip : ''"
        @click="emit('requestAnalysis')"
      >
        🤖 AI 分析
      </button>
      <span v-if="pending" class="ai-status pending">
        ⏳ 分析中...
        <button class="ai-cancel-btn" @click="emit('cancelAnalysis')">取消</button>
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
    disabledTip?: string
  }>(),
  {
    pending: false,
    disabled: false,
    disabledTip: ''
  }
)

const emit = defineEmits<{
  requestAnalysis: []
  applyRecommendation: []
  cancelAnalysis: []
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
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  background: var(--color-bg-panel);
}

.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.ai-header h4 {
  margin: 0;
  font-size: 0.85rem;
  color: var(--color-text-primary);
  font-weight: 700;
  letter-spacing: 0.3px;
}

.ai-trigger-btn {
  padding: 5px 14px;
  border: none;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover));
  cursor: pointer;
  font-size: 0.85rem;
  color: #fff;
  font-weight: 600;
  transition: all var(--transition-fast);
  box-shadow: 0 2px 8px var(--color-accent-glow);
}
.ai-trigger-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px var(--color-accent-glow);
}
.ai-trigger-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  transform: none;
}

.ai-status.pending {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
  color: var(--color-warning);
  animation: pulse 1.5s infinite;
}

.ai-cancel-btn {
  padding: 2px 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  font-size: 0.75rem;
  transition: all var(--transition-fast);
}
.ai-cancel-btn:hover {
  color: var(--color-danger);
  border-color: var(--color-danger);
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
  color: var(--color-danger);
  font-size: 0.85rem;
}
.error-icon {
  font-size: 1rem;
}

.ai-suggestion {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
}

.ai-suggestion label {
  color: var(--color-text-muted);
  min-width: 5em;
}

.suggested-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.suggested-tag {
  padding: 2px 8px;
  background: var(--color-accent-muted);
  color: var(--color-accent);
  border-radius: var(--radius-pill);
  font-size: 0.82rem;
  font-weight: 500;
}

.suggested-difficulty {
  font-weight: 600;
}

.ai-reasoning {
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}
.ai-reasoning label {
  display: block;
  margin-bottom: 2px;
  font-weight: 600;
}
.ai-reasoning p {
  margin: 0;
  padding: 6px 10px;
  background: var(--color-bg-panel);
  border: 1px solid var(--color-border-light);
  border-radius: 8px;
  line-height: 1.5;
}

.ai-apply-btn {
  align-self: flex-end;
  padding: 5px 16px;
  border: none;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-success), #00a884);
  color: white;
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 600;
  transition: all var(--transition-fast);
  box-shadow: 0 2px 8px rgba(0, 184, 148, 0.25);
}
.ai-apply-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}
.ai-apply-btn:disabled {
  opacity: 0.4;
  cursor: default;
  transform: none;
}
</style>
