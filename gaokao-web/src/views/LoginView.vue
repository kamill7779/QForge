<template>
  <div class="login-page">
    <div class="login-card card">
      <div class="eyebrow">QForge / 高考数学</div>
      <h1>正式录题前端</h1>
      <p>
        面向整卷录入、人工校正、AI 预览确认、正式发布与语料物化。该前端默认只接入
        gaokao-corpus-service 的正式链路。
      </p>

      <form class="login-form" @submit.prevent="handleLogin">
        <label>
          用户名
          <input v-model="username" type="text" placeholder="admin" />
        </label>
        <label>
          密码
          <input v-model="password" type="password" placeholder="admin" />
        </label>
        <button class="btn btn-primary" type="submit" :disabled="loading || !username || !password">
          {{ loading ? '登录中...' : '进入录题台' }}
        </button>
        <p v-if="error" class="error-text">{{ error }}</p>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const username = ref('admin')
const password = ref('admin')
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  loading.value = true
  error.value = ''
  try {
    await auth.login(username.value, password.value)
    router.replace('/sessions')
  } catch (err) {
    error.value = (err as Error).message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: grid;
  place-items: center;
  padding: 24px;
}

.login-card {
  width: min(560px, 100%);
  padding: 34px;
}

.eyebrow {
  color: var(--color-accent-strong);
  text-transform: uppercase;
  letter-spacing: 0.16em;
  font-size: 12px;
}

h1 {
  margin: 12px 0 10px;
  font-family: var(--font-family);
  font-size: 36px;
}

p {
  margin: 0;
  color: var(--color-text-secondary);
  line-height: 1.8;
}

.login-form {
  display: grid;
  gap: 16px;
  margin-top: 24px;
}

label {
  display: grid;
  gap: 8px;
  color: var(--color-text-secondary);
}

.error-text {
  color: var(--color-danger);
}
</style>