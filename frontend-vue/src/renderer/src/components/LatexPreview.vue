<template>
  <div ref="containerRef" class="latex-preview" :class="{ compact }">
    <span v-if="!xml" class="empty-note">{{ placeholder }}</span>
  </div>
</template>

<script setup lang="ts">
import { ref, watchEffect, toRefs } from 'vue'
import { useLatexRender } from '@/composables/useLatexRender'

const props = withDefaults(
  defineProps<{
    xml: string
    placeholder?: string
    compact?: boolean
    imageResolver?: (ref: string) => string
    mode?: 'stem' | 'answer'
  }>(),
  {
    placeholder: '暂无内容',
    compact: false,
    mode: 'stem'
  }
)

const containerRef = ref<HTMLElement>()
const { render } = useLatexRender()

watchEffect(() => {
  if (containerRef.value) {
    if (props.xml) {
      render(containerRef.value, props.xml, {
        imageResolver: props.imageResolver,
        mode: props.mode
      })
    } else {
      containerRef.value.innerHTML = ''
    }
  }
})
</script>

<style scoped>
.latex-preview {
  padding: var(--spacing-md);
  min-height: 3em;
  line-height: 1.7;
  font-size: 15px;
  overflow-x: auto;
}

.latex-preview.compact {
  padding: var(--spacing-sm);
  min-height: 2em;
  font-size: 14px;
}

.latex-preview .empty-note {
  color: var(--text-tertiary);
  font-style: italic;
}

.latex-preview :deep(.stem-p) {
  margin: 0.3em 0;
}

.latex-preview :deep(.stem-image) {
  max-width: 100%;
  max-height: 300px;
  display: block;
  margin: 8px 0;
  border-radius: var(--radius-sm);
}

.latex-preview :deep(.choices-container) {
  margin: 8px 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.latex-preview :deep(.choice-item) {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 2px 0;
}

.latex-preview :deep(.choice-key) {
  font-weight: 600;
  min-width: 1.5em;
  color: var(--accent-primary);
}

.latex-preview :deep(.choice-content) {
  flex: 1;
}

.latex-preview :deep(.blanks-container) {
  display: flex;
  gap: 12px;
  margin: 8px 0;
}

.latex-preview :deep(.blank-item) {
  display: inline-block;
  min-width: 60px;
  border-bottom: 2px solid var(--border-default);
  text-align: center;
  color: var(--text-tertiary);
  padding: 2px 8px;
}

.latex-preview :deep(.answer-area) {
  margin: 8px 0;
  border: 1px dashed var(--border-default);
  border-radius: var(--radius-sm);
  padding: 8px;
}

.latex-preview :deep(.answer-area-label) {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 4px;
}

.latex-preview :deep(.answer-area-line) {
  height: 28px;
  border-bottom: 1px solid var(--border-subtle);
}
</style>
