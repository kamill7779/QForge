<template>
  <div class="login-view">
    <div class="login-card">
      <h1 class="login-title">QForge 录题助手</h1>
      <p class="login-subtitle">请登录以继续</p>

      <form class="login-form" @submit.prevent="handleLogin">
        <div class="form-field">
          <label for="username">用户名</label>
          <input
            id="username"
            v-model="username"
            type="text"
            placeholder="请输入用户名"
            autocomplete="username"
            :disabled="loading"
          />
        </div>

        <div class="form-field">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            :disabled="loading"
          />
        </div>

        <p v-if="error" class="login-error">{{ error }}</p>

        <button type="submit" class="btn-login" :disabled="loading || !username || !password">
          {{ loading ? '登录中…' : '登 录' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

onMounted(async () => {
  // Auto-login if credentials exist
  try {
    const cred = await window.qforge.credentials.load()
    if (cred?.token) {
      router.replace('/')
    }
  } catch {
    /* ignore */
  }
})

async function handleLogin() {
  if (!username.value || !password.value) return
  loading.value = true
  error.value = ''

  try {
    const res = await window.qforge.auth.login(username.value, password.value)
    if (res.status >= 400) {
      const body = res.body as { message?: string }
      error.value = body?.message || `登录失败 (${res.status})`
      return
    }
    const body = res.body as { token: string }
    await window.qforge.credentials.save({ username: username.value, token: body.token })
    router.replace('/')
  } catch (e: unknown) {
    error.value = (e as Error).message || '网络错误'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-view {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: var(--color-bg-primary);
}

.login-card {
  width: 380px;
  padding: 40px 32px;
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-md);
}

.login-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text-primary);
  text-align: center;
  margin-bottom: 4px;
}

.login-subtitle {
  font-size: 13px;
  color: var(--color-text-muted);
  text-align: center;
  margin-bottom: 28px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-field label {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.form-field input {
  padding: 10px 12px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg-input);
  color: var(--color-text-primary);
  outline: none;
  transition: border-color 0.15s;
}

.form-field input:focus {
  border-color: var(--color-border-focus);
}

.login-error {
  font-size: 13px;
  color: var(--color-danger);
  margin: 0;
}

.btn-login {
  padding: 10px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-accent);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-login:hover:not(:disabled) {
  background: var(--color-accent-hover);
}

.btn-login:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
