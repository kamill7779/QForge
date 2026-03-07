<template>
  <div class="difficulty-slider">
    <label class="slider-label">难度系数</label>
    <input
      class="slider-input"
      type="range"
      min="0"
      max="100"
      step="1"
      :value="diff.pValue.value"
      :disabled="readonly"
      @input="handleInput"
    />
    <span class="slider-value" :class="diff.cssClass.value">
      {{ diff.pValue.value }}
    </span>
    <span class="slider-level-label" :class="diff.cssClass.value">
      {{ diff.label.value }}
    </span>
  </div>
</template>

<script setup lang="ts">
import { watch } from 'vue'
import { useDifficulty } from '@/composables/useDifficulty'

const props = withDefaults(
  defineProps<{
    modelValue?: number | null
    readonly?: boolean
  }>(),
  {
    modelValue: 50,
    readonly: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: number]
}>()

const diff = useDifficulty({
  initialValue: props.modelValue ?? 50,
  onChange: (val) => emit('update:modelValue', val)
})

// Sync when prop changes externally
watch(
  () => props.modelValue,
  (val) => {
    if (val !== null && val !== undefined && val !== diff.pValue.value) {
      diff.setValue(val)
    }
  }
)

function handleInput(e: Event) {
  diff.setValue(Number((e.target as HTMLInputElement).value))
}
</script>

<style scoped>
.difficulty-slider {
  display: flex;
  align-items: center;
  gap: 8px;
}

.slider-label {
  font-size: 13px;
  color: var(--text-secondary);
  min-width: 4em;
  text-align: right;
}

.slider-input {
  flex: 1;
  max-width: 200px;
  accent-color: var(--accent-primary);
}

.slider-value {
  font-weight: 600;
  font-size: 14px;
  min-width: 2.5em;
  text-align: center;
}

.slider-level-label {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 8px;
  white-space: nowrap;
}

/* Difficulty level colors */
.d-easy {
  color: var(--difficulty-easy);
  background: color-mix(in srgb, var(--difficulty-easy) 12%, transparent);
}
.d-medium-easy {
  color: var(--difficulty-medium-easy);
  background: color-mix(in srgb, var(--difficulty-medium-easy) 12%, transparent);
}
.d-medium {
  color: var(--difficulty-medium);
  background: color-mix(in srgb, var(--difficulty-medium) 12%, transparent);
}
.d-hard {
  color: var(--difficulty-hard);
  background: color-mix(in srgb, var(--difficulty-hard) 12%, transparent);
}
.d-very-hard {
  color: var(--difficulty-very-hard);
  background: color-mix(in srgb, var(--difficulty-very-hard) 12%, transparent);
}
</style>
