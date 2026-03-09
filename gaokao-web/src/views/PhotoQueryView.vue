<template>
  <div class="photo-page">
    <section class="query-panel card">
      <div>
        <h1 class="page-title">单题拍照检索</h1>
        <p class="page-subtitle">默认不落任何正式业务主表，只作为本次查询输入对象使用。</p>
      </div>

      <label class="upload-card">
        <input type="file" accept="image/*" hidden @change="onFileChange" />
        <span>{{ previewUrl ? '更换图片' : '上传题目图片' }}</span>
      </label>

      <label>
        召回条数
        <input v-model.number="topK" type="number" min="1" max="20" />
      </label>

      <button class="btn btn-primary" :disabled="!imageBase64 || busy" @click="runQuery">
        {{ busy ? '检索中...' : '开始检索' }}
      </button>
    </section>

    <section class="preview-column card">
      <div class="column-head">输入图片</div>
      <div v-if="previewUrl" class="image-wrap">
        <img :src="previewUrl" alt="query preview" />
      </div>
      <div v-else class="empty-block">尚未上传图片</div>
    </section>

    <section class="result-column card">
      <div class="column-head">相似题结果</div>
      <div v-if="gaokao.photoQueryResult?.results?.length" class="result-list">
        <article v-for="item in gaokao.photoQueryResult.results" :key="item.questionUuid" class="result-card">
          <div class="result-top">
            <strong>{{ item.questionUuid }}</strong>
            <span class="chip">相似度 {{ Math.round((item.similarity || 0) * 100) }}%</span>
          </div>
          <p>{{ item.stemText }}</p>
        </article>
      </div>
      <div v-else class="empty-block">暂无检索结果</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useGaokaoStore } from '@/stores/gaokao'
import { useNotificationStore } from '@/stores/notification'

const gaokao = useGaokaoStore()
const notif = useNotificationStore()

const previewUrl = ref('')
const imageBase64 = ref('')
const topK = ref(6)
const busy = ref(false)

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  previewUrl.value = URL.createObjectURL(file)
  const reader = new FileReader()
  reader.onload = () => {
    const result = String(reader.result || '')
    imageBase64.value = result.includes(',') ? result.split(',')[1] : result
  }
  reader.readAsDataURL(file)
}

async function runQuery() {
  busy.value = true
  try {
    await gaokao.runPhotoQuery(imageBase64.value, topK.value)
    notif.push('success', '拍照检索已完成')
  } catch (error) {
    notif.push('error', (error as Error).message)
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.photo-page {
  display: grid;
  grid-template-columns: 320px minmax(280px, 0.8fr) minmax(0, 1fr);
  gap: 20px;
  height: 100%;
}

.query-panel,
.preview-column,
.result-column {
  padding: 22px;
  overflow: auto;
}

.query-panel {
  display: grid;
  align-content: start;
  gap: 16px;
}

.upload-card {
  display: grid;
  place-items: center;
  min-height: 140px;
  border: 1px dashed var(--color-border-strong);
  border-radius: 20px;
  background: var(--color-surface-soft);
}

.column-head {
  font-family: var(--font-family);
  font-size: 22px;
}

.image-wrap {
  margin-top: 18px;
}

.image-wrap img {
  width: 100%;
  display: block;
  border-radius: 18px;
}

.empty-block {
  display: grid;
  place-items: center;
  min-height: 180px;
  color: var(--color-text-secondary);
}

.result-list {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.result-card {
  padding: 16px;
  border-radius: 18px;
  background: var(--color-surface-soft);
}

.result-top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.result-card p {
  margin: 12px 0 0;
  line-height: 1.8;
  color: var(--color-text-secondary);
}

@media (max-width: 1200px) {
  .photo-page {
    grid-template-columns: 1fr;
  }
}
</style>