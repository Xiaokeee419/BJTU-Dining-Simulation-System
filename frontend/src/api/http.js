import axios from 'axios'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

export function unwrapResponse(response) {
  const body = response.data
  if (body && typeof body === 'object' && 'code' in body) {
    if (body.code !== 0) {
      const error = new Error(body.message || '接口请求失败')
      error.code = body.code
      error.data = body.data
      error.traceId = body.traceId
      throw error
    }
    return body.data
  }
  return body?.data ?? body
}
