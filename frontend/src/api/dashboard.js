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

export function getOptimizationJob(taskId, config = {}) {
  return http.get(`/optimizations/${taskId}`, config).then(unwrapResponse)
}

export function getOptimizationIterations(taskId, config = {}) {
  return http.get(`/optimizations/${taskId}/iterations`, config).then(unwrapResponse)
}

export async function getOptimizationBest(taskId, config = {}) {
  try {
    return await http.get(`/optimizations/${taskId}/best`, config).then(unwrapResponse)
  } catch (error) {
    if (error?.status === 404) {
      return null
    }
    throw error
  }
}
