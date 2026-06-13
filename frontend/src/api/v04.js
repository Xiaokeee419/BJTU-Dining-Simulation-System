import { http, unwrapResponse } from './http'

export function getStudentPoolSummary(config = {}) {
  return http.get('/data/student-pool-summary', config).then(unwrapResponse)
}

export function previewArrivalCurve(payload, config = {}) {
  return http
    .post('/simulations/arrival-curve-preview', payload, config)
    .then(unwrapResponse)
}
