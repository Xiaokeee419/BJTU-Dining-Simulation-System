import { http, unwrapResponse } from './http'
import { mockGenerateRecommendation, mockGetRecommendation } from './mock'

const useMock = import.meta.env.VITE_USE_MOCK !== 'false'

export async function generateRecommendation(payload) {
  if (useMock) return mockGenerateRecommendation(payload)
  return http.post('/recommendations/generate', payload).then(unwrapResponse)
}

export async function getRecommendationResult(runId, params = {}) {
  if (useMock) return mockGetRecommendation(runId, params.minute)
  return http.get(`/recommendations/runs/${runId}`, { params }).then(unwrapResponse)
}
