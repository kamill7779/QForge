import { contextBridge, ipcRenderer } from 'electron'

// ── Type-safe IPC bridge exposed to renderer as window.qforge ──

const api = {
  config: {
    get: (): Promise<{ apiBaseUrl: string; wsBaseUrl: string; shortcuts: Record<string, string> }> =>
      ipcRenderer.invoke('config:get')
  },

  auth: {
    login: (
      username: string,
      password: string
    ): Promise<{ status: number; body: unknown }> =>
      ipcRenderer.invoke('auth:login', { username, password })
  },

  api: {
    request: (
      endpointPath: string,
      method: string,
      token: string,
      body?: unknown
    ): Promise<{ status: number; body: unknown }> =>
      ipcRenderer.invoke('api:request', { endpointPath, method, token, body }),

    uploadMultipart: (
      endpointPath: string,
      token: string,
      filePaths: string[],
      fields?: Record<string, string>
    ): Promise<{ status: number; body: unknown }> =>
      ipcRenderer.invoke('api:upload-multipart', { endpointPath, token, filePaths, fields })
  },

  credentials: {
    load: (): Promise<{ username: string; token: string } | null> =>
      ipcRenderer.invoke('credentials:load'),
    save: (payload: { username: string; token: string }): Promise<void> =>
      ipcRenderer.invoke('credentials:save', payload),
    clear: (): Promise<void> => ipcRenderer.invoke('credentials:clear')
  },

  screenshot: {
    trigger: (
      payload?: Record<string, unknown>
    ): Promise<{ success: boolean; error?: string }> =>
      ipcRenderer.invoke('screenshot:trigger', payload || {}),

    onCaptured: (
      handler: (payload: { imageBase64: string; width: number; height: number }) => void
    ): (() => void) => {
      const wrapped = (_event: Electron.IpcRendererEvent, p: unknown) =>
        handler((p || {}) as { imageBase64: string; width: number; height: number })
      ipcRenderer.on('screenshot:captured', wrapped)
      return () => ipcRenderer.removeListener('screenshot:captured', wrapped)
    },

    onError: (handler: (payload: { error: string }) => void): (() => void) => {
      const wrapped = (_event: Electron.IpcRendererEvent, p: unknown) =>
        handler((p || {}) as { error: string })
      ipcRenderer.on('screenshot:error', wrapped)
      return () => ipcRenderer.removeListener('screenshot:error', wrapped)
    }
  }
}

contextBridge.exposeInMainWorld('qforge', api)
