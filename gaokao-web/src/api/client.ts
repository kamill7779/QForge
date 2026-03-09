import { useAuthStore } from '@/stores/auth'

export class ApiError extends Error {
  constructor(
    public status: number,
    public body: string
  ) {
    super(resolveApiErrorMessage(status, body))
    this.name = 'ApiError'
  }
}

function resolveApiErrorMessage(status: number, body: string) {
  if (!body.trim()) {
    return `API ${status}`
  }

  try {
    const parsed = JSON.parse(body) as { message?: string; code?: string }
    if (parsed.message) {
      return parsed.message
    }
    if (parsed.code) {
      return `${parsed.code} (${status})`
    }
  } catch {
    // Ignore JSON parse failure and fall back to raw body.
  }

  return body.length > 240 ? `${body.slice(0, 237)}...` : body
}

type On401Fn = () => void
let on401: On401Fn | null = null

export function registerOn401(fn: On401Fn) {
  on401 = fn
}

export function unregisterOn401() {
  on401 = null
}

export async function apiRequest<T>(
  method: string,
  path: string,
  body?: unknown,
  token?: string | null
): Promise<T> {
  const auth = useAuthStore()
  const headers: Record<string, string> = {}
  const effectiveToken = token ?? auth.token

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (effectiveToken) {
    headers.Authorization = `Bearer ${effectiveToken}`
  }

  const res = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined
  })

  if (res.status === 401) {
    if (on401) on401()
    throw new ApiError(res.status, await res.text())
  }

  if (!res.ok) {
    throw new ApiError(res.status, await res.text())
  }

  const text = await res.text()
  if (!text.trim()) {
    return undefined as T
  }

  return JSON.parse(text) as T
}

export async function apiUpload<T>(
  path: string,
  files: File[],
  fields: Record<string, string> = {},
  token?: string | null
): Promise<T> {
  const auth = useAuthStore()
  const effectiveToken = token ?? auth.token
  const formData = new FormData()

  for (const file of files) {
    formData.append('files', file)
  }

  for (const [key, value] of Object.entries(fields)) {
    formData.append(key, value)
  }

  const headers: Record<string, string> = {}
  if (effectiveToken) {
    headers.Authorization = `Bearer ${effectiveToken}`
  }

  const res = await fetch(path, {
    method: 'POST',
    headers,
    body: formData
  })

  if (res.status === 401) {
    if (on401) on401()
    throw new ApiError(res.status, await res.text())
  }

  if (!res.ok) {
    throw new ApiError(res.status, await res.text())
  }

  const text = await res.text()
  if (!text.trim()) {
    return undefined as T
  }

  return JSON.parse(text) as T
}