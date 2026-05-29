import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import {
  compareStrategies,
  getDishes,
  getRestaurants,
  getScenarioPresets,
  getUserProfiles,
  getWindows,
  runSimulation,
} from '../api/simulation'
import { generateRecommendation } from '../api/recommendation'

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
  const baseRun = ref(null)
  const compareRun = ref(null)
  const recommendation = ref(null)
  const comparison = ref(null)
  const currentMinute = ref(0)
  const loading = ref(false)
  const running = ref(false)
  const comparing = ref(false)
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
      currentMinute.value = run.timePoints.at(-1)?.minute || 0
      await refreshRecommendation()
      lastRunMeta.value = rememberRun(run)
      ElMessage.success('仿真完成')
    } finally {
      running.value = false
    }
  }

  async function refreshRecommendation() {
    if (!currentRun.value) return
    recommendation.value = await generateRecommendation({
      runId: currentRun.value.runId,
      minute: currentMinute.value,
      profile: clone(profileForm.value),
      limit: 3,
    })
  }

  function setCurrentMinute(minute) {
    currentMinute.value = minute
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

  function setBaseRun() {
    if (!currentRun.value) return
    baseRun.value = clone(currentRun.value)
    comparison.value = null
    compareRun.value = null
    ElMessage.success('已设置基准场景')
  }

  async function runCompareSimulation() {
    if (!baseRun.value) {
      ElMessage.warning('请先设置基准场景')
      return
    }
    comparing.value = true
    try {
      const run = await runSimulation({
        profile: clone(profileForm.value),
        scenario: clone(scenarioForm.value),
      })
      compareRun.value = run
      currentRun.value = run
      currentMinute.value = run.timePoints.at(-1)?.minute || 0
      recommendation.value = await generateRecommendation({
        runId: run.runId,
        minute: currentMinute.value,
        profile: clone(profileForm.value),
        limit: 3,
      })
      comparison.value = await compareStrategies({
        baseRunId: baseRun.value.runId,
        compareRunId: run.runId,
      })
      lastRunMeta.value = rememberRun(run)
      ElMessage.success('对比完成')
    } finally {
      comparing.value = false
    }
  }

  function clearComparison() {
    baseRun.value = null
    compareRun.value = null
    comparison.value = null
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
    baseRun,
    compareRun,
    recommendation,
    comparison,
    currentMinute,
    currentTimePoint,
    maxMinute,
    loading,
    running,
    comparing,
    lastRunMeta,
    initializeDashboard,
    applyProfilePreset,
    applyScenarioPreset,
    runCurrentSimulation,
    refreshRecommendation,
    setCurrentMinute,
    resetForms,
    setBaseRun,
    runCompareSimulation,
    clearComparison,
    saveCurrentScheme,
  }
})

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
  const meta = {
    runId: run.runId,
    createdAt: run.createdAt || new Date().toISOString(),
    status: run.status || 'FINISHED',
    scenarioName: run.scenario?.name || run.scenario?.mealPeriod || '当前场景',
    durationMinutes: run.scenario?.durationMinutes || 0,
    avgWaitMinutes: run.metrics?.avgWaitMinutes ?? null,
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
