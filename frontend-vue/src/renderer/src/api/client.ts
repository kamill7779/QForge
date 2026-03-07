/**
 * client.ts — Unified HTTP client via Electron IPC bridge.
 *
 * Wraps window.qforge.api.request() with error handling and type safety.
 * The token is always passed explicitly — no circular store dependency.
 */

export class ApiError extends Error {
  public readonly status: number
  public readonly body: unknown

  constructor(status: number, body: unknown) {
    const msg =
      status === 401
        ? '登录已过期，请重新登录'
        : typeof body === 'object' && body !== null && 'message' in body
          ? (body as { message: string }).message
          : `HTTP ${status}`
    super(msg)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

/** Global 401 callback — set by AppShell to redirect to login. */
let on401: (() => void) | null = null
export function registerOn401(cb: () => void): void { on401 = cb }
export function unregisterOn401(): void { on401 = null }

function handle401(status: number): void {
  if (status === 401 && on401) on401()
}

/**
 * Send a JSON request through the Electron IPC bridge.
 * Throws ApiError for status >= 400.
 */
export async function apiRequest<T = unknown>(
  path: string,
  method: string = 'GET',
  token: string = '',
  body?: unknown
): Promise<T> {
  // Deep-clone body to strip Vue reactive proxies — Electron IPC requires
  // structuredClone-compatible plain objects; Proxy instances cause
  // "An object could not be cloned" errors.
  const plainBody = body !== undefined ? JSON.parse(JSON.stringify(body)) : undefined

  const tag = `[API] ${method} ${path}`
  console.log(tag, plainBody !== undefined ? '→' : '', plainBody !== undefined ? plainBody : '')
  const t0 = performance.now()
  try {
    const res = await window.qforge.api.request(path, method, token, plainBody)
    const ms = (performance.now() - t0).toFixed(0)
    if (res.status >= 400) {
      console.error(`${tag} ← ${res.status} (${ms}ms)`, res.body)
      handle401(res.status)
      throw new ApiError(res.status, res.body)
    }
    console.log(`${tag} ← ${res.status} (${ms}ms)`)
    return res.body as T
  } catch (err) {
    if (err instanceof ApiError) throw err
    const ms = (performance.now() - t0).toFixed(0)
    console.error(`${tag} FAILED (${ms}ms)`, err)
    throw err
  }
}

/**
 * Upload files via multipart through the Electron IPC bridge.
 * Throws ApiError for status >= 400.
 */
export async function apiUpload<T = unknown>(
  path: string,
  token: string,
  filePaths: string[],
  fields?: Record<string, string>
): Promise<T> {
  const res = await window.qforge.api.uploadMultipart(path, token, filePaths, fields)
  if (res.status >= 400) {
    handle401(res.status)
    throw new ApiError(res.status, res.body)
  }
  return res.body as T
}
