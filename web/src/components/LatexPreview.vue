<template>
  <div ref="containerRef" class="latex-preview" :class="{ compact }">
    <span v-if="!xml" class="empty-note">{{ placeholder }}</span>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { useLatexRender } from '@/composables/useLatexRender'

const props = withDefaults(
  defineProps<{
    xml: string
    placeholder?: string
    compact?: boolean
    imageResolver?: (ref: string) => string
    mode?: 'stem' | 'answer'
    renderKey?: number
  }>(),
  {
    placeholder: '暂无内容',
    compact: false,
    mode: 'stem',
    renderKey: 0
  }
)

const containerRef = ref<HTMLElement>()
const { render } = useLatexRender()

let renderTimer: ReturnType<typeof setTimeout> | null = null

function scheduleRender() {
  if (renderTimer) clearTimeout(renderTimer)
  renderTimer = setTimeout(() => {
    if (!containerRef.value) return
    if (props.xml) {
      render(containerRef.value, props.xml, {
        imageResolver: props.imageResolver,
        mode: props.mode
      })
    } else {
      containerRef.value.innerHTML = ''
    }
  }, 60)
}

watch(
  () => [props.xml, props.renderKey] as const,
  scheduleRender,
  { immediate: true }
)

onBeforeUnmount(() => {
  if (renderTimer) clearTimeout(renderTimer)
})
</script>

<style scoped>
.latex-preview {
  padding: 16px 18px;
  min-height: 3em;
  line-height: 1.8;
  font-size: 0.92rem;
  overflow-x: auto;
  background: var(--color-bg-panel);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-primary);
}

.latex-preview.compact {
  padding: 10px 12px;
  min-height: 2em;
  font-size: 0.88rem;
}

.latex-preview .empty-note {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(253, 203, 110, 0.12);
  color: var(--color-warning);
  border: 1px dashed var(--color-warning);
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 0.82rem;
}

.latex-preview :deep(.stem-p) {
  margin: 0.6em 0;
  line-height: 1.7;
}

.latex-preview :deep(.stem-image) {
  max-width: 100%;
  max-height: 360px;
  display: block;
  margin: 10px auto;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
}

.latex-preview :deep(.image-placeholder) {
  display: inline-block;
  color: var(--color-text-muted);
  border: 1px dashed var(--color-border);
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 0.8rem;
  font-style: italic;
}

.latex-preview :deep(.choices-container) {
  margin: 10px 0 10px 4px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.latex-preview :deep(.choice-item) {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 2px 0;
}

.latex-preview :deep(.choice-key) {
  font-weight: 700;
  min-width: 24px;
  white-space: nowrap;
  color: var(--color-accent);
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
  border-bottom: 2px solid var(--color-border);
  text-align: center;
  color: var(--color-text-muted);
  padding: 2px 8px;
}

.latex-preview :deep(.answer-area) {
  margin: 8px 0;
  border: 1px dashed var(--color-border);
  border-radius: 8px;
  padding: 8px;
}

.latex-preview :deep(.stem-table) {
  border-collapse: collapse;
  margin: 8px 0;
  width: auto;
  font-size: 14px;
}

.latex-preview :deep(.stem-th),
.latex-preview :deep(.stem-td) {
  border: 1px solid var(--color-border);
  padding: 6px 12px;
  text-align: center;
}

.latex-preview :deep(.stem-th) {
  font-weight: 600;
}
</style>
