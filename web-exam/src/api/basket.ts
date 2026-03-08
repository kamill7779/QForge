/**
 * api/basket.ts — Question basket (cart) API client.
 */

import { apiRequest } from './client'
import type { BasketItemResponse } from './types'

export const basketApi = {
  /** List all items in basket (with question info). */
  list(): Promise<BasketItemResponse[]> {
    return apiRequest('GET', '/api/question-basket')
  },

  /** List only question UUIDs in basket (lightweight). */
  listUuids(): Promise<string[]> {
    return apiRequest('GET', '/api/question-basket/uuids')
  },

  /** Add a question to the basket (idempotent). */
  add(questionUuid: string): Promise<void> {
    return apiRequest('POST', `/api/question-basket/${questionUuid}`)
  },

  /** Toggle a question in/out of the basket. Returns { inBasket: boolean }. */
  toggle(questionUuid: string): Promise<{ inBasket: boolean }> {
    return apiRequest('POST', `/api/question-basket/${questionUuid}/toggle`)
  },

  /** Remove a question from the basket. */
  remove(questionUuid: string): Promise<void> {
    return apiRequest('DELETE', `/api/question-basket/${questionUuid}`)
  },

  /** Clear the entire basket. */
  clear(): Promise<void> {
    return apiRequest('DELETE', '/api/question-basket')
  }
}
