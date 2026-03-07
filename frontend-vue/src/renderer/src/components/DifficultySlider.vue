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
  gap: 10px;
}

.slider-label {
  font-size: 0.78rem;
  font-weight: 700;
  color: #8a9bba;
  min-width: 4em;
  text-align: right;
}

.slider-input {
  flex: 1;
  max-width: 200px;
  height: 6px;
  -webkit-appearance: none;
  appearance: none;
  border-radius: 3px;
  background: linear-gradient(90deg, #2fa85e 0%, #88c840 25%, #f0b840 50%, #e05050 75%, #9040c8 100%);
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
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.15);
}

.slider-input:disabled {
  opacity: 0.4;
}

.slider-value {
  font-weight: 700;
  font-size: 0.88rem;
  min-width: 2.5em;
  text-align: right;
  color: #1f355c;
}

.slider-level-label {
  display: inline-block;
  min-width: 56px;
  text-align: center;
  font-size: 0.82rem;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 999px;
  white-space: nowrap;
}

/* Difficulty level colors — exact original */
.d-easy {
  color: #176b3d;
  background: #ddf4e8;
}
.d-medium-easy {
  color: #3d6b17;
  background: #e8f5cc;
}
.d-medium {
  color: #8c6306;
  background: #fff1d6;
}
.d-hard {
  color: #af3535;
  background: #fde3e3;
}
.d-very-hard {
  color: #7a2ea0;
  background: #f3e0fa;
}
</style>
