<template>
  <Teleport to="body">
    <div class="modal-overlay" @click.self="$emit('close')">
      <div class="modal-card detail-modal">
        <!-- Header -->
        <div class="detail-header">
          <h3>题目详情</h3>
          <button class="detail-close" @click="$emit('close')">×</button>
        </div>

        <!-- Body -->
        <div class="detail-body">
          <div v-if="loading" class="detail-loading">加载中…</div>
          <template v-else>
            <!-- Stem -->
            <div class="detail-section">
              <div class="detail-label">📝 题干</div>
              <LatexPreview
                :xml="stemText"
                :image-resolver="imgResolver"
                :render-key="renderKey"
              />
            </div>

            <!-- Answers -->
            <div class="detail-section">
              <div class="detail-label">✅ 答案</div>
              <div v-if="answers.length" class="answers-list">
                <div v-for="(ans, idx) in answers" :key="idx" class="answer-block">
                  <LatexPreview
                    :xml="ans.latexText"
                    mode="answer"
                    :image-resolver="imgResolver"
                    :render-key="renderKey"
                  />
                </div>
              </div>
              <div v-else class="no-answer">暂无答案</div>
            </div>

            <!-- Metadata -->
            <div class="detail-section detail-meta-section">
              <div v-if="source" class="meta-row">
                <span class="meta-key">来源</span>
                <span class="meta-val">{{ source }}</span>
              </div>
              <div v-if="difficulty != null" class="meta-row">
                <span class="meta-key">难度</span>
                <span class="meta-val">{{ diffLabel }}</span>
              </div>
              <div v-if="tags.length" class="meta-row">
                <span class="meta-key">标签</span>
                <div class="meta-tags">
                  <span v-for="t in tags" :key="t" class="meta-tag">{{ t }}</span>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import LatexPreview from '@/components/LatexPreview.vue'
import { useQuestionStore, type QuestionEntry } from '@/stores/question'
import { useQuestionAssets } from '@/composables/useQuestionAssets'
import { difficultyLabel } from '@/lib/difficulty'

const props = defineProps<{
  questionUuid: string
  /** Optional override for stem text (e.g. from basket item). */
  overrideStem?: string
  /** Optional override for source. */
  overrideSource?: string
  /** Optional override for difficulty. */
  overrideDifficulty?: number | null
}>()

defineEmits<{ close: [] }>()

const questionStore = useQuestionStore()
const { loadAssets, resolverFor } = useQuestionAssets()

const loading = ref(true)
const renderKey = ref(0)
const questionDetail = ref<QuestionEntry | null>(null)

const question = computed(() =>
  questionDetail.value
  ?? questionStore.allQuestions.find(q => q.questionUuid === props.questionUuid)
  ?? null
)

const stemText = computed(() =>
  props.overrideStem ?? question.value?.stemText ?? ''
)

const answers = computed(() =>
  question.value?.answers ?? []
)

const source = computed(() =>
  props.overrideSource ?? question.value?.source ?? ''
)

const difficulty = computed(() =>
  props.overrideDifficulty !== undefined ? props.overrideDifficulty : (question.value?.difficulty ?? null)
)

const diffLabel = computed(() =>
  difficulty.value != null ? difficultyLabel(difficulty.value) : ''
)

const tags = computed(() => {
  if (!question.value) return []
  const mainNames = question.value.mainTags.map(t => t.tagName)
  return [...mainNames, ...question.value.secondaryTags]
})

const imgResolver = computed(() => resolverFor(props.questionUuid))

onMounted(async () => {
  questionDetail.value = await questionStore.fetchQuestionDetail(props.questionUuid)

  // Load image assets
  await loadAssets(props.questionUuid)
  renderKey.value++
  loading.value = false
})
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9000;
  backdrop-filter: blur(4px);
}

.detail-modal {
  background: var(--color-bg-card, #fff);
  border-radius: 12px;
  width: 680px;
  max-width: 92vw;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.18);
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid var(--color-border, #e0e0e0);
  flex-shrink: 0;
}

.detail-header h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary, #222);
  margin: 0;
}

.detail-close {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-muted, #999);
  font-size: 20px;
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: all 0.15s ease;
}

.detail-close:hover {
  background: var(--color-danger-bg, #fee);
  color: var(--color-danger, #e74c3c);
}

.detail-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.detail-loading {
  text-align: center;
  padding: 40px;
  color: var(--color-text-muted, #999);
  font-size: 14px;
}

.detail-section {
  margin-bottom: 20px;
}

.detail-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary, #666);
  margin-bottom: 8px;
}

.answers-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.answer-block {
  border-left: 3px solid var(--color-accent, #6c5ce7);
  padding-left: 12px;
}

.no-answer {
  color: var(--color-text-muted, #999);
  font-size: 13px;
  font-style: italic;
}

.detail-meta-section {
  background: var(--color-bg-secondary, #f8f8f8);
  border-radius: 8px;
  padding: 12px 16px;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.meta-row:last-child {
  margin-bottom: 0;
}

.meta-key {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-muted, #999);
  min-width: 40px;
}

.meta-val {
  font-size: 13px;
  color: var(--color-text-primary, #222);
}

.meta-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.meta-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: var(--color-info-bg, #e8f4fd);
  color: var(--color-info, #0984e3);
}
</style>
