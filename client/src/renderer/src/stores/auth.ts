/**
 * stores/auth.ts — Authentication state management.
 *
 * Manages JWT token, username, config, login/logout, and credential persistence.
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import { useRouter } from 'vue-router'

export const useAuthStore = defineStore('auth', () => {
  // ── State ──

  const token = ref('')
  const username = ref('')
  const config = ref<{
    apiBaseUrl: string
    wsBaseUrl: string
    shortcuts: Record<string, string>
  }>({
    apiBaseUrl: '',
    wsBaseUrl: '',
    shortcuts: {}
  })

  // ── Getters ──

  const isLoggedIn = computed(() => !!token.value && !!username.value)

  // ── Actions ──

  /** Load Electron config (API base URL, WS URL, shortcuts). */
  async function loadConfig(): Promise<void> {
    const cfg = await window.qforge.config.get()
    config.value = cfg
  }

  /**
   * Login with username/password.
   * On success, stores token and optionally persists credentials.
   * Returns true on success.
   */
  async function login(
    user: string,
    password: string,
    remember: boolean = false
  ): Promise<void> {
    const res = await authApi.login(user, password)
    token.value = res.accessToken
    username.value = user

    if (remember) {
      await window.qforge.credentials.save({
        username: user,
        token: res.accessToken
      })
    }
  }

  /** Logout — clear state, clear credentials, close WS. */
  async function logout(): Promise<void> {
    token.value = ''
    username.value = ''
    await window.qforge.credentials.clear()
  }

  /**
   * Attempt auto-login from saved credentials.
   * Returns { username, token } if found, or null.
   */
  async function loadSavedCredentials(): Promise<{
    username: string
    token: string
  } | null> {
    return window.qforge.credentials.load()
  }

  /**
   * Restore session from saved credentials (skip the login API call).
   * Used when credentials were persisted from a previous session.
   */
  function restoreSession(user: string, savedToken: string): void {
    token.value = savedToken
    username.value = user
  }

  /** Reset all auth state. */
  function $reset(): void {
    token.value = ''
    username.value = ''
    config.value = { apiBaseUrl: '', wsBaseUrl: '', shortcuts: {} }
  }

  return {
    // state
    token,
    username,
    config,
    // getters
    isLoggedIn,
    // actions
    loadConfig,
    login,
    logout,
    loadSavedCredentials,
    restoreSession,
    $reset
  }
})
