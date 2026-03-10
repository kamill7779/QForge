import { describe, expect, it } from 'vitest'
import { resolveApiBaseUrl, resolveWsBaseUrl } from '../../../src/main/config'

describe('main config', () => {
  it('uses 49.235.113.163 defaults when env vars are absent', () => {
    expect(resolveApiBaseUrl({})).toBe('http://49.235.113.163')
    expect(resolveWsBaseUrl({})).toBe('ws://49.235.113.163')
  })

  it('prefers explicit env vars over defaults', () => {
    expect(
      resolveApiBaseUrl({ QFORGE_API_BASE_URL: 'https://api.example.com' })
    ).toBe('https://api.example.com')
    expect(
      resolveWsBaseUrl({ QFORGE_WS_BASE_URL: 'wss://ws.example.com' })
    ).toBe('wss://ws.example.com')
  })
})
