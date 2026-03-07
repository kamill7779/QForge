<template>
  <div class="stem-editor">
    <!-- Toolbar -->
    <div v-if="!readonly" class="editor-toolbar">
      <button
        v-for="t in editor.allowedTypes"
        :key="t"
        class="toolbar-btn"
        @click="editor.addBlock(t)"
      >+ {{ blockTypeLabel(t) }}</button>
      <button
        v-if="screenshotEnabled"
        class="toolbar-btn toolbar-screenshot"
        @click="emit('screenshot')"
      >📷 截图插图</button>
    </div>

    <!-- Block list -->
    <div class="block-list">
      <EditorBlock
        v-for="(block, idx) in editor.blocks.value"
        :key="idx"
        :block="block"
        :index="idx"
        :is-last="idx === editor.blocks.value.length - 1"
        :image-resolver="imageResolver"
        :readonly="readonly"
        :screenshot-enabled="screenshotEnabled"
        @update="(data) => editor.updateBlock(idx, data)"
        @remove="editor.removeBlock(idx)"
        @move-up="editor.moveBlock(idx, idx - 1)"
        @move-down="editor.moveBlock(idx, idx + 1)"
        @add-choice="editor.addChoiceItem(idx)"
        @remove-choice="(ci) => editor.removeChoiceItem(idx, ci)"
        @choice-screenshot="(ci) => emit('choiceScreenshot', idx, ci)"
        @add-blank="editor.addBlankItem(idx)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { watch } from 'vue'
import { useStemEditor, type BlockType, type RootTag } from '@/composables/useStemEditor'
import EditorBlock from './EditorBlock.vue'

const props = withDefaults(
  defineProps<{
    modelValue: string
    rootTag?: 'stem' | 'answer'
    imageResolver?: (ref: string) => string
    readonly?: boolean
    screenshotEnabled?: boolean
  }>(),
  {
    rootTag: 'stem',
    readonly: false,
    screenshotEnabled: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [xml: string]
  'screenshot': []
  'choiceScreenshot': [blockIndex: number, choiceIndex: number]
}>()

const editor = useStemEditor({
  initialXml: props.modelValue,
  rootTag: props.rootTag as RootTag,
  onChange: (xml) => emit('update:modelValue', xml)
})

// Re-init when external value changes (e.g., from API response)
watch(
  () => props.modelValue,
  (xml) => {
    if (xml !== editor.toXml()) {
      editor.initFromXml(xml)
    }
  }
)

const BLOCK_TYPE_LABELS: Record<string, string> = {
  p: '段落',
  choices: '选项',
  image: '配图',
  blanks: '填空',
  'answer-area': '答题区'
}

function blockTypeLabel(type: BlockType): string {
  return BLOCK_TYPE_LABELS[type] || type
}

defineExpose({
  /** Get current blocks as XML string. */
  toXml: editor.toXml,
  /** Re-initialize from XML. */
  initFromXml: editor.initFromXml
})
</script>

<style scoped>
.stem-editor {
  border: 1px solid #d2ddf1;
  border-radius: 10px;
  background: #f7faff;
}

.editor-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 6px 8px;
  border-bottom: 1px solid #e8eef8;
  background: #f6f9ff;
  border-radius: 10px 10px 0 0;
}

.toolbar-btn {
  padding: 3px 10px;
  border: 1px solid #c9d6ed;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  font-size: 0.82rem;
  color: #3a5b97;
  transition: all 0.15s;
}
.toolbar-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: #eef3ff;
}

.toolbar-screenshot {
  margin-left: auto;
}

.block-list {
  padding: 6px;
  min-height: 80px;
}
</style>
