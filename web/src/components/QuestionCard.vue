<template>
  <div class="question-card" :class="{ selected: isSelected }">
    <div class="qc-header">
      <div class="qc-badges">
        <span v-if="questionType" class="qc-type-badge">{{ questionType }}</span>
        <span v-if="entry.difficulty !== null" class="qc-diff-badge" :class="diffCss">
          {{ diffText }}
        </span>
        <span class="qc-answer-count">{{ entry.answerCount }} 个解法</span>
      </div>
      <div class="qc-meta-right">
        <span class="qc-time">{{ formattedTime }}</span>
      </div>
    </div>

    <div class="qc-body" @click="emit('toggleDetail')">
      <span class="qc-seq">{{ seq }}.</span>
      <div class="qc-stem-wrap">
        <LatexPreview
          :xml="entry.stemText"
          :image-resolver="imageResolver"
          :render-key="renderKey"
          compact
          class="qc-stem-preview"
        />
      </div>
    </div>

    <div v-if="hasTags" class="qc-tags-row">
      <span v-for="tag in entry.mainTags" :key="tag.tagCode" class="qc-tag main-tag">{{ tag.tagName }}</span>
      <span v-for="st in entry.secondaryTags" :key="st" class="qc-tag secondary-tag">{{ st }}</span>
    </div>

    <div class="qc-footer">
      <div class="qc-actions-left">
        <span class="qc-uuid-label">{{ entry.questionUuid.slice(0, 8) }}</span>
        <span v-if="entry.source && entry.source !== '未分类'" class="qc-source-badge">{{ entry.source }}</span>
      </div>
      <div class="qc-actions-right">
        <button class="qc-action-btn" @click="emit('toggleDetail')">详情</button>
        <button
          class="qc-action-btn primary"
          :class="{ active: isSelected }"
          @click="emit('toggleSelect')"
        >
          {{ isSelected ? '✓ 已选' : '+ 选题' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { QuestionEntry } from '@/stores/question'
import LatexPreview from './LatexPreview.vue'
import { difficultyLevel } from '@/lib/difficulty'
import { QUESTION_TYPES } from '@/lib/questionType'

const props = defineProps<{
  entry: QuestionEntry
  seq: number
  isSelected?: boolean
  imageResolver?: (ref: string) => string
  renderKey?: number
}>()

const emit = defineEmits<{
  toggleDetail: []
  toggleSelect: []
}>()

const diffText = computed(() =>
  props.entry.difficulty !== null ? difficultyLevel(props.entry.difficulty).label : ''
)

const diffCss = computed(() =>
  props.entry.difficulty !== null ? difficultyLevel(props.entry.difficulty).cssClass : ''
)

const questionType = computed(() => {
  for (const tag of props.entry.secondaryTags) {
    const upper = tag.toUpperCase().replace(/\s+/g, '_')
    if (QUESTION_TYPES[upper]) return QUESTION_TYPES[upper]
    for (const [, label] of Object.entries(QUESTION_TYPES)) {
      if (tag.includes(label)) return label
    }
  }
  const stem = props.entry.stemText
  if (stem.includes('<choices')) return '选择题'
  if (stem.includes('<blanks')) return '填空题'
  if (stem.includes('<answer-area')) return '解答题'
  return ''
})

const hasTags = computed(() =>
  props.entry.mainTags.length > 0 || props.entry.secondaryTags.length > 0
)

const formattedTime = computed(() => {
  if (!props.entry.updatedAt) return ''
  const d = new Date(props.entry.updatedAt)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})
</script>

<style scoped>
.question-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  margin-bottom: 12px;
  transition: all var(--transition-fast);
  overflow: hidden;
}
.question-card:hover {
  border-color: var(--color-border-strong);
  box-shadow: var(--shadow-card-hover);
}
.question-card.selected {
  border-left: 3px solid var(--color-accent);
}

.qc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px 6px;
}
.qc-badges {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.qc-type-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-weight: 600;
  background: var(--color-accent-muted);
  color: var(--color-accent);
}
.qc-diff-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-weight: 600;
}
.d-easy { background: var(--color-d-easy-bg); color: var(--color-d-easy); }
.d-medium-easy { background: var(--color-d-medium-easy-bg); color: var(--color-d-medium-easy); }
.d-medium { background: var(--color-d-medium-bg); color: var(--color-d-medium); }
.d-hard { background: var(--color-d-hard-bg); color: var(--color-d-hard); }
.d-very-hard { background: var(--color-d-very-hard-bg); color: var(--color-d-very-hard); }

.qc-answer-count { font-size: 11px; color: var(--color-text-muted); }
.qc-meta-right { display: flex; align-items: center; gap: 8px; }
.qc-time { font-size: 11px; color: var(--color-text-muted); }

.qc-body {
  display: flex;
  align-items: flex-start;
  padding: 4px 16px 10px;
  cursor: pointer;
  gap: 6px;
}
.qc-seq {
  font-weight: 700;
  font-size: 15px;
  color: var(--color-accent);
  min-width: 24px;
  padding-top: 2px;
  flex-shrink: 0;
}
.qc-stem-wrap { flex: 1; min-width: 0; overflow: hidden; }
.qc-stem-preview {
  max-height: 200px;
  overflow: hidden;
  position: relative;
}

.qc-tags-row {
  padding: 0 16px 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}
.qc-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-weight: 500;
}
.qc-tag.main-tag { background: var(--color-info-bg); color: var(--color-info); }
.qc-tag.secondary-tag { background: var(--color-bg-secondary); color: var(--color-text-secondary); }

.qc-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  border-top: 1px solid var(--color-border-light);
  background: var(--color-bg-panel);
}
.qc-actions-left { display: flex; align-items: center; gap: 8px; }
.qc-uuid-label { font-family: var(--font-mono); font-size: 11px; color: var(--color-text-muted); }
.qc-source-badge { display: inline-block; padding: 1px 8px; border-radius: var(--radius-pill); font-size: 10px; font-weight: 500; background: var(--color-warning-bg, #fef3cd); color: var(--color-warning, #856404); max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qc-actions-right { display: flex; align-items: center; gap: 6px; }
.qc-action-btn {
  padding: 4px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
}
.qc-action-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: var(--color-accent-muted);
}
.qc-action-btn.primary {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: var(--color-accent-muted);
}
.qc-action-btn.primary:hover {
  background: var(--color-accent);
  color: #fff;
}
.qc-action-btn.primary.active {
  background: var(--color-accent);
  color: #fff;
}
</style>
