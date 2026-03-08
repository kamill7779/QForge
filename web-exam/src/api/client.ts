/**
 * api/client.ts — Web version: uses native fetch() instead of Electron IPC.
 */

import { useAuthStore } from '@/stores/auth'

export class ApiError extends Error {
  constructor(
    public status: number,
    public body: string
  ) {
    super(`API ${status}: ${body}`)
    this.name = 'ApiError'
  }
}

type On401Fn = () => void
let _on401: On401Fn | null = null
export function registerOn401(fn: On401Fn) { _on401 = fn }
export function unregisterOn401() { _on401 = null }

/**
 * Generic API request using native fetch.
 * Base URL is proxied via Vite config (/api → localhost:8080).
 */
export async function apiRequest<T>(
  method: string,
  path: string,
  body?: unknown,
  token?: string | null
): Promise<T> {
  const auth = useAuthStore()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  }

  const effectiveToken = token ?? auth.token
  if (effectiveToken) {
    headers['Authorization'] = `Bearer ${effectiveToken}`
  }

  console.log(`[api] ${method} ${path}`, body != null ? '(has body)' : '')
  const start = performance.now()

  const opts: RequestInit = { method, headers }
  if (body != null) {
    opts.body = JSON.stringify(body)
  }

  const res = await fetch(path, opts)
  const elapsed = (performance.now() - start).toFixed(0)
  console.log(`[api] ${method} ${path} → ${res.status} (${elapsed}ms)`)

  if (res.status === 401) {
    console.warn('[api] 401 — session expired')
    if (_on401) _on401()
    throw new ApiError(res.status, await res.text())
  }

  if (res.status < 200 || res.status >= 300) {
    const errText = await res.text()
    throw new ApiError(res.status, errText)
  }

  const text = await res.text()
  if (!text || text.trim() === '') return undefined as unknown as T
  return JSON.parse(text) as T
}

/**
 * Upload files using FormData (web native).
 */
export async function apiUpload<T>(
  path: string,
  fields: Record<string, string>,
  files: Array<{ field: string; file: File }>,
  token?: string | null
): Promise<T> {
  const auth = useAuthStore()
  const formData = new FormData()

  for (const [key, value] of Object.entries(fields)) {
    formData.append(key, value)
  }

  for (const { field, file } of files) {
    formData.append(field, file)
  }

  const headers: Record<string, string> = {}
  const effectiveToken = token ?? auth.token
  if (effectiveToken) {
    headers['Authorization'] = `Bearer ${effectiveToken}`
  }

  console.log(`[api] UPLOAD ${path}`, { fields, files: files.map(f => f.file.name) })
  const start = performance.now()

  const res = await fetch(path, {
    method: 'POST',
    headers,
    body: formData
  })

  const elapsed = (performance.now() - start).toFixed(0)
  console.log(`[api] UPLOAD ${path} → ${res.status} (${elapsed}ms)`)

  if (res.status === 401) {
    if (_on401) _on401()
    throw new ApiError(res.status, await res.text())
  }

  if (res.status < 200 || res.status >= 300) {
    throw new ApiError(res.status, await res.text())
  }

  const text = await res.text()
  if (!text || text.trim() === '') return undefined as unknown as T
  return JSON.parse(text) as T
}
