<template>
  <div ref="containerRef" class="latex-preview" :class="{ compact }">
    <span v-if="!xml" class="empty-note">{{ placeholder }}</span>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
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
    imageResolver: undefined,
    mode: 'stem',
    renderKey: 0
  }
)

const containerRef = ref<HTMLElement>()
const { render } = useLatexRender()

let timer: ReturnType<typeof setTimeout> | null = null

watch(
  () => [props.xml, props.renderKey, props.mode],
  () => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      if (!containerRef.value) return
      render(containerRef.value, props.xml, {
        imageResolver: props.imageResolver,
        mode: props.mode
      })
    }, 40)
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
})
</script>

<style scoped>
.latex-preview {
  min-height: 120px;
  padding: 16px 18px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-surface-soft);
  color: var(--color-text-primary);
  line-height: 1.8;
}

.latex-preview.compact {
  min-height: 72px;
  padding: 12px 14px;
}

.empty-note {
  color: var(--color-text-muted);
}

.latex-preview :deep(.stem-p) {
  margin: 0.4rem 0;
}

.latex-preview :deep(.stem-image) {
  display: block;
  max-width: 100%;
  margin: 0.75rem 0;
  border-radius: 12px;
}

.latex-preview :deep(.image-placeholder) {
  display: inline-flex;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  color: var(--color-text-secondary);
}

.latex-preview :deep(.choices-container) {
  display: grid;
  gap: 0.5rem;
  margin: 0.6rem 0;
}

.latex-preview :deep(.choice-item) {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.5rem;
  align-items: start;
}

.latex-preview :deep(.choice-key) {
  font-weight: 700;
}

.latex-preview :deep(.blanks-container) {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin: 0.6rem 0;
}

.latex-preview :deep(.blank-item) {
  letter-spacing: 0.1em;
}

.latex-preview :deep(.answer-area) {
  margin: 0.75rem 0;
  padding: 0.75rem 0.9rem;
  border: 1px dashed var(--color-border);
  border-radius: 12px;
}

.latex-preview :deep(.answer-area-label) {
  color: var(--color-text-secondary);
  font-size: 0.92rem;
}

.latex-preview :deep(.stem-table) {
  width: 100%;
  border-collapse: collapse;
  margin: 0.75rem 0;
}

.latex-preview :deep(.stem-th),
.latex-preview :deep(.stem-td) {
  border: 1px solid var(--color-border);
  padding: 0.5rem 0.65rem;
}
</style>