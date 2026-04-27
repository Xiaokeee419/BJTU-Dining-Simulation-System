import { restaurants, windows } from './parameters'

const crowdWeights = {
  IDLE: 0.48,
  NORMAL: 0.7,
  BUSY: 0.96,
  EXTREME: 1.2,
}

const periodWeights = {
  BREAKFAST: 0.82,
  LUNCH: 1,
  DINNER: 0.92,
}

export function buildSimulationResult(profile, scenario, runId) {
  const duration = Number(scenario.durationMinutes || 60)
  const step = Number(scenario.stepMinutes || 5)
  const countFactor = Number(scenario.virtualUserCount || 300) / 300
  const scenarioFactor =
    (crowdWeights[scenario.crowdLevel] || 0.85) *
    (periodWeights[scenario.mealPeriod] || 1) *
    Number(scenario.weatherFactor || 1) *
    Number(scenario.eventFactor || 1) *
    countFactor

  const minutes = Array.from({ length: Math.floor(duration / step) + 1 }, (_, index) => index * step)
  const timePoints = minutes.map((minute, index) =>
    buildTimePoint(minute, index, minutes.length, scenario, scenarioFactor),
  )

  return {
    runId,
    status: 'FINISHED',
    profile: { ...profile },
    scenario: { ...scenario },
    timePoints,
    metrics: buildMetrics(timePoints, scenario.virtualUserCount),
    createdAt: new Date().toISOString(),
  }
}

function buildTimePoint(minute, index, totalPoints, scenario, scenarioFactor) {
  const progress = totalPoints <= 1 ? 0 : index / (totalPoints - 1)
  const arrivalWave = 0.52 + 0.52 * Math.sin(progress * Math.PI)
  const closedWindowIds = new Set(scenario.closedWindowIds || [])

  const restaurantRows = restaurants.map((restaurant, restaurantIndex) => {
    const restaurantWave = 0.86 + 0.1 * Math.sin(index * 0.85 + restaurantIndex)
    const currentCount = Math.max(
      8,
      Math.round(
        restaurant.capacity *
          restaurant.baseAttraction *
          scenarioFactor *
          arrivalWave *
          restaurantWave *
          0.55,
      ),
    )
    const cappedCount = Math.min(Math.round(restaurant.capacity * 1.08), currentCount)
    const relatedWindows = windows.filter((window) => window.restaurantId === restaurant.restaurantId)
    const restaurantWindows = buildWindowStates(
      relatedWindows,
      cappedCount,
      closedWindowIds,
      index,
    )

    return {
      restaurantId: restaurant.restaurantId,
      name: restaurant.name,
      currentCount: cappedCount,
      capacity: restaurant.capacity,
      crowdLevel: resolveRestaurantCrowd(cappedCount, restaurant.capacity),
      windows: restaurantWindows,
    }
  })

  return {
    minute,
    restaurants: restaurantRows,
  }
}

function buildWindowStates(relatedWindows, restaurantCount, closedWindowIds, index) {
  const openWindows = relatedWindows.filter((window) => !closedWindowIds.has(window.windowId))
  const totalRate = openWindows.reduce((sum, window) => sum + window.serviceRatePerMinute, 0) || 1

  return relatedWindows.map((window, windowIndex) => {
    if (closedWindowIds.has(window.windowId)) {
      return {
        windowId: window.windowId,
        name: window.name,
        queueLength: 0,
        servingCount: 0,
        waitMinutes: 0,
        crowdLevel: 'IDLE',
        status: 'CLOSED',
      }
    }

    const rateShare = window.serviceRatePerMinute / totalRate
    const wave = 0.9 + 0.18 * Math.sin(index * 0.72 + windowIndex * 1.2)
    const demand = restaurantCount * rateShare * wave
    const servingCount = Math.max(1, Math.round(window.serviceRatePerMinute * 2))
    const queueLength = Math.max(0, Math.round(demand - window.serviceRatePerMinute * 10))
    const waitMinutes = Math.max(1, Math.ceil(queueLength / window.serviceRatePerMinute))

    return {
      windowId: window.windowId,
      name: window.name,
      queueLength,
      servingCount,
      waitMinutes,
      crowdLevel: resolveWindowCrowd(waitMinutes),
      status: window.status,
    }
  })
}

function resolveRestaurantCrowd(currentCount, capacity) {
  const ratio = capacity > 0 ? currentCount / capacity : 0
  if (ratio < 0.4) return 'IDLE'
  if (ratio < 0.7) return 'NORMAL'
  if (ratio < 0.9) return 'BUSY'
  return 'EXTREME'
}

function resolveWindowCrowd(waitMinutes) {
  if (waitMinutes < 5) return 'IDLE'
  if (waitMinutes < 10) return 'NORMAL'
  if (waitMinutes < 20) return 'BUSY'
  return 'EXTREME'
}

function buildMetrics(timePoints, virtualUserCount) {
  const windowsAtAllTimes = timePoints.flatMap((point) =>
    point.restaurants.flatMap((restaurant) => restaurant.windows),
  )
  const waits = windowsAtAllTimes.map((window) => window.waitMinutes)
  const queues = windowsAtAllTimes.map((window) => window.queueLength)
  const busyWindowCount = windowsAtAllTimes.filter((window) =>
    ['BUSY', 'EXTREME'].includes(window.crowdLevel),
  ).length
  const extremeWindowCount = windowsAtAllTimes.filter(
    (window) => window.crowdLevel === 'EXTREME',
  ).length
  const totalVirtualUsers = Number(virtualUserCount || 0)
  const servedUserCount = Math.max(
    0,
    Math.round(totalVirtualUsers - queues.reduce((sum, value) => sum + value, 0) / timePoints.length),
  )

  return {
    avgWaitMinutes: round(waits.reduce((sum, value) => sum + value, 0) / waits.length, 1),
    maxWaitMinutes: Math.max(...waits),
    maxQueueLength: Math.max(...queues),
    busyWindowCount,
    extremeWindowCount,
    totalVirtualUsers,
    servedUserCount,
    unservedUserCount: Math.max(0, totalVirtualUsers - servedUserCount),
  }
}

function round(value, digits) {
  const base = 10 ** digits
  return Math.round(value * base) / base
}
