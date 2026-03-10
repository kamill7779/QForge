type EnvMap = Record<string, string | undefined>

export const DEFAULT_API_BASE_URL = 'http://49.235.113.163'
export const DEFAULT_WS_BASE_URL = 'ws://49.235.113.163'

export function resolveApiBaseUrl(env: EnvMap = process.env): string {
  return env.QFORGE_API_BASE_URL || DEFAULT_API_BASE_URL
}

export function resolveWsBaseUrl(env: EnvMap = process.env): string {
  return env.QFORGE_WS_BASE_URL || DEFAULT_WS_BASE_URL
}
