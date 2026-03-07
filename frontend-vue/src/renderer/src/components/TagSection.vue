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
  font-size: 0.78rem;
  font-weight: 700;
  color: #8a9bba;
  min-width: 4em;
  text-align: right;
}

.tag-select {
  flex: 1;
  padding: 5px 10px;
  border: 1px solid #c9d6ed;
  border-radius: 10px;
  font-size: 0.85rem;
  background: #fbfdff;
  color: #1f355c;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.tag-select:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px rgba(45, 108, 223, 0.14);
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
  gap: 6px;
  align-items: center;
  padding: 6px 8px;
  border: 1px solid #c9d6ed;
  border-radius: 10px;
  min-height: 38px;
  background: #fbfdff;
  cursor: text;
}
.tag-capsules:focus-within {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px rgba(45, 108, 223, 0.14);
}

.tag-capsule {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px 2px 10px;
  background: #e8efff;
  color: #2a4f99;
  border-radius: 999px;
  font-size: 0.82rem;
  white-space: nowrap;
}

.tag-capsule-remove {
  width: 16px;
  height: 16px;
  border: none;
  border-radius: 50%;
  background: rgba(45, 108, 223, 0.15);
  color: #3f6ec2;
  font-size: 0.75rem;
  line-height: 1;
  cursor: pointer;
  display: grid;
  place-items: center;
  padding: 0;
  transition: background 0.15s;
}
.tag-capsule-remove:hover {
  background: rgba(45, 108, 223, 0.3);
}

.tag-input {
  border: none;
  outline: none;
  font-size: 0.85rem;
  min-width: 100px;
  flex: 1;
  padding: 4px 0;
  background: transparent;
  color: #1f355c;
}
</style>
