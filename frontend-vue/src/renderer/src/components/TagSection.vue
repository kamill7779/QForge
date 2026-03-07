<template>
  <div class="tag-section">
    <!-- Main tag selectors -->
    <div
      v-for="cat in tags.mainCategories.value"
      :key="cat.categoryCode"
      class="main-tag-group"
    >
      <label class="tag-group-label">{{ cat.categoryName }}</label>
      <select
        class="tag-select"
        :value="tags.mainTagSelection.value[cat.categoryCode] || ''"
        :disabled="readonly"
        @change="(e) => handleMainTagChange(cat.categoryCode, e)"
      >
        <option value="">-- 选择{{ cat.categoryName }} --</option>
        <option
          v-for="opt in cat.options"
          :key="opt.tagCode"
          :value="opt.tagCode"
        >{{ opt.tagName }}</option>
      </select>
    </div>

    <!-- Secondary tags -->
    <div class="secondary-tag-group">
      <label class="tag-group-label">副标签</label>
      <div class="tag-capsules">
        <span
          v-for="(tag, i) in tags.secondaryTags.value"
          :key="i"
          class="tag-capsule"
        >
          {{ tag }}
          <button
            v-if="!readonly"
            class="tag-capsule-remove"
            @click="tags.removeSecondaryTag(i)"
          >×</button>
        </span>
        <input
          v-if="!readonly"
          v-model="tags.secondaryInput.value"
          class="tag-input"
          type="text"
          placeholder="输入标签，按空格添加"
          @keydown.space.prevent="tags.addFromInput()"
          @keydown.enter.prevent="tags.addFromInput()"
        />
      </div>
    </div>

    <slot name="actions" />
  </div>
</template>

<script setup lang="ts">
import { useTagEditor } from '@/composables/useTagEditor'
import type { QuestionMainTagResponse } from '@/api/types'

const props = withDefaults(
  defineProps<{
    /** Initial main tags from question data. */
    mainTags?: QuestionMainTagResponse[]
    /** Initial secondary tags from question data. */
    secondaryTags?: string[]
    readonly?: boolean
  }>(),
  {
    mainTags: () => [],
    secondaryTags: () => [],
    readonly: false
  }
)

const emit = defineEmits<{
  change: [payload: { tags: string[] }]
}>()

const tags = useTagEditor({ readonly: props.readonly })

// Initialize from props
tags.initFromQuestion(props.mainTags, props.secondaryTags)

function handleMainTagChange(categoryCode: string, e: Event) {
  const value = (e.target as HTMLSelectElement).value
  if (value) {
    tags.setMainTag(categoryCode, value)
  } else {
    tags.clearMainTag(categoryCode)
  }
  emit('change', tags.toPayload())
}

defineExpose({
  toPayload: tags.toPayload,
  initFromQuestion: tags.initFromQuestion
})
</script>

<style scoped>
.tag-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.main-tag-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tag-group-label {
  font-size: 13px;
  color: var(--text-secondary);
  min-width: 4em;
  text-align: right;
}

.tag-select {
  flex: 1;
  padding: 4px 8px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  font-size: 13px;
  background: var(--bg-primary);
}
.tag-select:focus {
  border-color: var(--accent-primary);
  outline: none;
}

.secondary-tag-group {
  display: flex;
  gap: 8px;
}

.tag-capsules {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  padding: 4px 6px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  min-height: 32px;
  background: var(--bg-primary);
}

.tag-capsule {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 8px;
  background: var(--accent-primary);
  color: white;
  border-radius: 12px;
  font-size: 12px;
}

.tag-capsule-remove {
  background: none;
  border: none;
  color: white;
  cursor: pointer;
  font-size: 14px;
  padding: 0 2px;
  opacity: 0.7;
}
.tag-capsule-remove:hover {
  opacity: 1;
}

.tag-input {
  border: none;
  outline: none;
  font-size: 13px;
  min-width: 120px;
  flex: 1;
  padding: 2px;
  background: transparent;
}
</style>
