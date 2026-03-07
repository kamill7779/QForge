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
  border: 1px solid #d2ddf1;
  border-radius: 10px;
  padding: 10px 14px;
  background: #f7faff;
}

.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.ai-header h4 {
  margin: 0;
  font-size: 0.88rem;
  color: #1f355c;
}

.ai-trigger-btn {
  padding: 5px 14px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #7c3aed, #5b21b6);
  cursor: pointer;
  font-size: 0.85rem;
  color: #fff;
  font-weight: 600;
  transition: filter 0.15s;
}
.ai-trigger-btn:hover:not(:disabled) {
  filter: brightness(1.08);
}
.ai-trigger-btn:disabled {
  background: #c4b5fd;
  cursor: not-allowed;
}

.ai-status.pending {
  font-size: 0.85rem;
  color: #8c6306;
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
  color: #af3535;
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
  color: #6a7fa0;
  min-width: 5em;
}

.suggested-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.suggested-tag {
  padding: 2px 8px;
  background: #e8efff;
  color: #2a4f99;
  border-radius: 999px;
  font-size: 0.82rem;
}

.suggested-difficulty {
  font-weight: 600;
}

.ai-reasoning {
  font-size: 0.82rem;
  color: #3a5b97;
}
.ai-reasoning label {
  display: block;
  margin-bottom: 2px;
}
.ai-reasoning p {
  margin: 0;
  padding: 4px 8px;
  background: #eef3ff;
  border-radius: 8px;
  line-height: 1.5;
}

.ai-apply-btn {
  align-self: flex-end;
  padding: 5px 16px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #22a358, #176b3d);
  color: white;
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 600;
  transition: filter 0.15s;
}
.ai-apply-btn:hover:not(:disabled) {
  filter: brightness(1.08);
}
.ai-apply-btn:disabled {
  opacity: 0.4;
  cursor: default;
}
</style>
