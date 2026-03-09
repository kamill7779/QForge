/**
 * stores/auth.ts — Authentication state (Web version).
 * Uses localStorage instead of Electron IPC.
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'

const LS_TOKEN = 'qforge_token'
const LS_USERNAME = 'qforge_username'

export const useAuthStore = defineStore('auth', () => {
  const token = ref('')
  const username = ref('')

  const isLoggedIn = computed(() => !!token.value && !!username.value)

  /** Try to restore session from localStorage. */
  function loadSavedCredentials(): { username: string; token: string } | null {
    const t = localStorage.getItem(LS_TOKEN)
    const u = localStorage.getItem(LS_USERNAME)
    if (t && u) return { username: u, token: t }
    return null
  }

  function restoreSession(user: string, savedToken: string): void {
    token.value = savedToken
    username.value = user
  }

  async function login(user: string, password: string, remember = true): Promise<void> {
    const res = await authApi.login(user, password)
    token.value = res.accessToken
    username.value = user
    if (remember) {
      localStorage.setItem(LS_TOKEN, res.accessToken)
      localStorage.setItem(LS_USERNAME, user)
    }
  }

  function logout(): void {
    token.value = ''
    username.value = ''
    localStorage.removeItem(LS_TOKEN)
    localStorage.removeItem(LS_USERNAME)
  }

  function $reset(): void {
    token.value = ''
    username.value = ''
  }

  return {
    token,
    username,
    isLoggedIn,
    loadSavedCredentials,
    restoreSession,
    login,
    logout,
    $reset
  }
})
