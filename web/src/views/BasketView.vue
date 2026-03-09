<template>
  <div class="basket-view">
    <!-- Header -->
    <div class="basket-header">
      <div class="basket-header-left">
        <span class="basket-icon">🛒</span>
        <h2 class="basket-title">试题篮</h2>
        <span class="basket-count">{{ basketStore.count }} 题</span>
      </div>
      <div class="basket-header-right">
        <button
          v-if="!basketStore.isEmpty"
          class="btn-clear"
          @click="handleClear"
        >
          清空试题篮
        </button>
        <router-link to="/bank" class="btn-back">
          ← 返回题库
        </router-link>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="basketStore.loading" class="basket-loading">
      加载中…
    </div>

    <!-- Empty state -->
    <div v-else-if="basketStore.isEmpty" class="basket-empty">
      <div class="empty-icon">📋</div>
      <p class="empty-text">试题篮为空</p>
      <p class="empty-hint">在题库中点击「选题」按钮添加题目</p>
      <router-link to="/bank" class="btn-go-bank">
        前往题库选题 →
      </router-link>
    </div>

    <!-- Basket items -->
    <div v-else ref="listRef" class="basket-list" @scroll="onListScroll">
      <div
        v-for="(item, idx) in basketStore.items"
        :key="item.questionUuid"
        class="basket-item"
        @click="openDetail(item)"
      >
        <div class="item-left">
          <span class="item-seq">{{ idx + 1 }}.</span>
          <div class="item-content">
            <LatexPreview
              :xml="item.stemText ?? ''"
              :image-resolver="assetHelper.resolverFor(item.questionUuid)"
              :render-key="assetRenderKey"
              class="item-stem"
            />
            <div class="item-meta">
              <span v-if="item.source" class="item-source">📁 {{ item.source }}</span>
              <span v-if="item.difficulty != null" class="item-diff">
                难度: {{ difficultyLabel(item.difficulty) }}
              </span>
              <span class="item-added">{{ formatDate(item.addedAt) }}</span>
            </div>
          </div>
        </div>
        <div class="item-right" @click.stop>
          <button class="btn-remove" @click="handleRemove(item.questionUuid)" title="移除">
            ×
          </button>
        </div>
      </div>
    </div>

    <!-- Footer actions -->
    <div v-if="!basketStore.isEmpty" class="basket-footer">
      <div class="footer-summary">
        共 {{ basketStore.count }} 题
      </div>
      <div class="footer-actions">
        <button class="footer-btn compose-btn" :disabled="composing" @click="handleCompose">
          {{ composing ? '创建中…' : '开始组卷 →' }}
        </button>
        <router-link to="/exams" class="footer-btn secondary-btn">
          管理试卷
        </router-link>
      </div>
    </div>

    <!-- Detail modal -->
    <QuestionDetailModal
      v-if="detailUuid"
      :question-uuid="detailUuid"
      :override-source="detailSource"
      :override-difficulty="detailDifficulty"
      @close="detailUuid = null"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import LatexPreview from '@/components/LatexPreview.vue'
import QuestionDetailModal from '@/components/QuestionDetailModal.vue'
import { useBasketStore } from '@/stores/basket'
import { useNotificationStore } from '@/stores/notification'
import { useQuestionAssets } from '@/composables/useQuestionAssets'
import { examPaperApi } from '@/api/examPaper'
import { difficultyLabel } from '@/lib/difficulty'
import type { BasketItemResponse } from '@/api/types'

const router = useRouter()
const basketStore = useBasketStore()
const notif = useNotificationStore()
const assetHelper = useQuestionAssets()
const composing = ref(false)
const assetRenderKey = ref(0)
const listRef = ref<HTMLElement | null>(null)
const loadedAssetCount = ref(0)
const assetBatchLoading = ref(false)
const ASSET_BATCH_SIZE = 12

// Detail modal state
const detailUuid = ref<string | null>(null)
const detailSource = ref<string | undefined>()
const detailDifficulty = ref<number | null | undefined>()

onMounted(async () => {
  await basketStore.fetchItems()
  await loadNextAssetBatch()
})

function openDetail(item: BasketItemResponse) {
  detailUuid.value = item.questionUuid
  detailSource.value = item.source ?? undefined
  detailDifficulty.value = item.difficulty
  assetHelper.loadAssets(item.questionUuid).then(() => {
    assetRenderKey.value++
  })
}

async function handleRemove(questionUuid: string) {
  try {
    await basketStore.toggle(questionUuid)
    await basketStore.fetchItems()
    loadedAssetCount.value = 0
    await loadNextAssetBatch()
    notif.log('已从试题篮移除')
  } catch {
    notif.log('移除失败')
  }
}

async function handleClear() {
  if (!confirm('确定要清空试题篮吗？')) return
  try {
    await basketStore.clear()
    loadedAssetCount.value = 0
    notif.log('试题篮已清空')
  } catch {
    notif.log('清空失败')
  }
}

async function loadNextAssetBatch() {
  if (assetBatchLoading.value) return
  const uuids = basketStore.items
    .slice(loadedAssetCount.value, loadedAssetCount.value + ASSET_BATCH_SIZE)
    .map((item) => item.questionUuid)
  if (!uuids.length) return
  assetBatchLoading.value = true
  try {
    loadedAssetCount.value += uuids.length
    await assetHelper.loadAssetsForMany(uuids)
    assetRenderKey.value++
  } finally {
    assetBatchLoading.value = false
  }
}

function onListScroll() {
  if (!listRef.value) return
  const { scrollTop, scrollHeight, clientHeight } = listRef.value
  if (scrollHeight - scrollTop - clientHeight < 240) {
    loadNextAssetBatch()
  }
}

async function handleCompose() {
  composing.value = true
  try {
    const detail = await examPaperApi.createFromBasket()
    notif.log(`已创建试卷「${detail.title}」，共 ${detail.sections.reduce((s, sec) => s + sec.questions.length, 0)} 题`)
    router.push(`/compose/${detail.paperUuid}`)
  } catch (e: any) {
    notif.log(e?.message ?? '创建试卷失败')
  } finally {
    composing.value = false
  }
}

function formatDate(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.basket-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: var(--color-bg-main);
}

/* ── Header ── */
.basket-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-card);
  flex-shrink: 0;
}

.basket-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.basket-icon {
  font-size: 24px;
}

.basket-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0;
}

.basket-count {
  font-size: 13px;
  color: var(--color-text-muted);
  padding: 2px 10px;
  background: var(--color-bg-secondary);
  border-radius: var(--radius-pill);
}

.basket-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-clear {
  padding: 6px 14px;
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-danger);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.btn-clear:hover {
  background: var(--color-danger-bg);
}

.btn-back {
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 500;
  text-decoration: none;
  transition: all var(--transition-fast);
}

.btn-back:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

/* ── Loading ── */
.basket-loading {
  flex: 1;
  display: grid;
  place-items: center;
  color: var(--color-text-muted);
  font-size: 14px;
}

/* ── Empty state ── */
.basket-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--color-text-muted);
}

.empty-icon {
  font-size: 48px;
  opacity: 0.5;
}

.empty-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.empty-hint {
  font-size: 13px;
}

.btn-go-bank {
  margin-top: 12px;
  padding: 10px 24px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-accent), #a29bfe);
  color: #fff;
  font-weight: 600;
  font-size: 14px;
  text-decoration: none;
  box-shadow: 0 4px 16px var(--color-accent-glow);
  transition: all var(--transition-fast);
}

.btn-go-bank:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px var(--color-accent-glow);
}

/* ── Basket list ── */
.basket-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
}

.basket-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  margin-bottom: 12px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  transition: all var(--transition-fast);
  cursor: pointer;
}

.basket-item:hover {
  border-color: var(--color-accent);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.item-left {
  display: flex;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.item-seq {
  font-weight: 700;
  font-size: 15px;
  color: var(--color-accent);
  min-width: 28px;
  flex-shrink: 0;
  padding-top: 2px;
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-stem {
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 8px;
}

.item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.item-source {
  color: var(--color-text-secondary);
}

.item-diff {
  color: var(--color-text-secondary);
}

.item-added {
  color: var(--color-text-muted);
}

.item-right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.btn-remove {
  width: 28px;
  height: 28px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-muted);
  font-size: 16px;
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: all var(--transition-fast);
}

.btn-remove:hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
  background: var(--color-danger-bg);
}

/* ── Footer ── */
.basket-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
  border-top: 1px solid var(--color-border);
  background: var(--color-bg-card);
  flex-shrink: 0;
}

.footer-summary {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.footer-actions {
  display: flex;
  gap: 10px;
}

.footer-btn {
  padding: 8px 20px;
  border-radius: var(--radius-md);
  font-weight: 600;
  font-size: 13px;
  text-decoration: none;
  transition: all var(--transition-fast);
  background: linear-gradient(135deg, var(--color-accent), #a29bfe);
  color: #fff;
  box-shadow: 0 4px 16px var(--color-accent-glow);
}

.footer-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 24px var(--color-accent-glow);
  color: #fff;
}

.compose-btn:disabled {
  opacity: .55;
  cursor: not-allowed;
  transform: none;
}

.secondary-btn {
  background: transparent;
  color: var(--color-text-secondary);
  box-shadow: none;
  border: 1px solid var(--color-border);
}

.secondary-btn:hover {
  background: var(--color-surface-hover);
  color: var(--color-text);
  box-shadow: none;
}
</style>
