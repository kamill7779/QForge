/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, never>, Record<string, never>, unknown>
  export default component
}

// ── Electron IPC bridge (exposed by preload as window.qforge) ──

interface QForgeConfig {
  apiBaseUrl: string
  wsBaseUrl: string
  shortcuts: Record<string, string>
}

interface QForgeApiResponse {
  status: number
  body: unknown
}

interface QForgeScreenshotPayload {
  imageBase64: string
  width: number
  height: number
  intent?: string
}

interface QForgeAPI {
  config: {
    get(): Promise<QForgeConfig>
  }
  auth: {
    login(username: string, password: string): Promise<QForgeApiResponse>
  }
  api: {
    request(
      endpointPath: string,
      method: string,
      token: string,
      body?: unknown
    ): Promise<QForgeApiResponse>
    uploadMultipart(
      endpointPath: string,
      token: string,
      filePaths: string[],
      fields?: Record<string, string>
    ): Promise<QForgeApiResponse>
  }
  credentials: {
    load(): Promise<{ username: string; token: string } | null>
    save(payload: { username: string; token: string }): Promise<void>
    clear(): Promise<void>
  }
  screenshot: {
    trigger(payload?: Record<string, unknown>): Promise<{ success: boolean; error?: string }>
    onCaptured(handler: (payload: QForgeScreenshotPayload) => void): () => void
    onError(handler: (payload: { error: string }) => void): () => void
  }
}

interface Window {
  qforge: QForgeAPI
}
