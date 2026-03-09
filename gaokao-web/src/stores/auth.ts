import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api/auth'

const LS_TOKEN = 'qforge_gaokao_token'
const LS_USERNAME = 'qforge_gaokao_username'

export const useAuthStore = defineStore('auth', () => {
  const token = ref('')
  const username = ref('')

  const isLoggedIn = computed(() => Boolean(token.value && username.value))

  function loadSavedCredentials(): { username: string; token: string } | null {
    const savedToken = localStorage.getItem(LS_TOKEN)
    const savedUsername = localStorage.getItem(LS_USERNAME)
    if (!savedToken || !savedUsername) return null
    return { username: savedUsername, token: savedToken }
  }

  function restoreSession(nextUsername: string, nextToken: string) {
    username.value = nextUsername
    token.value = nextToken
  }

  async function login(nextUsername: string, password: string) {
    const result = await authApi.login(nextUsername, password)
    username.value = nextUsername
    token.value = result.accessToken
    localStorage.setItem(LS_TOKEN, result.accessToken)
    localStorage.setItem(LS_USERNAME, nextUsername)
  }

  function logout() {
    username.value = ''
    token.value = ''
    localStorage.removeItem(LS_TOKEN)
    localStorage.removeItem(LS_USERNAME)
  }

  return {
    token,
    username,
    isLoggedIn,
    loadSavedCredentials,
    restoreSession,
    login,
    logout
  }
})