/**
 * ws.ts — WebSocket client with auto-reconnect.
 *
 * Connects to the backend WebSocket endpoint for real-time OCR/parse updates.
 */

import type { WsMessage } from './types'

export interface WsClientOptions {
  /** Full WebSocket URL (e.g. ws://localhost:8080/ws/questions?user=admin) */
  url: string
  /** Called when a message is received */
  onMessage: (msg: WsMessage) => void
  /** Called when connection state changes */
  onStatusChange?: (connected: boolean) => void
  /** Reconnect delay in ms (default: 3000) */
  reconnectDelay?: number
  /** Max reconnect attempts (default: Infinity) */
  maxRetries?: number
}

export class WsClient {
  private ws: WebSocket | null = null
  private url: string
  private onMessage: (msg: WsMessage) => void
  private onStatusChange?: (connected: boolean) => void
  private reconnectDelay: number
  private maxRetries: number
  private retryCount = 0
  private destroyed = false
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null

  constructor(options: WsClientOptions) {
    this.url = options.url
    this.onMessage = options.onMessage
    this.onStatusChange = options.onStatusChange
    this.reconnectDelay = options.reconnectDelay ?? 3000
    this.maxRetries = options.maxRetries ?? Infinity
  }

  /** Open the WebSocket connection. */
  connect(): void {
    if (this.destroyed) return
    this.cleanup()

    try {
      this.ws = new WebSocket(this.url)
    } catch {
      this.scheduleReconnect()
      return
    }

    this.ws.onopen = () => {
      this.retryCount = 0
      this.onStatusChange?.(true)
    }

    this.ws.onmessage = (event) => {
      try {
        const msg: WsMessage = JSON.parse(event.data)
        this.onMessage(msg)
      } catch {
        // Ignore malformed messages
      }
    }

    this.ws.onclose = () => {
      this.onStatusChange?.(false)
      this.scheduleReconnect()
    }

    this.ws.onerror = () => {
      // onclose will fire after onerror
    }
  }

  /** Update the WebSocket URL (e.g. when user changes). Reconnects automatically. */
  updateUrl(url: string): void {
    this.url = url
    this.connect()
  }

  /** Close the connection and stop reconnecting. */
  destroy(): void {
    this.destroyed = true
    this.cleanup()
  }

  /** Get current connection state. */
  get connected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN
  }

  private cleanup(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.ws) {
      this.ws.onopen = null
      this.ws.onmessage = null
      this.ws.onclose = null
      this.ws.onerror = null
      if (
        this.ws.readyState === WebSocket.OPEN ||
        this.ws.readyState === WebSocket.CONNECTING
      ) {
        this.ws.close()
      }
      this.ws = null
    }
  }

  private scheduleReconnect(): void {
    if (this.destroyed) return
    if (this.retryCount >= this.maxRetries) return

    this.retryCount++
    this.reconnectTimer = setTimeout(() => {
      this.connect()
    }, this.reconnectDelay)
  }
}
