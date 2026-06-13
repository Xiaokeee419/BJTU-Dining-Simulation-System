import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import {
  getDishes,
  getRestaurants,
  getScenarioPresets,
  getSimulation,
  getUserProfiles,
  getWindows,
  runDiversionComparison as requestDiversionComparison,
  runSimulation,
} from '../api/simulation'
import {
  generateDiversionSuggestion,
  generateRecommendation,
} from '../api/recommendation'
import { openWindowCount, resolvePeakTimePoint, totalCurrentCount } from '../utils/simulationStats'

export const useSimulationStore = defineStore('simulation', () => {
  const profiles = ref([])
  const scenarios = ref([])
  const restaurants = ref([])
  const windows = ref([])
  const dishes = ref([])
  const initialized = ref(false)

  const selectedProfileId = ref('')
  const selectedScenarioId = ref('')
  const profileForm = ref({})
  const scenarioForm = ref({})
  const strategyForm = ref(defaultStrategyForm())

  const currentRun = ref(null)
  const baselineRun = ref(null)
  const compareRun = ref(null)
  const recommendation = ref(null)
  const diversionResult = ref(null)
  const strategyComparison = ref(null)
  const baseMetrics = ref(null)
  const compareMetrics = ref(null)
  const diversionComparisonStatus = ref('IDLE')
  const selectedCompareMinute = ref(null)
  const comparisonError = ref('')
  const currentMinute = ref(0)
  const loading = ref(false)
  const running = ref(false)
  const lastRunMeta = ref(loadLastRunMeta())
  let initializePromise = null

  const timePoints = computed(() => currentRun.value?.timePoints || [])
  const currentTimePoint = computed(() => {
    if (!timePoints.value.length) return null
    return (
      timePoints.value.find((point) => point.minute === currentMinute.value) ||
      timePoints.value[timePoints.value.length - 1]
    )
  })
  const maxMinute = computed(() =>
    timePoints.value.length ? timePoints.value[timePoints.value.length - 1].minute : 0,
  )
  const comparing = computed(() => diversionComparisonStatus.value === 'RUNNING')

  const baseRun = computed(() => baselineRun.value)
  const diversion = computed(() => diversionResult.value)
  const comparison = computed(() => strategyComparison.value)

  async function initializeDashboard({ force = false } = {}) {
    if (initialized.value && !force) {
      return
    }
    if (initializePromise && !force) {
      return initializePromise
    }

    loading.value = true
    initializePromise = (async () => {
      const [profileData, scenarioData, restaurantData, windowData, dishData] =
        await Promise.all([
          getUserProfiles(),
          getScenarioPresets(),
          getRestaurants(),
          getWindows(),
          getDishes(),
        ])
      profiles.value = profileData
      scenarios.value = scenarioData
      restaurants.value = restaurantData
      windows.value = windowData
      dishes.value = dishData

      const savedScheme = loadSavedScheme()
      const profileId =
        selectedProfileId.value && profileData.some((item) => item.profileId === selectedProfileId.value)
          ? selectedProfileId.value
          : savedScheme?.selectedProfileId && profileData.some((item) => item.profileId === savedScheme.selectedProfileId)
            ? savedScheme.selectedProfileId
            : profileData[0]?.profileId
      const scenarioId =
        selectedScenarioId.value && scenarioData.some((item) => item.scenarioId === selectedScenarioId.value)
          ? selectedScenarioId.value
          : savedScheme?.selectedScenarioId && scenarioData.some((item) => item.scenarioId === savedScheme.selectedScenarioId)
            ? savedScheme.selectedScenarioId
            : scenarioData[0]?.scenarioId

      if (profileId) {
        applyProfilePreset(profileId)
      }
      if (scenarioId) {
        applyScenarioPreset(scenarioId)
      }

      if (savedScheme?.profileForm) {
        profileForm.value = {
          ...profileForm.value,
          ...clone(savedScheme.profileForm),
        }
      }
      if (savedScheme?.scenarioForm) {
        scenarioForm.value = {
          ...scenarioForm.value,
          ...clone(savedScheme.scenarioForm),
        }
      }
      if (savedScheme?.strategyForm) {
        strategyForm.value = {
          ...defaultStrategyForm(),
          ...clone(savedScheme.strategyForm),
        }
      }

      initialized.value = true
    })()

    try {
      await initializePromise
    } finally {
      initializePromise = null
      loading.value = false
    }
  }

  function applyProfilePreset(profileId) {
    const profile = profiles.value.find((item) => item.profileId === profileId)
    if (!profile) return
    selectedProfileId.value = profile.profileId
    profileForm.value = clone(profile)
  }

  function applyScenarioPreset(scenarioId) {
    const scenario = scenarios.value.find((item) => item.scenarioId === scenarioId)
    if (!scenario) return
    selectedScenarioId.value = scenario.scenarioId
    scenarioForm.value = clone(scenario)
  }

  async function runCurrentSimulation() {
    running.value = true
    try {
      const run = await runSimulation({
        profile: clone(profileForm.value),
        scenario: clone(scenarioForm.value),
      })
      currentRun.value = run
      currentMinute.value = resolveDefaultMinute(run)
      selectedCompareMinute.value = currentMinute.value
      clearComparisonState({ clearBaseline: true })
      await refreshRecommendation({ minute: currentMinute.value })
      lastRunMeta.value = rememberRun(run)
      ElMessage.success('仿真完成')
    } finally {
      running.value = false
    }
  }

  async function refreshRecommendation({ minute = currentMinute.value } = {}) {
    if (!currentRun.value) return
    const selectedMinute = resolveRecommendationMinute(currentRun.value, minute)
    currentMinute.value = selectedMinute
    if (selectedCompareMinute.value == null) {
      selectedCompareMinute.value = selectedMinute
    }

    const recommendationPayload = {
      runId: currentRun.value.runId,
      minute: selectedMinute,
      profile: clone(profileForm.value),
      limit: 3,
    }
    const diversionPayload = {
      runId: currentRun.value.runId,
      minute: selectedMinute,
      targetCrowdLevel: resolveDiversionTargetLevel(
        currentRun.value?.scenario?.crowdLevel || scenarioForm.value.crowdLevel,
      ),
      profile: clone(profileForm.value),
    }

    const [recommendationResponse, diversionResponse] = await Promise.all([
      generateRecommendation(recommendationPayload),
      generateDiversionSuggestion(diversionPayload),
    ])

    recommendation.value = recommendationResponse
    if (!strategyComparison.value) {
      diversionResult.value = diversionResponse
    }
  }

  function setCurrentMinute(minute) {
    currentMinute.value = minute
  }

  function setSelectedCompareMinute(minute) {
    selectedCompareMinute.value = minute
    currentMinute.value = minute
    clearComparisonState({ clearBaseline: false, preserveDiversionSuggestion: false })
  }

  function resetForms() {
    const profileId = selectedProfileId.value || profiles.value[0]?.profileId
    const scenarioId = selectedScenarioId.value || scenarios.value[0]?.scenarioId

    if (profileId) {
      applyProfilePreset(profileId)
    }
    if (scenarioId) {
      applyScenarioPreset(scenarioId)
    }
    strategyForm.value = defaultStrategyForm()
    ElMessage.success('已重置为当前预设')
  }

  function saveBaselineRun() {
    const sourceRun = currentRun.value
    if (!sourceRun || sourceRun.status !== 'FINISHED') {
      ElMessage.warning('请先完成一次仿真，再保存基准方案')
      return
    }
    baselineRun.value = clone(sourceRun)
    baseMetrics.value = clone(sourceRun.metrics || null)
    selectedCompareMinute.value = resolvePeakMinute(sourceRun)
    clearComparisonState({ clearBaseline: false, preserveDiversionSuggestion: true })
    ElMessage.success('已保存未分流基准方案')
  }

  async function runDiversionComparison() {
    const sourceRun = baselineRun.value || currentRun.value
    if (!sourceRun || sourceRun.status !== 'FINISHED') {
      ElMessage.warning('请先完成一次仿真')
      return
    }

    if (!baselineRun.value) {
      ElMessage.warning('请先使用当前仿真作为未分流基准')
      return
    }

    const minute = selectedCompareMinute.value ?? resolvePeakMinute(baselineRun.value)
    diversionComparisonStatus.value = 'RUNNING'
    comparisonError.value = ''
    strategyComparison.value = null
    compareRun.value = null
    compareMetrics.value = null

    try {
      const result = await requestDiversionComparison({
        baseRunId: baselineRun.value.runId,
        minute,
        targetCrowdLevel: resolveDiversionTargetLevel(
          baselineRun.value?.scenario?.crowdLevel || scenarioForm.value.crowdLevel,
        ),
        autoRunCompare: true,
      })

      selectedCompareMinute.value = result.minute
      diversionResult.value = result.diversionResult || null
      strategyComparison.value = result.comparison || null
      baseMetrics.value = result.baseMetrics || baselineRun.value.metrics || null
      compareMetrics.value = result.compareMetrics || null
      diversionComparisonStatus.value = result.status || 'IDLE'

      if (result.compareRunId) {
        compareRun.value = await getSimulation(result.compareRunId)
      } else {
        compareRun.value = null
      }

      if (result.compareRunId && result.comparison && compareRun.value?.runId === result.compareRunId) {
        diversionComparisonStatus.value = 'COMPLETED'
        ElMessage.success('分流对比已完成')
      } else {
        if (diversionComparisonStatus.value === 'COMPLETED') {
          diversionComparisonStatus.value = 'IDLE'
        }
        const feedback = resolveComparisonFeedback(result)
        if (diversionComparisonStatus.value === 'ERROR') {
          comparisonError.value = feedback || '分流对比失败'
        }
        if (feedback) {
          ElMessage.warning(feedback)
        }
      }
    } catch (error) {
      diversionComparisonStatus.value = 'ERROR'
      comparisonError.value = error.message || '分流对比失败'
      throw error
    }
  }

  function clearComparison() {
    clearComparisonState({ clearBaseline: true, preserveDiversionSuggestion: false })
    ElMessage.success('已清除分流对比结果')
  }

  function clearComparisonState({
    clearBaseline = false,
    preserveDiversionSuggestion = false,
  } = {}) {
    if (clearBaseline) {
      baselineRun.value = null
      baseMetrics.value = null
    }
    compareRun.value = null
    compareMetrics.value = null
    strategyComparison.value = null
    diversionComparisonStatus.value = 'IDLE'
    comparisonError.value = ''
    if (!preserveDiversionSuggestion) {
      diversionResult.value = null
    }
  }

  function resolvePeakMinute(run) {
    return resolvePeakMinuteValue(run)
  }

  function saveCurrentScheme() {
    const payload = {
      selectedProfileId: selectedProfileId.value,
      selectedScenarioId: selectedScenarioId.value,
      profileForm: clone(profileForm.value),
      scenarioForm: clone(scenarioForm.value),
      strategyForm: clone(strategyForm.value),
      savedAt: new Date().toISOString(),
    }
    saveScheme(payload)
    ElMessage.success('当前方案已保存到本地')
  }

  return {
    profiles,
    scenarios,
    restaurants,
    windows,
    dishes,
    initialized,
    selectedProfileId,
    selectedScenarioId,
    profileForm,
    scenarioForm,
    strategyForm,
    currentRun,
    baselineRun,
    compareRun,
    recommendation,
    diversionResult,
    strategyComparison,
    baseMetrics,
    compareMetrics,
    diversionComparisonStatus,
    selectedCompareMinute,
    comparisonError,
    currentMinute,
    currentTimePoint,
    maxMinute,
    loading,
    running,
    comparing,
    lastRunMeta,
    baseRun,
    diversion,
    comparison,
    initializeDashboard,
    applyProfilePreset,
    applyScenarioPreset,
    runCurrentSimulation,
    refreshRecommendation,
    setCurrentMinute,
    setSelectedCompareMinute,
    resetForms,
    saveBaselineRun,
    resolvePeakMinute,
    runDiversionComparison,
    clearComparison,
    saveCurrentScheme,
    setBaseRun: saveBaselineRun,
    runCompareSimulation: runDiversionComparison,
  }
})

function resolveDefaultMinute(run) {
  return resolvePeakMinuteValue(run)
}

function resolveRecommendationMinute(run, minute) {
  if (minute != null && run?.timePoints?.some((point) => point.minute === minute)) {
    return minute
  }
  return resolveDefaultMinute(run)
}

function resolvePeakMinuteValue(run) {
  const peakPoint = resolvePeakTimePoint(run)
  if (peakPoint?.minute != null) {
    return peakPoint.minute
  }
  return run?.timePoints?.[0]?.minute || 0
}

function resolveDiversionTargetLevel(crowdLevel) {
  const normalized = String(crowdLevel || 'NORMAL').toUpperCase()
  if (normalized === 'IDLE') return 'IDLE'
  return 'NORMAL'
}

function resolveComparisonFeedback(result) {
  if (result?.status === 'MOCK_UNSUPPORTED') {
    return 'Mock 模式暂不支持真实分流对比，请切换到真实后端接口。'
  }
  if (result?.status === 'ERROR') {
    return result?.message || result?.diversionResult?.reason || '分流对比失败'
  }
  return result?.diversionResult?.reason || ''
}

function clone(value) {
  return JSON.parse(JSON.stringify(value))
}

function defaultStrategyForm() {
  return {
    stagedWindowOpening: 'ENABLED',
    popularDishRestock: 'SMART',
    peakDiversion: 'ENABLED',
  }
}

function saveScheme(payload) {
  if (typeof window === 'undefined') return
  window.localStorage.setItem('bjtu-dining-saved-scheme', JSON.stringify(payload))
}

function loadSavedScheme() {
  if (typeof window === 'undefined') return null
  const raw = window.localStorage.getItem('bjtu-dining-saved-scheme')
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function rememberRun(run) {
  const peakPoint = resolvePeakTimePoint(run)
  const meta = {
    runId: run.runId,
    createdAt: run.createdAt || new Date().toISOString(),
    status: run.status || 'FINISHED',
    scenarioName: run.scenario?.name || run.scenario?.mealPeriod || '当前场景',
    durationMinutes: run.scenario?.durationMinutes || 0,
    avgWaitMinutes: run.metrics?.avgWaitMinutes ?? null,
    maxQueueLength: run.metrics?.maxQueueLength ?? null,
    peakMinute: peakPoint?.minute ?? null,
    peakLoadCount: peakPoint ? totalCurrentCount(peakPoint) : null,
    openWindowCount: peakPoint ? openWindowCount(peakPoint) : null,
  }
  if (typeof window !== 'undefined') {
    window.localStorage.setItem('bjtu-dining-last-run', JSON.stringify(meta))
  }
  return meta
}

function loadLastRunMeta() {
  if (typeof window === 'undefined') return null
  const raw = window.localStorage.getItem('bjtu-dining-last-run')
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}
