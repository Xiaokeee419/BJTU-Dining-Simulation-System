import { flattenWindows, resolveTimePoint } from './simulationStats'

export const OVERLOAD_THRESHOLD = 10

export function isFiniteMetric(value) {
  return value !== null && value !== undefined && Number.isFinite(Number(value))
}

export function finiteMetricOrNull(value) {
  return isFiniteMetric(value) ? Number(value) : null
}

export function formatNumber(value, digits = 1) {
  if (!isFiniteMetric(value)) return '--'
  return Number(value).toFixed(digits).replace(/\.0+$/, '')
}

export function formatMetricValue(value, digits = 1, unit = '') {
  if (!isFiniteMetric(value)) return '--'
  return `${formatNumber(value, digits)}${unit ? ` ${unit}` : ''}`
}

export function metricDelta(before, after) {
  if (!isFiniteMetric(before) || !isFiniteMetric(after)) return null
  return Number(after) - Number(before)
}

export function improvementPercent(before, after) {
  if (!isFiniteMetric(before) || !isFiniteMetric(after)) return null
  if (Number(before) === 0) return Number(after) === 0 ? 0 : null
  return ((Number(before) - Number(after)) / Math.abs(Number(before))) * 100
}

export function lowerBetterTone(delta) {
  if (!isFiniteMetric(delta)) return ''
  if (Number(delta) < 0) return 'success'
  if (Number(delta) > 0) return 'danger'
  return ''
}

export function formatImprovementText(before, after, digits = 1, unit = '') {
  const delta = metricDelta(before, after)
  if (!isFiniteMetric(delta)) return '--'
  if (Number(delta) === 0) return '持平'
  const amount = formatMetricValue(Math.abs(delta), digits, unit)
  return Number(delta) < 0 ? `下降 ${amount}` : `上升 ${amount}`
}

export function formatPercentChange(value) {
  if (!isFiniteMetric(value)) return '--'
  if (Number(value) === 0) return '持平'
  return `${Number(value) > 0 ? '下降' : '上升'} ${formatNumber(Math.abs(value), 1)}%`
}

export function resolveSuggestionSourceWindowId(suggestion) {
  const value =
    suggestion?.fromWindowId ??
    suggestion?.sourceWindowId ??
    suggestion?.sourceWindow?.windowId ??
    suggestion?.sourceWindow?.id
  return value === null || value === undefined || value === '' ? null : Number(value)
}

export function resolveSourceWindowIds(suggestions = []) {
  return [
    ...new Set(
      suggestions
        .map(resolveSuggestionSourceWindowId)
        .filter((windowId) => windowId !== null),
    ),
  ]
}

export function sumSuggestionField(suggestions = [], field) {
  if (!suggestions.length) return null
  const values = suggestions
    .map((suggestion) => suggestion?.[field])
    .filter(isFiniteMetric)
    .map(Number)
  return values.length ? values.reduce((sum, value) => sum + value, 0) : null
}

export function windowQueueLength(window) {
  const value = window?.queueLength ?? window?.queueCount
  return isFiniteMetric(value) ? Number(value) : 0
}

export function resolveWindowWait(window) {
  return finiteMetricOrNull(
    window?.waitMinutes ??
      window?.avgWaitTime ??
      window?.averageWaitTime ??
      window?.avgWaitMinutes,
  )
}

export function findWindow(point, windowId) {
  return flattenWindows(point).find(
    ({ window }) => Number(window.windowId) === Number(windowId),
  )?.window
}

export function windowLabel(point, windowId) {
  const item = flattenWindows(point).find(
    ({ window }) => Number(window.windowId) === Number(windowId),
  )
  return item ? `${item.restaurantName} / ${item.window.name}` : `窗口 ${windowId}`
}

export function buildBottleneckMetrics(run, minute, sourceWindowIds = []) {
  const comparisonPoint = resolveTimePoint(run, minute)
  const comparisonWindows = sourceWindowIds
    .map((windowId) => findWindow(comparisonPoint, windowId))
    .filter(Boolean)
  const sourceWaitValues = comparisonWindows.map(resolveWindowWait).filter(isFiniteMetric)
  const timePoints = Array.isArray(run?.timePoints) ? run.timePoints : []

  let maxWindowQueue = null
  let peakTotalQueue = null
  let totalOverload = null
  let extremeOverload = null
  let loadImbalance = null

  if (timePoints.length) {
    maxWindowQueue = 0
    peakTotalQueue = 0
    totalOverload = 0
    extremeOverload = 0
    loadImbalance = 0

    timePoints.forEach((point) => {
      const windows = flattenWindows(point).map((entry) => entry.window)
      const totalQueue = windows.reduce((sum, window) => sum + windowQueueLength(window), 0)
      peakTotalQueue = Math.max(peakTotalQueue, totalQueue)

      windows.forEach((window) => {
        const queueLength = windowQueueLength(window)
        maxWindowQueue = Math.max(maxWindowQueue, queueLength)
        totalOverload += Math.max(queueLength - OVERLOAD_THRESHOLD, 0)
        extremeOverload += Math.max(queueLength - 15, 0) ** 2
      })

      ;(point.restaurants || []).forEach((restaurant) => {
        const queues = (restaurant.windows || []).map(windowQueueLength)
        if (!queues.length) return
        const averageQueue = queues.reduce((sum, queue) => sum + queue, 0) / queues.length
        queues.forEach((queue) => {
          loadImbalance += (queue - averageQueue) ** 2
        })
      })
    })
  }

  return {
    sourceQueue:
      sourceWindowIds.length && comparisonWindows.length
        ? comparisonWindows.reduce((sum, window) => sum + windowQueueLength(window), 0)
        : null,
    sourceWait: sourceWaitValues.length
      ? sourceWaitValues.reduce((sum, value) => sum + Number(value), 0) /
        sourceWaitValues.length
      : null,
    maxWindowQueue,
    peakTotalQueue,
    totalOverload,
    extremeOverload,
    targetOverload: null,
    loadImbalance,
    unservedUsers: finiteMetricOrNull(run?.metrics?.unservedUserCount),
  }
}

export function normalizeOptimizationMetrics(metrics, fallbackMetrics) {
  return {
    sourceQueue: finiteMetricOrNull(metrics?.sourceWindowQueueTotal),
    sourceWait: finiteMetricOrNull(metrics?.sourceWindowAverageWait),
    maxWindowQueue: finiteMetricOrNull(
      metrics?.maxSingleWindowQueue ?? fallbackMetrics?.maxQueueLength,
    ),
    peakTotalQueue: finiteMetricOrNull(metrics?.peakTotalQueue),
    totalOverload: finiteMetricOrNull(metrics?.totalOverload),
    extremeOverload: finiteMetricOrNull(metrics?.extremeOverloadSeverity),
    targetOverload: finiteMetricOrNull(metrics?.targetWindowOverload),
    loadImbalance: finiteMetricOrNull(metrics?.loadImbalancePenalty),
    unservedUsers: finiteMetricOrNull(
      metrics?.unservedUserCount ?? fallbackMetrics?.unservedUserCount,
    ),
  }
}

export function buildBottleneckRow(key, label, baseline, compare, digits, unit) {
  const delta = metricDelta(baseline, compare)
  const percent = improvementPercent(baseline, compare)
  let changeLabel = '--'
  if (isFiniteMetric(delta)) {
    changeLabel =
      Number(delta) === 0
        ? '持平'
        : `${Number(delta) < 0 ? '减少' : '增加'} ${formatMetricValue(
            Math.abs(delta),
            digits,
            unit,
          )}`
  }

  return {
    key,
    label,
    baseline: finiteMetricOrNull(baseline),
    compare: finiteMetricOrNull(compare),
    baselineLabel: formatMetricValue(baseline, digits, unit),
    compareLabel: formatMetricValue(compare, digits, unit),
    changeLabel,
    percent,
    percentLabel: formatPercentChange(percent),
    tone: lowerBetterTone(delta),
  }
}

export function buildFiveMetricRows(baseline, compare) {
  return [
    buildBottleneckRow(
      'sourceQueue',
      '来源窗口总排队',
      baseline.sourceQueue,
      compare.sourceQueue,
      0,
      '人',
    ),
    buildBottleneckRow(
      'sourceWait',
      '来源窗口平均等待',
      baseline.sourceWait,
      compare.sourceWait,
      1,
      '分钟',
    ),
    buildBottleneckRow(
      'maxWindowQueue',
      '最大单窗口排队',
      baseline.maxWindowQueue,
      compare.maxWindowQueue,
      0,
      '人',
    ),
    buildBottleneckRow(
      'peakTotalQueue',
      '峰值总排队人数',
      baseline.peakTotalQueue,
      compare.peakTotalQueue,
      0,
      '人',
    ),
    buildBottleneckRow(
      'totalOverload',
      '总过载程度',
      baseline.totalOverload,
      compare.totalOverload,
      0,
      '',
    ),
  ]
}

export function buildThreeStageRow(
  label,
  baseline,
  strategy,
  optimized,
  digits,
  unit,
) {
  return {
    label,
    baseline: formatMetricValue(baseline, digits, unit),
    strategy: formatMetricValue(strategy, digits, unit),
    optimized: formatMetricValue(optimized, digits, unit),
    vsBaseline: formatImprovementText(baseline, optimized, digits, unit),
    vsStrategy: formatImprovementText(strategy, optimized, digits, unit),
  }
}

export function buildFurtherImprovementCard(label, before, after, digits, unit) {
  const delta = metricDelta(before, after)
  const percent = improvementPercent(before, after)
  return {
    label,
    transition:
      isFiniteMetric(before) && isFiniteMetric(after)
        ? `${formatMetricValue(before, digits, unit)} → ${formatMetricValue(
            after,
            digits,
            unit,
          )}`
        : '--',
    detail:
      isFiniteMetric(delta) && Number(delta) !== 0
        ? `${Number(delta) < 0 ? '再降' : '增加'} ${formatMetricValue(
            Math.abs(delta),
            digits,
            unit,
          )}${
            isFiniteMetric(percent) ? `（${formatNumber(Math.abs(percent), 1)}%）` : ''
          }`
        : isFiniteMetric(delta)
          ? '持平'
          : '--',
    tone: lowerBetterTone(delta),
  }
}
