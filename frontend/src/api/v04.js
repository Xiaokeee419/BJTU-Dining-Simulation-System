import { http, unwrapResponse } from './http'

export function getStudentPoolSummary() {
  return http.get('/data/student-pool-summary').then(unwrapResponse)
}

export function getArrivalRules() {
  return http.get('/data/arrival-rules').then(unwrapResponse)
}

export function getTagMappingsSummary() {
  return http.get('/data/tag-mappings/summary').then(unwrapResponse)
}

export function getDefaultStrategyParameters() {
  return http.get('/strategies/default-parameters').then(unwrapResponse)
}

export function getLossConfig() {
  return http.get('/strategies/loss-config').then(unwrapResponse)
}

export function previewArrivalCurve(payload) {
  return http.post('/simulations/arrival-curve-preview', payload).then(unwrapResponse)
}

export function runSimulation(payload) {
  return http.post('/simulations/run', payload).then(unwrapResponse)
}

export function getArrivalCurve(runId) {
  return http.get(`/simulations/${runId}/arrival-curve`).then(unwrapResponse)
}

export function getMinuteMetrics(runId) {
  return http.get(`/simulations/${runId}/minute-metrics`).then(unwrapResponse)
}

export function getWindowPressure(runId) {
  return http.get(`/simulations/${runId}/window-pressure`).then(unwrapResponse)
}

export function getAdvancedMetrics(runId) {
  return http.get(`/simulations/${runId}/advanced-metrics`).then(unwrapResponse)
}

export function generateDiversion(payload) {
  return http.post('/recommendations/diversion', payload).then(unwrapResponse)
}

export function compareDiversion(payload) {
  return http.post('/strategies/diversion-comparison', payload).then(unwrapResponse)
}

export function evaluateOptimization(payload) {
  return http.post('/optimizations/evaluate', payload).then(unwrapResponse)
}

export function runOptimization(payload) {
  return http.post('/optimizations/run', payload).then(unwrapResponse)
}

export function getOptimizationJob(jobId) {
  return http.get(`/optimizations/${jobId}`).then(unwrapResponse)
}

export function getOptimizationIterations(jobId) {
  return http.get(`/optimizations/${jobId}/iterations`).then(unwrapResponse)
}

export function getOptimizationBest(jobId) {
  return http.get(`/optimizations/${jobId}/best`).then(unwrapResponse)
}
