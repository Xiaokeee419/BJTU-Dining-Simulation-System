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
    throw new Error(`未找到仿真结果 ${runId}`)
  }
  return delayedClone(run)
}

export async function mockRunSimulationWithDiversion(payload) {
  const baseRun = runs.get(Number(payload.baseRunId))
  if (!baseRun) {
    throw new Error(`未找到基准仿真 ${payload.baseRunId}`)
  }
  throw new Error('Mock 模式暂不支持真实分流对比，请切换到真实后端接口。')
}

export async function mockGenerateRecommendation(payload) {
  const run = runs.get(Number(payload.runId))
  if (!run) {
    throw new Error(`未找到仿真结果 ${payload.runId}`)
  }
  const result = buildRecommendationResult(run, payload.minute, payload.profile, payload.limit)
  recommendations.set(`${result.runId}:${result.minute}`, result)
  return delayedClone(result)
}

export async function mockGetRecommendation(runId, minute) {
  const keyPrefix = `${Number(runId)}:`
  const key =
    minute == null
      ? [...recommendations.keys()].find((item) => item.startsWith(keyPrefix))
      : `${runId}:${minute}`
  const result = recommendations.get(key)
  if (!result) {
    throw new Error(`未找到辅助推荐数据 ${runId}`)
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

export async function mockGetDiversionComparison(payload) {
  const baseRun = runs.get(Number(payload.baseRunId))
  if (!baseRun) {
    throw new Error(`未找到基准仿真 ${payload.baseRunId}`)
  }

  const minute = resolveMockMinute(baseRun, payload.minute)
  return delayedClone({
    baseRunId: baseRun.runId,
    compareRunId: null,
    minute,
    diversionResult: {
      runId: baseRun.runId,
      minute,
      suggestions: [],
      reason: 'Mock 模式暂不支持真实分流对比，请切换到真实后端接口后运行。',
    },
    comparison: null,
    baseMetrics: baseRun.metrics,
    compareMetrics: null,
    status: 'MOCK_UNSUPPORTED',
  })
}

function delayedClone(data) {
  return new Promise((resolve) => {
    window.setTimeout(() => resolve(JSON.parse(JSON.stringify(data))), 220)
  })
}

function resolveMockMinute(run, requestedMinute) {
  if (requestedMinute != null && run?.timePoints?.some((point) => point.minute === requestedMinute)) {
    return requestedMinute
  }
  const peakPoint = [...(run?.timePoints || [])]
    .sort((left, right) => {
      const queueDelta = totalQueueLength(right) - totalQueueLength(left)
      if (queueDelta !== 0) return queueDelta
      const loadDelta = totalCurrentCount(right) - totalCurrentCount(left)
      if (loadDelta !== 0) return loadDelta
      return right.minute - left.minute
    })[0]
  return peakPoint?.minute ?? 0
}

function totalQueueLength(timePoint) {
  return (timePoint?.restaurants || []).reduce(
    (sum, restaurant) =>
      sum +
      (restaurant.windows || []).reduce(
        (windowSum, window) => windowSum + Number(window.queueLength || 0),
        0,
      ),
    0,
  )
}

function totalCurrentCount(timePoint) {
  return (timePoint?.restaurants || []).reduce(
    (sum, restaurant) => sum + Number(restaurant.currentCount || 0),
    0,
  )
}
