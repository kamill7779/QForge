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
  }>(),
  {
    placeholder: '暂无内容',
    compact: false
  }
)

const containerRef = ref<HTMLElement>()
const { render } = useLatexRender()

let timer: ReturnType<typeof setTimeout> | null = null

watch(
  () => props.xml,
  () => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      if (!containerRef.value) return
      render(containerRef.value, props.xml)
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

.latex-preview :deep(.latex-p) {
  margin: 0.4rem 0;
}
</style>