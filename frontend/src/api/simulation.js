import { http, unwrapResponse } from './http'
import {
  mockCompareStrategies,
  mockGetDishes,
  mockGetRestaurants,
  mockGetScenarios,
  mockGetSimulation,
  mockGetUserProfiles,
  mockGetWindows,
  mockRunSimulation,
} from './mock'

const useMock = import.meta.env.VITE_USE_MOCK !== 'false'

export async function getUserProfiles() {
  if (useMock) return mockGetUserProfiles()
  return http.get('/presets/user-profiles').then(unwrapResponse)
}

export async function getScenarioPresets() {
  if (useMock) return mockGetScenarios()
  return http.get('/presets/scenarios').then(unwrapResponse)
}

export async function getRestaurants() {
  if (useMock) return mockGetRestaurants()
  return http.get('/parameters/restaurants').then(unwrapResponse)
}

export async function getWindows(params = {}) {
  if (useMock) return mockGetWindows(params.restaurantId)
  return http.get('/parameters/windows', { params }).then(unwrapResponse)
}

export async function getDishes(params = {}) {
  if (useMock) return mockGetDishes(params)
  return http.get('/parameters/dishes', { params }).then(unwrapResponse)
}

export async function runSimulation(payload) {
  if (useMock) return mockRunSimulation(payload)
  return http.post('/simulations/run', payload).then(unwrapResponse)
}

export async function getSimulation(runId) {
  if (useMock) return mockGetSimulation(runId)
  return http.get(`/simulations/${runId}`).then(unwrapResponse)
}

export async function compareStrategies(payload) {
  if (useMock) return mockCompareStrategies(payload)
  return http.post('/strategies/compare', payload).then(unwrapResponse)
}
