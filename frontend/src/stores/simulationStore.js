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

  const selectedProfileId = ref('')
  const selectedScenarioId = ref('')
  const profileForm = ref({})
  const scenarioForm = ref({})

  const currentRun = ref(null)
  const baseRun = ref(null)
  const compareRun = ref(null)
  const recommendation = ref(null)
  const comparison = ref(null)
  const currentMinute = ref(0)
  const loading = ref(false)
  const running = ref(false)
  const comparing = ref(false)

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

  async function initializeDashboard() {
    loading.value = true
    try {
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
      applyProfilePreset(profileData[0]?.profileId)
      applyScenarioPreset(scenarioData[0]?.scenarioId)
    } finally {
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

  return {
    profiles,
    scenarios,
    restaurants,
    windows,
    dishes,
    selectedProfileId,
    selectedScenarioId,
    profileForm,
    scenarioForm,
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
    initializeDashboard,
    applyProfilePreset,
    applyScenarioPreset,
    runCurrentSimulation,
    refreshRecommendation,
    setCurrentMinute,
    setBaseRun,
    runCompareSimulation,
    clearComparison,
  }
})

function clone(value) {
  return JSON.parse(JSON.stringify(value))
}
