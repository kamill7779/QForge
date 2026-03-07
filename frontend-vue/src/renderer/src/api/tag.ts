/**
 * tag.ts — Tag catalog API.
 */

import { apiRequest } from './client'
import type { TagCatalogResponse } from './types'

export const tagApi = {
  /** Fetch the full tag catalog (main categories + secondary). */
  getCatalog: (token: string) =>
    apiRequest<TagCatalogResponse>('/api/tags', 'GET', token)
}
