import axios from 'axios'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isCancel(error) || error?.code === 'ERR_CANCELED') {
      return Promise.reject(error)
    }
    const body = error?.response?.data
    const reason = body?.data?.reason
    const message = reason || body?.message || error?.message || '接口请求失败'
    const normalized = new Error(message)
    normalized.name = 'ApiError'
    normalized.code = body?.code ?? error?.code
    normalized.status = error?.response?.status
    normalized.data = body?.data
    normalized.traceId = body?.traceId
    normalized.endpoint = error?.config?.url
    return Promise.reject(normalized)
  },
)

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

export function isCanceledRequest(error) {
  return axios.isCancel(error) || error?.code === 'ERR_CANCELED'
}
