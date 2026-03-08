import { apiRequest } from './client'
import type { TagCatalogResponse } from './types'

export const tagApi = {
  getCatalog(token: string) {
    return apiRequest<TagCatalogResponse>('GET', '/api/tags', undefined, token)
  }
}
