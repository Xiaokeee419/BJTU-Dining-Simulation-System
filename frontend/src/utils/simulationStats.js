const CROWD_WEIGHT = {
  IDLE: 0.22,
  NORMAL: 0.48,
  BUSY: 0.74,
  EXTREME: 1,
}

export function buildStatisticsSnapshot(run) {
  const timePoints = Array.isArray(run?.timePoints) ? run.timePoints : []
  if (!timePoints.length) {
    return null
  }

  const metrics = run?.metrics || {}
  const labels = timePoints.map((point) => `${point.minute ?? 0} 分`)
  const totalPeopleSeries = timePoints.map(totalCurrentCount)
  const queueSeries = timePoints.map(totalQueueLength)
  const avgWaitSeries = timePoints.map(avgWaitMinutes)
  const seatUtilizationSeries = timePoints.map(seatUtilizationRate)
  const windowEfficiencyRows = buildWindowEfficiencyRows(timePoints)
  const congestionSeries = timePoints.map(congestionIndex)
  const periodComparison = buildPeriodComparison(timePoints)

  const peakPoint = resolvePeakTimePoint(run, totalQueueLength)

  return {
    cards: [
      metricItem(
        'avgWait',
        '平均排队时长',
        pickNumber(metrics.avgWaitMinutes, average(avgWaitSeries)),
        '分钟',
        'primary',
        `峰值 ${round(Math.max(...avgWaitSeries), 1)} 分钟`,
      ),
      metricItem(
        'seatUtilization',
        '容量负载率',
        average(seatUtilizationSeries),
        '%',
        'teal',
        `峰值 ${round(Math.max(...seatUtilizationSeries), 1)}%`,
      ),
      metricItem(
        'windowEfficiency',
        '窗口服务负载比',
        average(windowEfficiencyRows.map((item) => item.efficiency)),
        '%',
        'cyan',
        '按 serving / (serving + queue) 近似计算',
      ),
      metricItem(
        'totalDiners',
        '仿真样本人数',
        pickNumber(metrics.totalVirtualUsers, totalPeopleSeries[totalPeopleSeries.length - 1]),
        '人',
        'blue',
        `已服务 ${pickNumber(metrics.servedUserCount, 0)} 人`,
      ),
      metricItem(
        'congestion',
        '拥挤指数',
        average(congestionSeries),
        '/100',
        'orange',
        congestionLabel(average(congestionSeries)),
      ),
    ],
    series: {
      labels,
      totalPeopleSeries,
      queueSeries,
      avgWaitSeries,
      seatUtilizationSeries,
      congestionSeries,
      windowEfficiencyRows,
      periodComparison,
    },
    summary: {
      createdAt: run?.createdAt || null,
      peakMinutes: inferPeakWindow(timePoints),
      maxQueueLength: round(Math.max(...queueSeries), 0),
      seatTotal: totalCapacity(timePoints[0]),
      windowTotal: flattenWindows(timePoints[0]).length,
      durationMinutes: run?.scenario?.durationMinutes || timePoints.at(-1)?.minute || 0,
      stepMinutes: run?.scenario?.stepMinutes || estimateStep(timePoints),
      peakCrowd: `${peakPoint?.minute ?? 0} 分钟`,
    },
  }
}

export function resolveTimePoint(run, minute) {
  const timePoints = Array.isArray(run?.timePoints) ? run.timePoints : []
  if (!timePoints.length) {
    return null
  }
  if (minute == null) {
    return resolvePeakTimePoint(run)
  }
  return timePoints.find((point) => point.minute === minute) || resolvePeakTimePoint(run)
}

export function resolvePeakTimePoint(run, selector = totalQueueLength) {
  const timePoints = Array.isArray(run?.timePoints) ? run.timePoints : []
  if (!timePoints.length) {
    return null
  }

  return timePoints.reduce((peak, point) => {
    const pointScore = Number(selector(point) || 0)
    const peakScore = Number(selector(peak) || 0)
    if (pointScore !== peakScore) {
      return pointScore > peakScore ? point : peak
    }
    return totalCurrentCount(point) > totalCurrentCount(peak) ? point : peak
  })
}

export function totalCurrentCount(point) {
  return (point?.restaurants || []).reduce(
    (sum, restaurant) => sum + Number(restaurant.currentCount || 0),
    0,
  )
}

export function totalQueueLength(point) {
  return flattenWindows(point).reduce((sum, entry) => sum + Number(entry.window.queueLength || 0), 0)
}

export function avgWaitMinutes(point) {
  const windows = flattenWindows(point)
    .map((entry) => entry.window)
    .filter((window) => window.status !== 'CLOSED')
  if (!windows.length) {
    return 0
  }
  return round(
    windows.reduce((sum, window) => sum + Number(window.waitMinutes || 0), 0) / windows.length,
    1,
  )
}

export function seatUtilizationRate(point) {
  const restaurants = point?.restaurants || []
  const current = restaurants.reduce((sum, restaurant) => sum + Number(restaurant.currentCount || 0), 0)
  const capacity = restaurants.reduce((sum, restaurant) => sum + Number(restaurant.capacity || 0), 0)
  return capacity > 0 ? round((current / capacity) * 100, 1) : 0
}

export function flattenWindows(point) {
  return (point?.restaurants || []).flatMap((restaurant) =>
    (restaurant.windows || []).map((window) => ({
      restaurantId: restaurant.restaurantId,
      restaurantName: restaurant.name || `餐厅 ${restaurant.restaurantId}`,
      window,
    })),
  )
}

function buildWindowEfficiencyRows(timePoints) {
  const rows = new Map()

  timePoints.forEach((point) => {
    flattenWindows(point).forEach(({ restaurantName, window }) => {
      const key = String(window.windowId ?? `${restaurantName}-${window.name}`)
      if (!rows.has(key)) {
        rows.set(key, {
          key,
          name: window.name || '未命名窗口',
          restaurantName,
          queueTotal: 0,
          servingTotal: 0,
          sampleCount: 0,
        })
      }

      const row = rows.get(key)
      row.queueTotal += Number(window.queueLength || 0)
      row.servingTotal += Number(window.servingCount || 0)
      row.sampleCount += 1
    })
  })

  return Array.from(rows.values())
    .map((row) => {
      const averageQueue = row.sampleCount ? row.queueTotal / row.sampleCount : 0
      const averageServing = row.sampleCount ? row.servingTotal / row.sampleCount : 0
      const efficiency =
        averageQueue + averageServing > 0
          ? (averageServing / (averageQueue + averageServing)) * 100
          : 0

      return {
        ...row,
        averageQueue: round(averageQueue, 1),
        averageServing: round(averageServing, 1),
        efficiency: round(efficiency, 1),
      }
    })
    .sort((left, right) => right.efficiency - left.efficiency)
}

function buildPeriodComparison(timePoints) {
  const bucketSize = Math.max(2, Math.round(timePoints.length / 4))
  const buckets = []

  for (let index = 0; index < timePoints.length; index += bucketSize) {
    const slice = timePoints.slice(index, index + bucketSize)
    if (!slice.length) continue
    buckets.push({
      label: `${slice[0].minute}-${slice.at(-1).minute} 分`,
      avgWait: round(average(slice.map(avgWaitMinutes)), 1),
      seatUtilization: round(average(slice.map(seatUtilizationRate)), 1),
      congestion: round(average(slice.map(congestionIndex)), 1),
    })
  }

  return buckets
}

function congestionIndex(point) {
  const windows = flattenWindows(point).map((entry) => entry.window)
  if (!windows.length) {
    return 0
  }

  const avgQueue = totalQueueLength(point) / Math.max(1, windows.length)
  const avgWait = avgWaitMinutes(point)
  const crowdScore =
    windows.reduce((sum, window) => sum + (CROWD_WEIGHT[window.crowdLevel] || 0.4), 0) /
    windows.length
  const seatUtilization = seatUtilizationRate(point) / 100

  return round(clamp(avgQueue * 2.4 + avgWait * 4.2 + crowdScore * 26 + seatUtilization * 20, 0, 100), 1)
}

function metricItem(key, label, value, unit, tone, note) {
  return {
    key,
    label,
    value: round(value, unit === '人' ? 0 : 1),
    unit,
    tone,
    note,
  }
}

function totalCapacity(point) {
  return (point?.restaurants || []).reduce(
    (sum, restaurant) => sum + Number(restaurant.capacity || 0),
    0,
  )
}

function inferPeakWindow(timePoints) {
  if (!timePoints.length) return '暂无数据'
  const peakIndex = timePoints.reduce(
    (best, point, index) =>
      totalCurrentCount(point) > totalCurrentCount(timePoints[best]) ? index : best,
    0,
  )
  const peak = timePoints[peakIndex]
  const next = timePoints[Math.min(timePoints.length - 1, peakIndex + 1)]
  return `${peak.minute} - ${next.minute} 分钟`
}

function estimateStep(timePoints) {
  if (timePoints.length < 2) return 5
  return Math.max(1, Number(timePoints[1].minute || 0) - Number(timePoints[0].minute || 0))
}

function average(values) {
  const list = values.filter((value) => Number.isFinite(value))
  if (!list.length) return 0
  return list.reduce((sum, value) => sum + value, 0) / list.length
}

function pickNumber(...values) {
  return values.find((value) => Number.isFinite(value)) ?? 0
}

function round(value, digits) {
  const base = 10 ** digits
  return Math.round(Number(value || 0) * base) / base
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function congestionLabel(value) {
  if (value >= 72) return '高峰拥挤明显'
  if (value >= 48) return '存在局部拥堵'
  return '整体运行平稳'
}
