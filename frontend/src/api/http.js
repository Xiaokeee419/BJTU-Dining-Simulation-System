import axios from 'axios'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 10000,
})

export function unwrapResponse(response) {
  return response.data?.data ?? response.data
}
