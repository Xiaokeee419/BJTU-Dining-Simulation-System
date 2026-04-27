import { restaurants, windows, dishes } from '../mock/parameters'
import { scenarios } from '../mock/scenarios'
import { userProfiles } from '../mock/userProfiles'
import { buildSimulationResult } from '../mock/simulationResult'
import {
  buildRecommendationResult,
  buildStrategyComparison,
} from '../mock/recommendationResult'

let runSequence = 10001
const runs = new Map()
const recommendations = new Map()

export async function mockGetUserProfiles() {
  return delayedClone(userProfiles)
}

export async function mockGetScenarios() {
  return delayedClone(scenarios)
}

export async function mockGetRestaurants() {
  return delayedClone(restaurants)
}

export async function mockGetWindows(restaurantId) {
  const data = restaurantId
    ? windows.filter((window) => window.restaurantId === Number(restaurantId))
    : windows
  return delayedClone(data)
}

export async function mockGetDishes(params = {}) {
  const data = dishes.filter((dish) => {
    const restaurantMatched =
      params.restaurantId == null || dish.restaurantId === Number(params.restaurantId)
    const windowMatched = params.windowId == null || dish.windowId === Number(params.windowId)
    return restaurantMatched && windowMatched
  })
  return delayedClone(data)
}

export async function mockRunSimulation(payload) {
  const run = buildSimulationResult(payload.profile, payload.scenario, runSequence++)
  runs.set(run.runId, run)
  return delayedClone(run)
}

export async function mockGetSimulation(runId) {
  const run = runs.get(Number(runId))
  if (!run) {
    throw new Error(`未找到仿真结果: ${runId}`)
  }
  return delayedClone(run)
}

export async function mockGenerateRecommendation(payload) {
  const run = runs.get(Number(payload.runId))
  if (!run) {
    throw new Error(`未找到仿真结果: ${payload.runId}`)
  }
  const result = buildRecommendationResult(run, payload.minute, payload.profile, payload.limit)
  recommendations.set(`${result.runId}:${result.minute}`, result)
  return delayedClone(result)
}

export async function mockGetRecommendation(runId, minute) {
  const keyPrefix = `${Number(runId)}:`
  const key = minute == null ? [...recommendations.keys()].find((item) => item.startsWith(keyPrefix)) : `${runId}:${minute}`
  const result = recommendations.get(key)
  if (!result) {
    throw new Error(`未找到推荐结果: ${runId}`)
  }
  return delayedClone(result)
}

export async function mockCompareStrategies(payload) {
  const baseRun = runs.get(Number(payload.baseRunId))
  const compareRun = runs.get(Number(payload.compareRunId))
  if (!baseRun || !compareRun) {
    throw new Error('缺少可对比的仿真结果')
  }
  return delayedClone(buildStrategyComparison(baseRun, compareRun))
}

function delayedClone(data) {
  return new Promise((resolve) => {
    window.setTimeout(() => resolve(JSON.parse(JSON.stringify(data))), 220)
  })
}
