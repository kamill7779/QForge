import type { LoginResponse } from './types'

export const authApi = {
  async login(username: string, password: string): Promise<LoginResponse> {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    })

    if (!res.ok) {
      throw new Error('登录失败，请检查用户名和密码')
    }

    return res.json()
  }
}