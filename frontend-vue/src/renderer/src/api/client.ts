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
      typeof body === 'object' && body !== null && 'message' in body
        ? (body as { message: string }).message
        : `HTTP ${status}`
    super(msg)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
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
  const res = await window.qforge.api.request(path, method, token, body)
  if (res.status >= 400) throw new ApiError(res.status, res.body)
  return res.body as T
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
  if (res.status >= 400) throw new ApiError(res.status, res.body)
  return res.body as T
}
