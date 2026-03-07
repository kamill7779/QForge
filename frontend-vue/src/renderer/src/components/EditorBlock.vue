<template>
  <div class="editor-block" :data-type="block.type">
    <!-- Block handle -->
    <div class="block-handle">
      <span class="block-drag-handle" title="拖动排序">☰</span>
      <span class="block-type-label">{{ typeLabel }}</span>
      <div class="block-handle-actions">
        <button
          v-if="index > 0"
          class="block-action-btn"
          title="上移"
          @click="emit('moveUp')"
        >↑</button>
        <button
          v-if="!isLast"
          class="block-action-btn"
          title="下移"
          @click="emit('moveDown')"
        >↓</button>
        <button
          v-if="!readonly"
          class="block-remove-btn"
          title="删除此块"
          @click="emit('remove')"
        >×</button>
      </div>
    </div>

    <!-- Paragraph block -->
    <textarea
      v-if="block.type === 'p'"
      class="block-text-input"
      :value="block.text"
      :readonly="readonly"
      placeholder="输入文本（支持 $LaTeX$ 公式）"
      rows="2"
      @input="handleTextInput"
    />

    <!-- Choices block -->
    <div v-else-if="block.type === 'choices'" class="choices-items">
      <div class="choices-header">
        <label class="mode-toggle">
          <input
            type="checkbox"
            :checked="block.mode === 'multi'"
            :disabled="readonly"
            @change="toggleMode"
          />
          <span>多选</span>
        </label>
      </div>
      <div
        v-for="(item, i) in block.items"
        :key="i"
        class="choice-edit-row"
      >
        <span class="choice-key-edit">{{ item.key }}.</span>
        <input
          class="choice-text-input"
          type="text"
          :value="item.text"
          :readonly="readonly"
          placeholder="选项内容"
          @input="(e) => handleChoiceTextInput(i, e)"
        />
        <span
          v-if="item.imageRef"
          class="choice-has-image"
          title="已插入图片"
        >🖼</span>
        <button
          v-if="!readonly && screenshotEnabled"
          class="choice-image-shot-btn"
          title="截图插图"
          @click="emit('choiceScreenshot', i)"
        >📷</button>
        <button
          v-if="!readonly && item.imageRef"
          class="choice-image-clear-btn"
          title="清除图片"
          @click="handleClearChoiceImage(i)"
        >🗑</button>
        <button
          v-if="!readonly && block.items.length > 2"
          class="choice-delete-btn"
          title="删除选项"
          @click="emit('removeChoice', i)"
        >×</button>
      </div>
      <button
        v-if="!readonly"
        class="add-choice-item-btn"
        @click="emit('addChoice')"
      >+ 添加选项</button>
    </div>

    <!-- Image block -->
    <div v-else-if="block.type === 'image'" class="block-image-info">
      <img
        v-if="resolvedImageSrc"
        class="block-image-thumb"
        :src="resolvedImageSrc"
        alt="配图"
      />
      <span v-else class="empty-note">⚠ 图片: {{ block.ref }}</span>
    </div>

    <!-- Blanks block -->
    <div v-else-if="block.type === 'blanks'" class="blanks-edit-items">
      <span
        v-for="(item, i) in block.items"
        :key="i"
        class="blank-edit-chip"
      >空{{ item.id }}</span>
      <button
        v-if="!readonly"
        class="add-blank-item-btn"
        @click="emit('addBlank')"
      >+ 空</button>
    </div>

    <!-- Answer area block -->
    <div v-else-if="block.type === 'answer-area'" class="answer-area-config">
      <span>行数: </span>
      <input
        class="answer-area-lines-input"
        type="number"
        min="1"
        max="20"
        :value="block.lines"
        :readonly="readonly"
        @input="handleLinesInput"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Block } from '@/composables/useStemEditor'

const props = withDefaults(
  defineProps<{
    block: Block
    index: number
    isLast?: boolean
    readonly?: boolean
    screenshotEnabled?: boolean
    imageResolver?: (ref: string) => string
  }>(),
  {
    isLast: false,
    readonly: false,
    screenshotEnabled: false
  }
)

const emit = defineEmits<{
  update: [data: Partial<Block>]
  remove: []
  moveUp: []
  moveDown: []
  addChoice: []
  removeChoice: [index: number]
  choiceScreenshot: [choiceIndex: number]
  addBlank: []
}>()

const TYPE_LABELS: Record<string, string> = {
  p: '段落',
  choices: '选项组',
  image: '配图',
  blanks: '填空',
  'answer-area': '答题区'
}

const typeLabel = computed(() => {
  let label = TYPE_LABELS[props.block.type] || props.block.type
  if (props.block.type === 'choices') {
    label += props.block.mode === 'multi' ? '(多选)' : '(单选)'
  }
  return label
})

const resolvedImageSrc = computed(() => {
  if (props.block.type !== 'image') return ''
  const src = props.imageResolver?.(props.block.ref)
  if (!src) return ''
  return src.startsWith('data:') ? src : `data:image/png;base64,${src}`
})

function handleTextInput(e: Event) {
  emit('update', { type: 'p', text: (e.target as HTMLTextAreaElement).value })
}

function handleChoiceTextInput(choiceIndex: number, e: Event) {
  if (props.block.type !== 'choices') return
  const items = [...props.block.items]
  items[choiceIndex] = {
    ...items[choiceIndex],
    text: (e.target as HTMLInputElement).value
  }
  emit('update', { type: 'choices', mode: props.block.mode, items })
}

function handleClearChoiceImage(choiceIndex: number) {
  if (props.block.type !== 'choices') return
  const items = [...props.block.items]
  items[choiceIndex] = { ...items[choiceIndex], imageRef: '' }
  emit('update', { type: 'choices', mode: props.block.mode, items })
}

function toggleMode() {
  if (props.block.type !== 'choices') return
  const newMode = props.block.mode === 'multi' ? 'single' : 'multi'
  emit('update', { type: 'choices', mode: newMode, items: props.block.items })
}

function handleLinesInput(e: Event) {
  emit('update', {
    type: 'answer-area',
    lines: Number((e.target as HTMLInputElement).value) || 4
  })
}
</script>

<style scoped>
.editor-block {
  border: 1px solid #d2ddf1;
  border-radius: 10px;
  margin-bottom: 6px;
  background: #fff;
}

.block-handle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  background: #f6f9ff;
  border-bottom: 1px solid #e8eef8;
  border-radius: 10px 10px 0 0;
  font-size: 0.82rem;
}

.block-drag-handle {
  cursor: grab;
  opacity: 0;
  transition: opacity 0.15s;
}
.editor-block:hover .block-drag-handle {
  opacity: 0.6;
}

.block-type-label {
  color: #6a7fa0;
  font-weight: 500;
  flex: 1;
}

.block-handle-actions {
  display: flex;
  gap: 2px;
}

.block-action-btn,
.block-remove-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0 4px;
  font-size: 0.88rem;
  color: #8a9bba;
  border-radius: 4px;
}
.block-action-btn:hover {
  background: #eef3ff;
  color: #3a5b97;
}
.block-remove-btn:hover {
  background: #fde3e3;
  color: #af3535;
}

/* Paragraph */
.block-text-input {
  width: 100%;
  border: none;
  padding: 8px 10px;
  font-size: 0.88rem;
  font-family: inherit;
  resize: vertical;
  min-height: 48px;
  background: #fbfdff;
  box-sizing: border-box;
  color: #1f355c;
  border-top: 1px solid #e8eef8;
}
.block-text-input:focus {
  outline: 2px solid rgba(45, 108, 223, 0.3);
  outline-offset: -2px;
  background: #fff;
}

/* Choices */
.choices-items {
  padding: 6px 10px;
}

.choices-header {
  margin-bottom: 4px;
}

.mode-toggle {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 0.82rem;
  color: #6a7fa0;
  cursor: pointer;
}

.choice-edit-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 3px;
}

.choice-key-edit {
  font-weight: 700;
  min-width: 1.5em;
  color: #1f355c;
  font-size: 0.88rem;
}

.choice-text-input {
  flex: 1;
  padding: 4px 8px;
  border: 1px solid #c9d6ed;
  border-radius: 8px;
  font-size: 0.85rem;
  background: #fbfdff;
  color: #1f355c;
}
.choice-text-input:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 2px rgba(45, 108, 223, 0.12);
  outline: none;
}

.choice-has-image {
  font-size: 0.88rem;
}

.choice-image-shot-btn,
.choice-image-clear-btn,
.choice-delete-btn,
.add-choice-item-btn {
  background: none;
  border: 1px solid #c9d6ed;
  border-radius: 8px;
  cursor: pointer;
  padding: 2px 6px;
  font-size: 0.82rem;
  color: #3a5b97;
  transition: background 0.15s;
}
.add-choice-item-btn {
  margin-top: 4px;
  width: 100%;
  padding: 4px;
  background: #f6f9ff;
}
.add-choice-item-btn:hover {
  background: #eef3ff;
}
.choice-delete-btn:hover {
  border-color: #e8c0c0;
  color: #af3535;
  background: #fff1f1;
}

/* Image */
.block-image-info {
  padding: 8px;
  text-align: center;
}

.block-image-thumb {
  max-width: 100%;
  max-height: 200px;
  border-radius: 8px;
  border: 1px solid #d2ddf1;
}

/* Blanks */
.blanks-edit-items {
  padding: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.blank-edit-chip {
  background: #e8efff;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 0.85rem;
  color: #2a4f99;
}

.add-blank-item-btn {
  background: none;
  border: 1px dashed #c9d6ed;
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 0.82rem;
  cursor: pointer;
  color: #6a7fa0;
}
.add-blank-item-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

/* Answer area */
.answer-area-config {
  padding: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.85rem;
  color: #3a5b97;
}

.answer-area-lines-input {
  width: 60px;
  padding: 3px 6px;
  border: 1px solid #c9d6ed;
  border-radius: 8px;
  text-align: center;
  color: #1f355c;
}
</style>
