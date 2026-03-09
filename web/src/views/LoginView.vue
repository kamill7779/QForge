<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="brand-line">
        <div class="brand-icon">Q</div>
        <div>
          <h1>QForge</h1>
          <p>在线组卷平台</p>
        </div>
      </div>

      <form class="login-form" @submit.prevent="handleLogin">
        <label>用户名
          <input v-model="username" type="text" placeholder="admin" autocomplete="username" :disabled="loading" />
        </label>
        <label>密码
          <input v-model="password" type="password" placeholder="admin" autocomplete="current-password" :disabled="loading" />
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

onMounted(() => {
  const cred = auth.loadSavedCredentials()
  if (cred?.token) {
    auth.restoreSession(cred.username, cred.token)
    router.replace('/')
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
  background:
    radial-gradient(ellipse at 30% 20%, rgba(108,92,231,0.08) 0%, transparent 50%),
    radial-gradient(ellipse at 70% 80%, rgba(225,112,85,0.05) 0%, transparent 50%),
    var(--color-bg-primary);
}
.login-card {
  width: min(440px, 100%);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  padding: 36px 32px;
  animation: fadeInUp 0.5s ease;
}
.brand-line {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 28px;
}
.brand-line h1 {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-2xl);
  font-weight: 800;
  letter-spacing: 1.5px;
  text-transform: uppercase;
  background: linear-gradient(135deg, var(--color-accent), #a29bfe);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.brand-line p {
  margin: 2px 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
}
.brand-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 800;
  font-size: 24px;
  background: linear-gradient(135deg, var(--color-accent), #a29bfe);
  box-shadow: 0 4px 20px var(--color-accent-glow);
  transition: transform var(--transition-fast);
}
.brand-icon:hover { transform: rotate(-5deg) scale(1.05); }
.login-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.login-form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
}
.remember-line {
  flex-direction: row !important;
  align-items: center;
  gap: 8px !important;
  font-size: 13px;
  color: var(--color-text-muted);
}
.btn {
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  padding: 11px 14px;
  cursor: pointer;
  background: var(--color-bg-panel);
  color: var(--color-text-secondary);
  font-weight: 600;
  transition: all var(--transition-fast);
}
.btn:hover { transform: translateY(-1px); box-shadow: var(--shadow-md); }
.btn:disabled { opacity: 0.55; cursor: not-allowed; transform: none; }
.btn-primary {
  color: #fff;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover));
  font-size: 15px;
  padding: 12px 14px;
  letter-spacing: 2px;
  box-shadow: 0 4px 16px var(--color-accent-glow);
}
.btn-primary:hover:not(:disabled) {
  box-shadow: 0 6px 24px var(--color-accent-glow);
  transform: translateY(-2px);
}
.login-hint { font-size: 12px; color: var(--color-text-muted); text-align: center; }
.error-text {
  color: var(--color-danger);
  font-size: 13px;
  font-weight: 500;
  animation: fadeIn 0.3s ease;
}
</style>
