<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="brand-line">
        <div class="brand-icon">Q</div>
        <div>
          <h1>QForge</h1>
          <p>录题助手 Electron Demo</p>
        </div>
      </div>

      <form class="login-form" @submit.prevent="handleLogin">
        <label>用户名
          <input
            v-model="username"
            type="text"
            placeholder="admin"
            autocomplete="username"
            :disabled="loading"
          />
        </label>

        <label>密码
          <input
            v-model="password"
            type="password"
            placeholder="admin"
            autocomplete="current-password"
            :disabled="loading"
          />
        </label>

        <label class="remember-line">
          <input type="checkbox" v-model="remember" />记住密码
        </label>

        <button type="submit" class="btn btn-primary" :disabled="loading || !username || !password">
          {{ loading ? '登录中…' : '登 录' }}
        </button>

        <div class="login-hint">默认账号: admin / admin</div>
        <p v-if="error" class="error-text">{{ error }}</p>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const remember = ref(true)
const loading = ref(false)
const error = ref('')

onMounted(async () => {
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
    await auth.login(username.value, password.value, remember.value)
    router.replace('/')
  } catch (e: unknown) {
    error.value = (e as Error).message || '登录失败，请检查用户名和密码'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100%;
  display: grid;
  place-items: center;
  padding: 24px;
}

.login-card {
  width: min(500px, 100%);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: 18px;
  box-shadow: var(--shadow-soft);
  padding: 28px;
}

.brand-line {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.brand-line h1 {
  margin: 0;
  font-size: 30px;
}

.brand-line p {
  margin: 2px 0 0;
  color: var(--color-text-secondary);
}

.brand-icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 700;
  font-size: 22px;
  background: linear-gradient(135deg, #2f6fe4, #5b93ff);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.login-form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 14px;
}

.remember-line {
  flex-direction: row !important;
  align-items: center;
  gap: 8px !important;
}

.btn {
  border: 1px solid transparent;
  border-radius: 10px;
  padding: 9px 14px;
  cursor: pointer;
  background: #e9f0ff;
  color: #274989;
  font-weight: 500;
}

.btn:hover {
  filter: brightness(0.98);
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-primary {
  color: #fff;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover));
}

.login-hint {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.error-text {
  min-height: 20px;
  color: #d64646;
  font-size: 13px;
}
</style>
