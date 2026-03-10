import { apiRequest } from './client'
import type {
  BasketComposeDetailResponse,
  SaveBasketComposeContentRequest,
  UpdateBasketComposeMetaRequest,
  ExamPaperDetailResponse
} from './types'

export const basketComposeApi = {
  detail(): Promise<BasketComposeDetailResponse> {
    return apiRequest('GET', '/api/question-basket/compose')
  },

  updateMeta(request: UpdateBasketComposeMetaRequest): Promise<BasketComposeDetailResponse> {
    return apiRequest('PUT', '/api/question-basket/compose/meta', request)
  },

  saveContent(request: SaveBasketComposeContentRequest): Promise<BasketComposeDetailResponse> {
    return apiRequest('PUT', '/api/question-basket/compose/content', request)
  },

  confirm(): Promise<ExamPaperDetailResponse> {
    return apiRequest('POST', '/api/question-basket/compose/confirm')
  }
}
