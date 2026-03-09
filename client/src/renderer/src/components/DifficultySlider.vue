<template>
  <div class="difficulty-slider">
    <label class="slider-label">难度系数</label>
    <input
      class="slider-input"
      type="range"
      min="0"
      max="1"
      step="0.01"
      :value="diff.pValue.value"
      :disabled="readonly"
      @input="handleInput"
    />
    <span class="slider-value" :class="diff.cssClass.value">
      {{ Math.round(diff.pValue.value * 100) }}
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
    modelValue: 0.5,
    readonly: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: number]
}>()

const diff = useDifficulty({
  initialValue: props.modelValue ?? 0.5,
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
  gap: 10px;
}

.slider-label {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--color-text-muted);
  min-width: 4em;
  text-align: right;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.slider-input {
  flex: 1;
  max-width: 200px;
  height: 6px;
  -webkit-appearance: none;
  appearance: none;
  border-radius: 3px;
  background: linear-gradient(90deg, #00b894 0%, #88c840 25%, #fdcb6e 50%, #e17055 75%, #a29bfe 100%);
  outline: none;
}

.slider-input::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid var(--color-accent);
  cursor: pointer;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.15);
  transition: transform var(--transition-fast);
}

.slider-input::-webkit-slider-thumb:hover {
  transform: scale(1.15);
}

.slider-input:disabled {
  opacity: 0.4;
}

.slider-value {
  font-weight: 700;
  font-size: 0.88rem;
  min-width: 2.5em;
  text-align: right;
  color: var(--color-text-primary);
}

.slider-level-label {
  display: inline-block;
  min-width: 56px;
  text-align: center;
  font-size: 0.82rem;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  white-space: nowrap;
}

/* Difficulty level colors — warm palette */
.d-easy {
  color: #00b894;
  background: rgba(0, 184, 148, 0.12);
}
.d-medium-easy {
  color: #00cec9;
  background: rgba(0, 206, 201, 0.12);
}
.d-medium {
  color: #e17055;
  background: rgba(225, 112, 85, 0.1);
}
.d-hard {
  color: #d63031;
  background: rgba(214, 48, 49, 0.1);
}
.d-very-hard {
  color: #a29bfe;
  background: rgba(162, 155, 254, 0.12);
}
</style>
