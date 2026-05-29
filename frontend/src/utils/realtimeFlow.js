import {
  avgWaitMinutes,
  flattenWindows,
  resolveTimePoint,
  totalCurrentCount,
  totalQueueLength,
} from './simulationStats'

const FLOW_ZONES = [
  { key: 'entrance', label: '入口区', baseRatio: 0.1, color: '#60a5fa' },
  { key: 'ordering', label: '点餐区', baseRatio: 0.24, color: '#fb923c' },
  { key: 'diningA', label: '就餐区 A', baseRatio: 0.28, color: '#86efac' },
  { key: 'diningB', label: '就餐区 B', baseRatio: 0.26, color: '#bef264' },
  { key: 'recycle', label: '回收区', baseRatio: 0.12, color: '#93c5fd' },
]

const TAG_ORDER = [
  { key: '辣', color: '#ef4444' },
  { key: '清淡', color: '#22c55e' },
  { key: '米饭', color: '#3b82f6' },
  { key: '面食', color: '#8b5cf6' },
  { key: '减脂', color: '#06b6d4' },
  { key: '高蛋白', color: '#f97316' },
  { key: '素食', color: '#84cc16' },
  { key: '快餐', color: '#2563eb' },
]

const PERSONA_POOL = {
  STUDENT: ['学生A', '学生B', '学生C', '学生D', '学生E', '学生F', '学生G', '学生H'],
  FACULTY: ['教职工A', '教职工B', '教职工C', '教职工D', '教职工E', '教职工F'],
  VISITOR: ['访客A', '访客B', '访客C', '访客D', '访客E', '访客F'],
}

export function buildRealtimeFlowSnapshot(run, profile, minute, recommendation) {
  const point = resolveTimePoint(run, minute)
  if (!point) {
    return null
  }

  const previous = resolvePreviousPoint(run, point.minute)
  const windows = flattenWindows(point).map((entry) => entry.window)
  const currentPeople = totalCurrentCount(point)
  const previousPeople = previous ? totalCurrentCount(previous) : currentPeople
  const peopleDelta = currentPeople - previousPeople
  const totalQueue = totalQueueLength(point)
  const totalServing = windows.reduce((sum, window) => sum + Number(window.servingCount || 0), 0)
  const avgWait = avgWaitMinutes(point)
  const stepMinutes = resolveStepMinutes(run)
  const zoneCounts = buildZoneCounts(point, Math.max(0, peopleDelta), totalQueue, totalServing)
  const tagBreakdown = buildTagBreakdown(profile, recommendation, currentPeople)
  const diningCount = zoneCounts
    .filter((zone) => zone.key === 'diningA' || zone.key === 'diningB')
    .reduce((sum, zone) => sum + zone.count, 0)

  return {
    currentPeople,
    peopleDelta,
    newEntries: Math.max(0, peopleDelta),
    avgStayMinutes: deriveStayMinutes(diningCount, totalServing, stepMinutes, avgWait),
    queuedWindowCount: windows.filter((window) => Number(window.queueLength || 0) > 0).length,
    zoneCounts,
    tagBreakdown,
    totalTaggedCount: tagBreakdown.reduce((sum, item) => sum + item.count, 0),
    trend: buildTrend(run),
    profiles: buildAudienceProfiles(zoneCounts, tagBreakdown, profile, minute, avgWait),
    lastUpdated: recommendation?.generatedAt || run?.createdAt || null,
  }
}

function resolvePreviousPoint(run, minute) {
  const timePoints = Array.isArray(run?.timePoints) ? run.timePoints : []
  const currentIndex = timePoints.findIndex((point) => point.minute === minute)
  if (currentIndex <= 0) return null
  return timePoints[currentIndex - 1]
}

function resolveStepMinutes(run) {
  const step = Number(run?.scenario?.stepMinutes || 5)
  return Number.isFinite(step) && step > 0 ? step : 5
}

function buildZoneCounts(point, newEntries, totalQueue, totalServing) {
  const currentPeople = totalCurrentCount(point)
  if (!currentPeople) {
    return FLOW_ZONES.map((zone) => ({ ...zone, count: 0, density: 'low' }))
  }

  const restaurants = point.restaurants || []
  const halfway = Math.ceil(restaurants.length / 2)
  const diningByRestaurant = restaurants.map((restaurant) => {
    const queue = (restaurant.windows || []).reduce(
      (sum, window) => sum + Number(window.queueLength || 0),
      0,
    )
    const serving = (restaurant.windows || []).reduce(
      (sum, window) => sum + Number(window.servingCount || 0),
      0,
    )
    return Math.max(0, Number(restaurant.currentCount || 0) - queue - serving)
  })

  const diningA = diningByRestaurant.slice(0, halfway).reduce((sum, value) => sum + value, 0)
  const diningB = diningByRestaurant.slice(halfway).reduce((sum, value) => sum + value, 0)
  const entrance = Math.max(0, newEntries + Math.round(totalQueue * 0.12))
  const ordering = Math.max(0, totalQueue + totalServing)
  const recycle = Math.max(0, Math.round(Math.min(totalServing * 0.35, currentPeople * 0.1)))

  const normalized = normalizeCounts([entrance, ordering, diningA, diningB, recycle], currentPeople)
  return FLOW_ZONES.map((zone, index) => ({
    ...zone,
    count: normalized[index],
    density: densityLevel(normalized[index], currentPeople),
  }))
}

function buildTagBreakdown(profile, recommendation, currentPeople) {
  const userType = profile?.userType || 'STUDENT'
  const weights = new Map(TAG_ORDER.map((item) => [item.key, 0.4]))

  ;(profile?.tasteTags || []).forEach((tag) => {
    const normalized = normalizeTag(tag)
    weights.set(normalized, (weights.get(normalized) || 0) + 1.8)
  })

  collectRecommendationTags(recommendation).forEach(({ tag, weight }) => {
    weights.set(tag, (weights.get(tag) || 0) + weight)
  })

  if (userType === 'STUDENT') {
    weights.set('快餐', (weights.get('快餐') || 0) + 0.6)
    weights.set('高蛋白', (weights.get('高蛋白') || 0) + 0.3)
  } else if (userType === 'FACULTY') {
    weights.set('清淡', (weights.get('清淡') || 0) + 0.6)
    weights.set('减脂', (weights.get('减脂') || 0) + 0.4)
  }

  const totalWeight = Array.from(weights.values()).reduce((sum, value) => sum + value, 0) || 1
  const counts = allocateCounts(
    TAG_ORDER.map((item) => weights.get(item.key) || 0),
    Math.max(0, currentPeople),
  )

  return TAG_ORDER.map((item, index) => ({
    ...item,
    ratio: round((weights.get(item.key) || 0) / totalWeight, 3),
    count: counts[index],
  }))
}

function collectRecommendationTags(recommendation) {
  const items = [
    ...(recommendation?.restaurants || []),
    ...(recommendation?.windows || []),
    ...(recommendation?.dishes || []),
  ]

  const collected = []
  items.forEach((item) => {
    const itemWeight = Math.max(0.35, Number(item.score || 0) / 100) * Math.max(1, 4 - Number(item.rank || 1))
    ;(item.matchedTags || []).forEach((tag) => {
      const normalized = normalizeTag(tag)
      collected.push({ tag: normalized, weight: itemWeight })
    })
  })
  return collected
}

function buildTrend(run) {
  const timePoints = Array.isArray(run?.timePoints) ? run.timePoints : []
  return timePoints.map((point) => ({
    label: `${point.minute} 分钟`,
    minute: point.minute,
    count: totalCurrentCount(point),
  }))
}

function buildAudienceProfiles(zoneCounts, tagBreakdown, profile, minute, avgWait) {
  const names = PERSONA_POOL[profile?.userType || 'STUDENT'] || PERSONA_POOL.STUDENT
  const zones = zoneCounts
    .filter((zone) => zone.count > 0)
    .sort((left, right) => right.count - left.count)
  const tags = tagBreakdown
    .filter((tag) => tag.count > 0)
    .sort((left, right) => right.count - left.count)

  if (!zones.length || !tags.length) {
    return []
  }

  return Array.from({ length: Math.min(6, names.length) }, (_, index) => {
    const zone = zones[index % zones.length]
    const primaryTag = tags[index % tags.length]
    const secondaryTag = tags[(index + 1) % tags.length]
    return {
      id: `${profile?.userType || 'USER'}-${index + 1}`,
      name: names[index],
      tags: [primaryTag.key, secondaryTag.key].filter(Boolean),
      area: zone.label,
      stayTime: round(Math.max(3, avgWait + 4 + (index % 3) * 1.5 + minute / 60), 1),
    }
  })
}

function deriveStayMinutes(diningCount, totalServing, stepMinutes, avgWait) {
  if (diningCount <= 0) {
    return round(Math.max(4, avgWait + stepMinutes * 0.6), 1)
  }

  const throughput = Math.max(1, totalServing)
  const stayFromTurnover = (diningCount / throughput) * stepMinutes
  return round(clamp(stayFromTurnover + avgWait * 0.35, 4, 45), 1)
}

function allocateCounts(rawWeights, targetTotal) {
  if (!targetTotal) {
    return rawWeights.map(() => 0)
  }

  const safeWeights =
    rawWeights.reduce((sum, value) => sum + value, 0) > 0
      ? rawWeights
      : FLOW_ZONES.map((zone) => zone.baseRatio)
  const total = safeWeights.reduce((sum, value) => sum + value, 0) || 1
  const scaled = safeWeights.map((value) => Math.floor((value / total) * targetTotal))
  let diff = targetTotal - scaled.reduce((sum, value) => sum + value, 0)
  const order = safeWeights
    .map((value, index) => ({ value, index }))
    .sort((left, right) => right.value - left.value)

  let cursor = 0
  while (diff > 0 && order.length) {
    scaled[order[cursor % order.length].index] += 1
    diff -= 1
    cursor += 1
  }

  return scaled
}

function normalizeCounts(rawCounts, targetTotal) {
  const total = rawCounts.reduce((sum, value) => sum + Math.max(0, value), 0)
  if (total <= 0) {
    return allocateCounts(
      FLOW_ZONES.map((zone) => zone.baseRatio),
      targetTotal,
    )
  }
  return allocateCounts(rawCounts, targetTotal)
}

function densityLevel(count, total) {
  const ratio = total > 0 ? count / total : 0
  if (ratio >= 0.3) return 'high'
  if (ratio >= 0.16) return 'medium'
  return 'low'
}

function normalizeTag(tag) {
  const mapping = {
    偏辣: '辣',
    辣: '辣',
    清淡: '清淡',
    米饭: '米饭',
    面食: '面食',
    轻食: '减脂',
    清真: '高蛋白',
    套餐: '快餐',
  }
  return mapping[tag] || tag
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function round(value, digits) {
  const base = 10 ** digits
  return Math.round(Number(value || 0) * base) / base
}
