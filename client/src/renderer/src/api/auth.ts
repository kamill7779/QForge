/**
 * auth.ts — Authentication API.
 *
 * Uses the dedicated auth IPC channel (window.qforge.auth.login)
 * rather than the generic api.request, matching the Electron main process handler.
 */

import type { LoginResponse } from './types'
import { ApiError } from './client'

export const authApi = {
  /** Login and receive JWT token. */
  async login(username: string, password: string): Promise<LoginResponse> {
    const res = await window.qforge.auth.login(username, password)
    if (res.status >= 400) throw new ApiError(res.status, res.body)
    return res.body as LoginResponse
  }
}
