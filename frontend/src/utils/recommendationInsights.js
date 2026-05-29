import { buildStatisticsSnapshot } from './simulationStats'

export function buildRecommendationInsights({
  currentRun,
  baseRun,
  compareRun,
  comparison,
  recommendation,
  windows,
}) {
  if (!currentRun) {
    return null
  }

  const sourceRun = compareRun || currentRun
  const currentPlanRun = comparison && baseRun ? baseRun : currentRun
  const recommendedPlanRun = comparison ? compareRun || currentRun : null
  const currentSummary = buildStatisticsSnapshot(currentPlanRun)
  const recommendedSummary = buildStatisticsSnapshot(recommendedPlanRun || currentRun)
  const activeWindows = countActiveWindows(sourceRun, windows)
  const recommendedIncrease = currentRun.metrics?.avgWaitMinutes > 8 ? 2 : 1
  const recommendedOpenWindows = activeWindows + recommendedIncrease
  const peakWindow = recommendedSummary?.summary.peakMinutes || inferPeakLabel(sourceRun)

  const currentMetrics = currentPlanRun?.metrics || {}
  const recommendedMetrics = comparison
    ? {
        avgWaitMinutes: round(
          compareRun?.metrics?.avgWaitMinutes ??
            (currentMetrics.avgWaitMinutes || 0) + (comparison.avgWaitDelta || 0),
          1,
        ),
        seatUtilization: round(findCardValue(recommendedSummary, 'seatUtilization'), 1),
        congestion: round(findCardValue(recommendedSummary, 'congestion'), 1),
        servedUserCount:
          compareRun?.metrics?.servedUserCount ??
          (currentMetrics.servedUserCount || 0) + (comparison.servedUserCountDelta || 0),
      }
    : {
        avgWaitMinutes: round(Math.max(2, (currentMetrics.avgWaitMinutes || 0) * 0.64), 1),
        seatUtilization: round(findCardValue(currentSummary, 'seatUtilization') * 1.12, 1),
        congestion: round(Math.max(18, findCardValue(currentSummary, 'congestion') * 0.68), 1),
        servedUserCount: Math.round((currentMetrics.servedUserCount || 0) * 1.18),
      }

  const improvementWait = percentageDelta(
    currentMetrics.avgWaitMinutes,
    recommendedMetrics.avgWaitMinutes,
  )
  const improvementSeat = percentageDelta(
    findCardValue(currentSummary, 'seatUtilization'),
    recommendedMetrics.seatUtilization,
  )
  const improvementServed = percentageDelta(
    currentMetrics.servedUserCount,
    recommendedMetrics.servedUserCount,
  )

  const planItems = [
    {
      rank: 1,
      title: '增加 1-2 个窗口',
      description: `建议优先在高峰时段临时增开 ${recommendedIncrease}-${recommendedIncrease + 1} 个窗口，优先缓解当前排队压力较大的点餐区域。`,
      priority: 'high',
    },
    {
      rank: 2,
      title: '优化热门菜供应',
      description: recommendation?.diversionSuggestion || '针对热度较高的菜品延长供餐时段，减少因缺货导致的二次排队。',
      priority: 'high',
    },
    {
      rank: 3,
      title: '高峰时段引导分流',
      description: `建议在 ${peakWindow} 加强导视与广播，引导人群向等待时间更短的窗口和餐厅分散。`,
      priority: 'medium',
    },
    {
      rank: 4,
      title: '提升座位周转率',
      description: '通过高峰后快速清洁与引导合座，提升就餐区周转效率，缓解座位紧张。',
      priority: 'medium',
    },
  ]

  return {
    topCards: [
      {
        key: 'windows',
        label: '推荐开放窗口数',
        value: recommendedOpenWindows,
        unit: '个',
        note: `当前开放 ${activeWindows} 个，建议 +${recommendedIncrease} 个`,
      },
      {
        key: 'peak',
        label: '预计高峰时段',
        value: peakWindow.replace(' 分钟', ''),
        unit: '',
        note: '建议重点关注该时段并加强疏导',
      },
      {
        key: 'effect',
        label: '预期优化效果',
        value: improvementWait,
        unit: '',
        note: `平均等待 ${improvementWait}，座位利用率 ${improvementSeat}，总服务人数 ${improvementServed}`,
      },
    ],
    planItems,
    effectRows: [
      {
        label: '平均排队时长',
        unit: '分钟/人',
        current: round(currentMetrics.avgWaitMinutes || 0, 1),
        recommended: round(recommendedMetrics.avgWaitMinutes || 0, 1),
      },
      {
        label: '座位利用率',
        unit: '%',
        current: round(findCardValue(currentSummary, 'seatUtilization'), 1),
        recommended: round(recommendedMetrics.seatUtilization || 0, 1),
      },
      {
        label: '拥堵指数',
        unit: '0-100',
        current: round(findCardValue(currentSummary, 'congestion'), 1),
        recommended: round(recommendedMetrics.congestion || 0, 1),
      },
      {
        label: '总服务人数',
        unit: '人/天',
        current: round(currentMetrics.servedUserCount || 0, 0),
        recommended: round(recommendedMetrics.servedUserCount || 0, 0),
      },
    ],
    priorities: planItems,
    suggestions: [
      '保存当前推荐方案，作为下一轮配置调整的参考基线。',
      '优先验证高峰时段窗口增开和分流策略，再观察平均等待变化。',
    ],
  }
}

function countActiveWindows(run, windows) {
  const lastPoint = run?.timePoints?.at?.(-1)
  if (lastPoint?.restaurants?.length) {
    return lastPoint.restaurants
      .flatMap((restaurant) => restaurant.windows)
      .filter((window) => window.status !== 'CLOSED').length
  }
  return (windows || []).filter((window) => window.status !== 'CLOSED').length
}

function inferPeakLabel(run) {
  const summary = buildStatisticsSnapshot(run)
  return summary?.summary.peakMinutes || '11:30 - 12:30'
}

function percentageDelta(current, next) {
  const base = Number(current || 0)
  const target = Number(next || 0)
  if (!base) return '--'
  const raw = ((target - base) / base) * 100
  const value = Math.round(raw)
  return `${value > 0 ? '+' : ''}${value}%`
}

function findCardValue(summary, key) {
  return summary?.cards.find((item) => item.key === key)?.value || 0
}

function round(value, digits) {
  const base = 10 ** digits
  return Math.round(Number(value || 0) * base) / base
}
