import {
  app,
  BrowserWindow,
  ipcMain,
  globalShortcut,
  desktopCapturer,
  screen
} from 'electron'
import { join } from 'path'
import { readFileSync, writeFileSync, existsSync, mkdirSync, unlinkSync } from 'fs'

// ──────────────────── Config ────────────────────

const API_BASE_URL = process.env.QFORGE_API_BASE_URL || 'http://localhost:8080'
const WS_BASE_URL = process.env.QFORGE_WS_BASE_URL || 'ws://localhost:8080'

const SHORTCUTS = {
  OCR: process.env.QFORGE_SHORTCUT_OCR || 'CommandOrControl+Alt+A',
  INSERT_IMAGE: process.env.QFORGE_SHORTCUT_INSERT_IMAGE || 'CommandOrControl+Alt+Q',
  CHOICE_IMAGE: process.env.QFORGE_SHORTCUT_CHOICE_IMAGE || 'CommandOrControl+Alt+1'
}

// ──────────────────── Credential helpers ────────────────────

function credentialsFilePath(): string {
  return join(app.getPath('userData'), 'qforge-credentials.json')
}

function loadCredentials(): { username: string; token: string } | null {
  try {
    const raw = readFileSync(credentialsFilePath(), 'utf-8')
    const data = JSON.parse(raw)
    if (data?.username && data?.token) return { username: data.username, token: data.token }
  } catch {
    /* ignore */
  }
  return null
}

function saveCredentials(payload: { username: string; token: string }): void {
  const dir = app.getPath('userData')
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true })
  writeFileSync(credentialsFilePath(), JSON.stringify(payload, null, 2), 'utf-8')
}

function clearCredentials(): void {
  try {
    unlinkSync(credentialsFilePath())
  } catch {
    /* ignore */
  }
}

// ──────────────────── HTTP proxy ────────────────────

interface BackendRequestParams {
  endpointPath: string
  method?: string
  token?: string
  body?: unknown
}

interface BackendMultipartParams {
  endpointPath: string
  token?: string
  filePaths?: string[]
  fields?: Record<string, string>
}

interface BackendResponse {
  status: number
  body: unknown
}

async function parseResponse(res: Response): Promise<BackendResponse> {
  let body: unknown = null
  const ct = res.headers.get('content-type') || ''
  if (ct.includes('application/json')) {
    body = await res.json()
  } else {
    body = await res.text()
  }
  return { status: res.status, body }
}

async function requestBackend({
  endpointPath,
  method = 'GET',
  token,
  body
}: BackendRequestParams): Promise<BackendResponse> {
  const url = `${API_BASE_URL}${endpointPath}`
  const headers: Record<string, string> = {}
  if (token) headers['Authorization'] = `Bearer ${token}`

  let fetchBody: string | undefined
  if (body !== undefined && body !== null) {
    headers['Content-Type'] = 'application/json'
    fetchBody = JSON.stringify(body)
  }

  const res = await fetch(url, { method, headers, body: fetchBody })
  return parseResponse(res)
}

async function requestBackendMultipart({
  endpointPath,
  token,
  filePaths = [],
  fields = {}
}: BackendMultipartParams): Promise<BackendResponse> {
  const url = `${API_BASE_URL}${endpointPath}`
  const boundary = `----QForgeBoundary${Date.now()}${Math.random().toString(36).slice(2)}`
  const parts: Buffer[] = []

  for (const fp of filePaths) {
    const fileName = fp.replace(/\\/g, '/').split('/').pop() || 'file'
    const fileData = readFileSync(fp)
    parts.push(
      Buffer.from(
        `--${boundary}\r\nContent-Disposition: form-data; name="files"; filename="${fileName}"\r\nContent-Type: application/octet-stream\r\n\r\n`
      ),
      fileData,
      Buffer.from('\r\n')
    )
  }

  for (const [key, val] of Object.entries(fields)) {
    parts.push(
      Buffer.from(
        `--${boundary}\r\nContent-Disposition: form-data; name="${key}"\r\n\r\n${val}\r\n`
      )
    )
  }

  parts.push(Buffer.from(`--${boundary}--\r\n`))

  const bodyBuffer = Buffer.concat(parts)
  const headers: Record<string, string> = {
    'Content-Type': `multipart/form-data; boundary=${boundary}`,
    'Content-Length': String(bodyBuffer.length)
  }
  if (token) headers['Authorization'] = `Bearer ${token}`

  const res = await fetch(url, { method: 'POST', headers, body: bodyBuffer })
  return parseResponse(res)
}

// ──────────────────── Screenshot ────────────────────

let screenshotWindow: BrowserWindow | null = null

function screenshotDir(): string {
  return join(app.getAppPath(), 'resources', 'screenshot')
}

async function captureTargetDisplay(targetWindow: BrowserWindow) {
  const bounds = targetWindow.getBounds()
  const cx = bounds.x + Math.floor(bounds.width / 2)
  const cy = bounds.y + Math.floor(bounds.height / 2)
  const targetDisplay = screen.getDisplayNearestPoint({ x: cx, y: cy })

  const sources = await desktopCapturer.getSources({
    types: ['screen'],
    thumbnailSize: { width: targetDisplay.size.width, height: targetDisplay.size.height }
  })

  const match =
    sources.find((s) => {
      const num = parseInt(s.display_id || '', 10)
      return !isNaN(num) && num === targetDisplay.id
    }) || sources[0]

  if (!match) throw new Error('No screen source found')
  return { imageDataUrl: match.thumbnail.toDataURL(), display: targetDisplay }
}

function openScreenshotWindow(meta: { imageDataUrl: string; display: Electron.Display }, intent = 'ocr') {
  if (screenshotWindow && !screenshotWindow.isDestroyed()) screenshotWindow.close()

  const { bounds } = meta.display
  screenshotWindow = new BrowserWindow({
    x: bounds.x,
    y: bounds.y,
    width: bounds.width,
    height: bounds.height,
    frame: false,
    transparent: true,
    alwaysOnTop: true,
    resizable: false,
    skipTaskbar: true,
    fullscreenable: true,
    webPreferences: {
      preload: join(screenshotDir(), 'screenshot-preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  })

  screenshotWindow.setFullScreen(true)
  screenshotWindow.loadFile(join(screenshotDir(), 'index.html'))

  screenshotWindow.webContents.once('did-finish-load', () => {
    screenshotWindow?.webContents.send('screenshot:init', {
      imageDataUrl: meta.imageDataUrl,
      shortcut: SHORTCUTS.OCR,
      intent
    })
  })

  screenshotWindow.on('closed', () => {
    screenshotWindow = null
  })
}

function closeScreenshotWindow() {
  if (screenshotWindow && !screenshotWindow.isDestroyed()) screenshotWindow.close()
  screenshotWindow = null
}

// ──────────────────── Main Window ────────────────────

let mainWindow: BrowserWindow | null = null

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1520,
    height: 980,
    minWidth: 1100,
    minHeight: 700,
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  })

  // electron-vite: dev server vs production build
  if (!app.isPackaged && process.env['ELECTRON_RENDERER_URL']) {
    mainWindow.loadURL(process.env['ELECTRON_RENDERER_URL'])
  } else {
    mainWindow.loadFile(join(__dirname, '../renderer/index.html'))
  }
}

// ──────────────────── IPC handlers ────────────────────

ipcMain.handle('config:get', () => ({
  apiBaseUrl: API_BASE_URL,
  wsBaseUrl: WS_BASE_URL,
  shortcuts: SHORTCUTS
}))

ipcMain.handle('auth:login', async (_e, { username, password }) => {
  return requestBackend({
    endpointPath: '/api/auth/login',
    method: 'POST',
    body: { username, password }
  })
})

ipcMain.handle('api:request', async (_e, { endpointPath, method, token, body }) => {
  return requestBackend({ endpointPath, method, token, body })
})

ipcMain.handle('api:upload-multipart', async (_e, { endpointPath, token, filePaths, fields }) => {
  return requestBackendMultipart({ endpointPath, token, filePaths, fields })
})

ipcMain.handle('credentials:load', () => loadCredentials())
ipcMain.handle('credentials:save', (_e, payload) => saveCredentials(payload))
ipcMain.handle('credentials:clear', () => clearCredentials())

ipcMain.handle('screenshot:trigger', async (_e, params?: { intent?: string }) => {
  if (!mainWindow) return { success: false, error: 'No window' }
  try {
    const meta = await captureTargetDisplay(mainWindow)
    openScreenshotWindow(meta, params?.intent || 'ocr')
    return { success: true }
  } catch (err: unknown) {
    return { success: false, error: (err as Error).message }
  }
})

ipcMain.on('screenshot:cancel', () => closeScreenshotWindow())

ipcMain.on('screenshot:confirm', (_e, payload) => {
  closeScreenshotWindow()
  mainWindow?.webContents.send('screenshot:captured', payload)
})

// ──────────────────── Global Shortcuts ────────────────────

function registerShortcuts() {
  const shortcutIntents: Record<string, string> = {
    [SHORTCUTS.OCR]: 'ocr',
    [SHORTCUTS.INSERT_IMAGE]: 'insert-image',
    [SHORTCUTS.CHOICE_IMAGE]: 'choice-image'
  }

  for (const [accelerator, intent] of Object.entries(shortcutIntents)) {
    if (!accelerator) continue
    try {
      globalShortcut.register(accelerator, () => {
        if (!mainWindow) return
        captureTargetDisplay(mainWindow)
          .then((meta) => openScreenshotWindow(meta, intent))
          .catch(() => {})
      })
    } catch {
      /* ignore */
    }
  }
}

// ──────────────────── App Lifecycle ────────────────────

app.whenReady().then(() => {
  createWindow()
  registerShortcuts()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})

app.on('will-quit', () => {
  globalShortcut.unregisterAll()
})
