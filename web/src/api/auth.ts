/**
 * api/auth.ts — Web version: uses native fetch instead of Electron IPC.
 */

export interface LoginResponse {
  accessToken: string
  expiresInSeconds: number
  tokenType: string
}

export const authApi = {
  async login(username: string, password: string): Promise<LoginResponse> {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    })

    if (!res.ok) {
      const errText = await res.text()
      let msg = '登录失败'
      try {
        const err = JSON.parse(errText)
        msg = err.message || err.error || msg
      } catch { /* ignore */ }
      throw new Error(msg)
    }

    return res.json()
  },

  async me(token: string): Promise<{ username: string }> {
    const res = await fetch('/api/auth/me', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    if (!res.ok) throw new Error('Token invalid')
    return res.json()
  }
}
