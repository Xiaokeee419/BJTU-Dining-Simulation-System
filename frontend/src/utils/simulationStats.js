export function resolvePeakTimePoint(run) {
  const timePoints = Array.isArray(run?.timePoints) ? run.timePoints : []
  if (!timePoints.length) {
    return null
  }

  return timePoints.reduce((peak, point) => {
    const pointQueue = totalQueueLength(point)
    const peakQueue = totalQueueLength(peak)
    if (pointQueue !== peakQueue) {
      return pointQueue > peakQueue ? point : peak
    }

    const pointLoad = totalCurrentCount(point)
    const peakLoad = totalCurrentCount(peak)
    if (pointLoad !== peakLoad) {
      return pointLoad > peakLoad ? point : peak
    }

    return Number(point.minute || 0) > Number(peak.minute || 0) ? point : peak
  })
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

export function interpolateTimePoint(run, minute) {
  const timePoints = [...(Array.isArray(run?.timePoints) ? run.timePoints : [])].sort(
    (left, right) => Number(left.minute || 0) - Number(right.minute || 0),
  )
  if (!timePoints.length) {
    return null
  }

  const targetMinute = Number(minute || 0)
  const firstPoint = timePoints[0]
  const lastPoint = timePoints[timePoints.length - 1]
  if (targetMinute <= Number(firstPoint.minute || 0)) {
    return clonePoint(firstPoint, targetMinute)
  }
  if (targetMinute >= Number(lastPoint.minute || 0)) {
    return clonePoint(lastPoint, targetMinute)
  }

  const upperIndex = timePoints.findIndex((point) => Number(point.minute || 0) >= targetMinute)
  const lowerPoint = timePoints[Math.max(0, upperIndex - 1)]
  const upperPoint = timePoints[upperIndex]
  if (Number(upperPoint.minute) === targetMinute) {
    return clonePoint(upperPoint, targetMinute)
  }

  const span = Number(upperPoint.minute) - Number(lowerPoint.minute)
  const ratio = span > 0 ? (targetMinute - Number(lowerPoint.minute)) / span : 0

  return {
    minute: targetMinute,
    restaurants: interpolateRestaurants(
      lowerPoint.restaurants || [],
      upperPoint.restaurants || [],
      ratio,
    ),
  }
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

export function totalCurrentCount(point) {
  return (point?.restaurants || []).reduce(
    (sum, restaurant) => sum + Number(restaurant.currentCount || 0),
    0,
  )
}

export function totalQueueLength(point) {
  return flattenWindows(point).reduce((sum, entry) => sum + Number(entry.window.queueLength || 0), 0)
}

export function totalServingCount(point) {
  return flattenWindows(point).reduce((sum, entry) => sum + Number(entry.window.servingCount || 0), 0)
}

export function maxWindowQueueLength(point) {
  return flattenWindows(point).reduce(
    (max, entry) => Math.max(max, Number(entry.window.queueLength || 0)),
    0,
  )
}

export function topWindowQueueSum(point, limit = 5) {
  return flattenWindows(point)
    .map((entry) => Number(entry.window.queueLength || 0))
    .sort((left, right) => right - left)
    .slice(0, limit)
    .reduce((sum, value) => sum + value, 0)
}

export function busyWindowCount(point) {
  return flattenWindows(point).filter(({ window }) => window.crowdLevel === 'BUSY').length
}

export function extremeWindowCount(point) {
  return flattenWindows(point).filter(({ window }) => window.crowdLevel === 'EXTREME').length
}

export function totalCapacity(point) {
  return (point?.restaurants || []).reduce(
    (sum, restaurant) => sum + Number(restaurant.capacity || 0),
    0,
  )
}

export function openWindowCount(point) {
  return flattenWindows(point).filter(({ window }) => window.status !== 'CLOSED').length
}

export function avgWaitMinutes(point) {
  const openWindows = flattenWindows(point)
    .map((entry) => entry.window)
    .filter((window) => window.status !== 'CLOSED')
  if (!openWindows.length) {
    return 0
  }
  return round(
    openWindows.reduce((sum, window) => sum + Number(window.waitMinutes || 0), 0) / openWindows.length,
    1,
  )
}

export function capacityLoadRate(point) {
  const capacity = totalCapacity(point)
  if (capacity <= 0) {
    return 0
  }
  return round((totalCurrentCount(point) / capacity) * 100, 1)
}

export function seatUtilizationRate(point) {
  return capacityLoadRate(point)
}

export function queuePressureLevel(waitMinutes) {
  const numericWait = Number(waitMinutes || 0)
  if (numericWait < 5) {
    return { label: '低', tone: 'low' }
  }
  if (numericWait < 10) {
    return { label: '中', tone: 'medium' }
  }
  if (numericWait < 20) {
    return { label: '高', tone: 'high' }
  }
  return { label: '极高', tone: 'extreme' }
}

export function buildStatisticsSnapshot(run) {
  const timePoints = Array.isArray(run?.timePoints) ? run.timePoints : []
  if (!timePoints.length) {
    return null
  }

  const metrics = run?.metrics || {}
  const peakPoint = resolvePeakTimePoint(run)
  const labels = timePoints.map((point) => `${point.minute ?? 0} 分钟`)
  const queueSeries = timePoints.map(totalQueueLength)
  const loadRateSeries = timePoints.map(capacityLoadRate)
  const windowQueueLoadRows = buildWindowQueueLoadRows(timePoints)

  return {
    cards: [
      {
        key: 'avgWait',
        label: '平均排队时长',
        value: pickNumber(metrics.avgWaitMinutes, average(timePoints.map(avgWaitMinutes))),
        unit: '分钟',
        note: peakPoint ? `高峰快照第 ${peakPoint.minute} 分钟` : '',
      },
      {
        key: 'maxQueue',
        label: '最大排队人数',
        value: pickNumber(metrics.maxQueueLength, Math.max(...queueSeries)),
        unit: '人',
        note: peakPoint ? `高峰总排队 ${totalQueueLength(peakPoint)} 人` : '',
      },
      {
        key: 'sampleCount',
        label: '仿真样本人数',
        value: pickNumber(metrics.totalVirtualUsers, run?.scenario?.virtualUserCount, 0),
        unit: '人',
        note: `已服务 ${pickNumber(metrics.servedUserCount, 0)} 人`,
      },
      {
        key: 'unserved',
        label: '未服务人数',
        value: pickNumber(metrics.unservedUserCount, 0),
        unit: '人',
        note: peakPoint ? `高峰负载 ${totalCurrentCount(peakPoint)} 人` : '',
      },
    ],
    series: {
      labels,
      queueSeries,
      loadRateSeries,
      windowQueueLoadRows,
    },
  }
}

function buildWindowQueueLoadRows(timePoints) {
  const rows = new Map()

  timePoints.forEach((point) => {
    flattenWindows(point).forEach(({ restaurantName, window }) => {
      const key = String(window.windowId ?? `${restaurantName}-${window.name}`)
      if (!rows.has(key)) {
        rows.set(key, {
          key,
          name: window.name || `窗口 ${key}`,
          restaurantName,
          queueTotal: 0,
          loadTotal: 0,
          sampleCount: 0,
        })
      }

      const row = rows.get(key)
      const queueLength = Number(window.queueLength || 0)
      const servingCount = Number(window.servingCount || 0)
      row.queueTotal += queueLength
      row.loadTotal += queueLength + servingCount
      row.sampleCount += 1
    })
  })

  return [...rows.values()]
    .map((row) => ({
      ...row,
      averageQueue: row.sampleCount ? round(row.queueTotal / row.sampleCount, 1) : 0,
      averageLoad: row.sampleCount ? round(row.loadTotal / row.sampleCount, 1) : 0,
    }))
    .sort((left, right) => {
      if (right.averageLoad !== left.averageLoad) {
        return right.averageLoad - left.averageLoad
      }
      return right.averageQueue - left.averageQueue
    })
}

function average(values) {
  const list = values.filter((value) => Number.isFinite(value))
  if (!list.length) {
    return 0
  }
  return list.reduce((sum, value) => sum + value, 0) / list.length
}

function pickNumber(...values) {
  return values.find((value) => Number.isFinite(value)) ?? 0
}

function round(value, digits) {
  const factor = 10 ** digits
  return Math.round(Number(value || 0) * factor) / factor
}

function interpolateRestaurants(lowerRestaurants, upperRestaurants, ratio) {
  const upperById = new Map(
    upperRestaurants.map((restaurant) => [String(restaurant.restaurantId), restaurant]),
  )

  return lowerRestaurants.map((lowerRestaurant) => {
    const upperRestaurant =
      upperById.get(String(lowerRestaurant.restaurantId)) || lowerRestaurant
    const currentCount = interpolateInteger(
      lowerRestaurant.currentCount,
      upperRestaurant.currentCount,
      ratio,
    )
    const capacity = Number(upperRestaurant.capacity || lowerRestaurant.capacity || 0)

    return {
      ...lowerRestaurant,
      ...upperRestaurant,
      currentCount,
      capacity,
      crowdLevel: restaurantCrowdLevel(currentCount, capacity),
      windows: interpolateWindows(
        lowerRestaurant.windows || [],
        upperRestaurant.windows || [],
        ratio,
      ),
    }
  })
}

function interpolateWindows(lowerWindows, upperWindows, ratio) {
  const upperById = new Map(upperWindows.map((window) => [String(window.windowId), window]))

  return lowerWindows.map((lowerWindow) => {
    const upperWindow = upperById.get(String(lowerWindow.windowId)) || lowerWindow
    const queueLength = interpolateInteger(
      lowerWindow.queueLength,
      upperWindow.queueLength,
      ratio,
    )
    const servingCount = interpolateInteger(
      lowerWindow.servingCount,
      upperWindow.servingCount,
      ratio,
    )
    const waitMinutes = interpolateNumber(
      lowerWindow.waitMinutes,
      upperWindow.waitMinutes,
      ratio,
      1,
    )
    const status =
      lowerWindow.status === 'CLOSED' && upperWindow.status === 'CLOSED'
        ? 'CLOSED'
        : upperWindow.status || lowerWindow.status

    return {
      ...lowerWindow,
      ...upperWindow,
      queueLength,
      servingCount,
      waitMinutes,
      status,
      crowdLevel: status === 'CLOSED' ? 'IDLE' : windowCrowdLevel(waitMinutes),
    }
  })
}

function interpolateInteger(lowerValue, upperValue, ratio) {
  return Math.round(interpolateNumber(lowerValue, upperValue, ratio))
}

function interpolateNumber(lowerValue, upperValue, ratio, digits = 0) {
  const lower = Number(lowerValue || 0)
  const upper = Number(upperValue || 0)
  return round(lower + (upper - lower) * ratio, digits)
}

function restaurantCrowdLevel(currentCount, capacity) {
  const ratio = capacity > 0 ? currentCount / capacity : 0
  if (ratio < 0.4) return 'IDLE'
  if (ratio < 0.7) return 'NORMAL'
  if (ratio < 0.9) return 'BUSY'
  return 'EXTREME'
}

function windowCrowdLevel(waitMinutes) {
  if (waitMinutes < 5) return 'IDLE'
  if (waitMinutes < 10) return 'NORMAL'
  if (waitMinutes < 20) return 'BUSY'
  return 'EXTREME'
}

function clonePoint(point, minute) {
  return {
    ...point,
    minute,
    restaurants: (point.restaurants || []).map((restaurant) => ({
      ...restaurant,
      windows: (restaurant.windows || []).map((window) => ({ ...window })),
    })),
  }
}
