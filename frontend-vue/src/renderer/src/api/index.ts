/**
 * api/index.ts — Barrel export for all API modules.
 */

export { ApiError, apiRequest, apiUpload } from './client'
export { authApi } from './auth'
export { questionApi } from './question'
export { examParseApi } from './examParse'
export { tagApi } from './tag'
export { WsClient } from './ws'
export type { WsClientOptions } from './ws'
export type * from './types'
