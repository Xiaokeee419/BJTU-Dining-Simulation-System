import { http, unwrapResponse } from './http'

export function getSimulationOptions(config = {}) {
  return Promise.all([
    http.get('/presets/user-profiles', config).then(unwrapResponse),
    http.get('/presets/scenarios', config).then(unwrapResponse),
    http.get('/parameters/restaurants', config).then(unwrapResponse),
    http.get('/parameters/windows', config).then(unwrapResponse),
  ]).then(([profiles, scenarios, restaurants, windows]) => ({
    profiles,
    scenarios,
    restaurants,
    windows,
  }))
}

export function getDataOverview(config = {}) {
  return http.get('/data/overview', config).then(unwrapResponse)
}

export function getCsvFlowCurves(params, config = {}) {
  return http.get('/data/flow-curves', { ...config, params }).then(unwrapResponse)
}

export function runDashboardSimulation(payload, config = {}) {
  return http.post('/simulations/run', payload, config).then(unwrapResponse)
}

export function getDashboardSimulation(runId, config = {}) {
  return http.get(`/simulations/${runId}`, config).then(unwrapResponse)
}

export function getDiversionSuggestions(payload, config = {}) {
  return http.post('/recommendations/diversion', payload, config).then(unwrapResponse)
}

export function runDashboardComparison(payload, config = {}) {
  return http.post('/strategies/diversion-comparison', payload, config).then(unwrapResponse)
}

export function startOptimization(payload, config = {}) {
  return http.post('/optimizations/run', payload, config).then(unwrapResponse)
}

export function getOptimizationJob(jobId, config = {}) {
  return http.get(`/optimizations/${jobId}`, config).then(unwrapResponse)
}

export function getOptimizationIterations(jobId, config = {}) {
  return http.get(`/optimizations/${jobId}/iterations`, config).then(unwrapResponse)
}

export async function getOptimizationBest(jobId, config = {}) {
  try {
    return await http.get(`/optimizations/${jobId}/best`, config).then(unwrapResponse)
  } catch (error) {
    if (error?.status === 404) {
      return null
    }
    throw error
  }
}
