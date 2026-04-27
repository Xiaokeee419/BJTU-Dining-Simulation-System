import { dishes, restaurants, windows } from './parameters'

const crowdPenalty = {
  IDLE: 0,
  NORMAL: 6,
  BUSY: 16,
  EXTREME: 28,
}

export function buildRecommendationResult(runResult, minute, profile, limit = 3) {
  const selectedTimePoint = resolveTimePoint(runResult, minute)
  const restaurantItems = buildRestaurantItems(selectedTimePoint, limit)
  const windowItems = buildWindowItems(selectedTimePoint, profile, limit)
  const dishItems = buildDishItems(selectedTimePoint, profile, limit)

  return {
    runId: runResult.runId,
    minute: selectedTimePoint.minute,
    restaurants: restaurantItems,
    windows: windowItems,
    dishes: dishItems,
    diversionSuggestion: buildDiversionSuggestion(selectedTimePoint, restaurantItems),
    generatedAt: new Date().toISOString(),
  }
}

export function buildStrategyComparison(baseRun, compareRun) {
  const avgWaitDelta = round(compareRun.metrics.avgWaitMinutes - baseRun.metrics.avgWaitMinutes, 1)
  const maxQueueDelta = compareRun.metrics.maxQueueLength - baseRun.metrics.maxQueueLength
  const busyWindowCountDelta =
    compareRun.metrics.busyWindowCount - baseRun.metrics.busyWindowCount

  return {
    baseRunId: baseRun.runId,
    compareRunId: compareRun.runId,
    avgWaitDelta,
    maxQueueDelta,
    busyWindowCountDelta,
    conclusion: buildConclusion(avgWaitDelta, maxQueueDelta, busyWindowCountDelta),
  }
}

function buildRestaurantItems(timePoint, limit) {
  return timePoint.restaurants
    .map((restaurant) => {
      const waits = restaurant.windows.map((window) => window.waitMinutes)
      const estimatedWaitMinutes = Math.min(...waits)
      const score = clamp(
        100 -
          estimatedWaitMinutes * 2.8 -
          (crowdPenalty[restaurant.crowdLevel] || 0) +
          (restaurant.capacity - restaurant.currentCount) / 24,
        0,
        100,
      )

      return {
        targetType: 'RESTAURANT',
        targetId: restaurant.restaurantId,
        name: restaurant.name,
        score: round(score, 1),
        reason: `${restaurant.name}当前等待约 ${estimatedWaitMinutes} 分钟，拥挤度为 ${labelCrowd(restaurant.crowdLevel)}`,
        relatedRestaurantId: restaurant.restaurantId,
        relatedWindowId: null,
        estimatedWaitMinutes,
        crowdLevel: restaurant.crowdLevel,
      }
    })
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)
    .map(withRank)
}

function buildWindowItems(timePoint, profile, limit) {
  return flattenWindows(timePoint)
    .map((row) => {
      const matchScore = profile.tasteTags.some((tag) => row.name.includes(tag)) ? 12 : 0
      const score = clamp(
        96 - row.waitMinutes * 3.2 - (crowdPenalty[row.crowdLevel] || 0) + matchScore,
        0,
        100,
      )

      return {
        targetType: 'WINDOW',
        targetId: row.windowId,
        name: row.name,
        score: round(score, 1),
        reason: `${row.restaurantName}${row.name}预计等待 ${row.waitMinutes} 分钟，当前${labelCrowd(row.crowdLevel)}`,
        relatedRestaurantId: row.restaurantId,
        relatedWindowId: row.windowId,
        estimatedWaitMinutes: row.waitMinutes,
        crowdLevel: row.crowdLevel,
      }
    })
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)
    .map(withRank)
}

function buildDishItems(timePoint, profile, limit) {
  const windowRows = flattenWindows(timePoint)
  return dishes
    .map((dish) => {
      const windowState = windowRows.find((row) => row.windowId === dish.windowId)
      const tagMatchScore = dish.tags.filter((tag) => profile.tasteTags.includes(tag)).length * 14
      const budgetScore = dish.price >= profile.budgetMin && dish.price <= profile.budgetMax ? 12 : -10
      const waitPenalty = (windowState?.waitMinutes || 12) * 2.2
      const score = clamp(
        62 + tagMatchScore + budgetScore + dish.popularity * 18 - waitPenalty,
        0,
        100,
      )

      return {
        targetType: 'DISH',
        targetId: dish.dishId,
        name: dish.name,
        score: round(score, 1),
        reason: buildDishReason(dish, windowState, profile),
        relatedRestaurantId: dish.restaurantId,
        relatedWindowId: dish.windowId,
        estimatedWaitMinutes: windowState?.waitMinutes || 0,
        crowdLevel: windowState?.crowdLevel || 'IDLE',
      }
    })
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)
    .map(withRank)
}

function resolveTimePoint(runResult, minute) {
  if (!runResult?.timePoints?.length) {
    throw new Error('缺少仿真时间序列')
  }
  if (minute == null) {
    return runResult.timePoints[runResult.timePoints.length - 1]
  }
  return (
    runResult.timePoints.find((point) => point.minute === minute) ||
    runResult.timePoints[runResult.timePoints.length - 1]
  )
}

function flattenWindows(timePoint) {
  return timePoint.restaurants.flatMap((restaurant) =>
    restaurant.windows.map((window) => ({
      ...window,
      restaurantId: restaurant.restaurantId,
      restaurantName: restaurant.name,
    })),
  )
}

function buildDishReason(dish, windowState, profile) {
  const tags = dish.tags.filter((tag) => profile.tasteTags.includes(tag))
  const tagText = tags.length ? `匹配${tags.join('、')}偏好` : '基础热度较高'
  const budgetText =
    dish.price >= profile.budgetMin && dish.price <= profile.budgetMax
      ? '价格在预算内'
      : '价格接近预算'
  const waitText = windowState ? `窗口等待 ${windowState.waitMinutes} 分钟` : '窗口等待较短'
  return `${tagText}，${budgetText}，${waitText}`
}

function buildDiversionSuggestion(timePoint, restaurantItems) {
  const mostCrowded = [...timePoint.restaurants].sort(
    (a, b) => b.currentCount / b.capacity - a.currentCount / a.capacity,
  )[0]
  const target = restaurantItems[0]
  if (!mostCrowded || !target) {
    return '当前场景暂无明显分流建议'
  }
  if (mostCrowded.restaurantId === target.targetId) {
    return `${target.name}当前可作为主要就餐选择，建议持续观察窗口队列变化`
  }
  return `建议将${mostCrowded.name}部分人流引导至${target.name}，可降低高拥挤窗口压力`
}

function buildConclusion(avgWaitDelta, maxQueueDelta, busyWindowCountDelta) {
  const waitText =
    avgWaitDelta <= 0
      ? `平均等待降低 ${Math.abs(avgWaitDelta)} 分钟`
      : `平均等待增加 ${avgWaitDelta} 分钟`
  const queueText =
    maxQueueDelta <= 0
      ? `最大排队减少 ${Math.abs(maxQueueDelta)} 人`
      : `最大排队增加 ${maxQueueDelta} 人`
  const busyText =
    busyWindowCountDelta <= 0
      ? `高拥挤窗口减少 ${Math.abs(busyWindowCountDelta)} 个`
      : `高拥挤窗口增加 ${busyWindowCountDelta} 个`
  return `${waitText}，${queueText}，${busyText}`
}

function withRank(item, index) {
  return {
    ...item,
    rank: index + 1,
  }
}

function labelCrowd(crowdLevel) {
  return (
    {
      IDLE: '空闲',
      NORMAL: '正常',
      BUSY: '繁忙',
      EXTREME: '极拥挤',
    }[crowdLevel] || crowdLevel
  )
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function round(value, digits) {
  const base = 10 ** digits
  return Math.round(value * base) / base
}
