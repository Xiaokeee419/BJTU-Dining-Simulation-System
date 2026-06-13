import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getCsvFlowCurves,
  getDashboardSimulation,
  getDataOverview,
  getDiversionSuggestions,
  getOptimizationBest,
  getOptimizationIterations,
  getOptimizationJob,
  getSimulationOptions,
  runDashboardComparison,
  runDashboardSimulation,
  startOptimization as requestOptimization,
} from '../api/dashboard'
import { isCanceledRequest } from '../api/http'
import { resolvePeakTimePoint } from '../utils/simulationStats'

export const useDashboardStore = defineStore('diversion-dashboard', () => {
  const profiles = ref([])
  const scenarios = ref([])
  const restaurants = ref([])
  const windows = ref([])
  const dataOverview = ref(null)
  const flowCurve = ref(null)

  const form = reactive({
    userType: 'STUDENT',
    mealPeriod: 'LUNCH',
    dayType: 'WEEKDAY',
    crowdLevel: 'BUSY',
    virtualUserCount: 3000,
    durationMinutes: 90,
    randomSeed: 20260612,
  })
  const strategyParameters = reactive(defaultStrategyParameters())
  const optimizationSettings = reactive({
    iterationCount: 12,
    randomSeed: 20260613,
  })

  const currentRun = ref(null)
  const compareRun = ref(null)
  const diversionResult = ref(null)
  const comparison = ref(null)
  const optimizationJob = ref(null)
  const optimizationIterations = ref([])
  const optimizationBest = ref(null)

  const loading = reactive({
    initialize: false,
    flow: false,
    simulation: false,
    diversion: false,
    comparison: false,
    optimization: false,
  })
  const requestStatus = ref('IDLE')
  const lastError = ref(null)
  const rawResponses = ref([])
  const lastChartUpdatedAt = ref(null)

  const controllers = new Map()
  let sessionId = 0
  let pollTimer = null

  const targetCrowdLevel = computed(() => {
    if (form.crowdLevel === 'IDLE') return 'IDLE'
    return 'NORMAL'
  })
  const peakPoint = computed(() => resolvePeakTimePoint(currentRun.value))
  const staleDataRisk = computed(() => {
    if (!currentRun.value) return false
    if (comparison.value && comparison.value.baseRunId !== currentRun.value.runId) return true
    if (compareRun.value && comparison.value?.compareRunId !== compareRun.value.runId) return true
    if (optimizationJob.value && optimizationJob.value.baseRunId !== currentRun.value.runId) return true
    return false
  })

  async function initialize() {
    loading.initialize = true
    requestStatus.value = 'INITIALIZING'
    lastError.value = null
    const controller = replaceController('initialize')
    try {
      const [options, overview] = await Promise.all([
        getSimulationOptions({ signal: controller.signal }),
        getDataOverview({ signal: controller.signal }),
      ])
      profiles.value = options.profiles || []
      scenarios.value = options.scenarios || []
      restaurants.value = options.restaurants || []
      windows.value = options.windows || []
      dataOverview.value = overview
      applyAvailableDefaults()
      rememberResponse('data-overview', overview)
      await refreshFlowCurve()
      requestStatus.value = 'READY'
    } catch (error) {
      handleError(error, '初始化数据读取失败')
      throw error
    } finally {
      finishOperation('initialize', controller, () => {
        loading.initialize = false
      })
    }
  }

  async function refreshFlowCurve() {
    loading.flow = true
    const controller = replaceController('flow')
    try {
      const result = await getCsvFlowCurves(
        { mealPeriod: form.mealPeriod, bucketMinutes: 3 },
        { signal: controller.signal },
      )
      flowCurve.value = result
      if (lastError.value?.endpoint?.includes('/data/flow-curves')) {
        lastError.value = null
      }
      rememberResponse('csv-flow-curve', result)
      touchCharts()
      return result
    } catch (error) {
      handleError(error, 'CSV 人流曲线读取失败')
      throw error
    } finally {
      finishOperation('flow', controller, () => {
        loading.flow = false
      })
    }
  }

  async function runBaseline() {
    const activeSession = beginRunSession()
    loading.simulation = true
    requestStatus.value = 'RUNNING_SIMULATION'
    const controller = replaceController('simulation')
    try {
      const run = await runDashboardSimulation(
        buildSimulationPayload(),
        { signal: controller.signal },
      )
      if (activeSession !== sessionId) return null
      currentRun.value = run
      rememberResponse('simulation-run', run)
      touchCharts()
      await generateDiversion(activeSession)
      requestStatus.value = 'SIMULATION_COMPLETED'
      return run
    } catch (error) {
      handleError(error, '基础仿真运行失败')
      throw error
    } finally {
      finishOperation('simulation', controller, () => {
        loading.simulation = false
      })
    }
  }

  async function generateDiversion(activeSession = sessionId) {
    if (!currentRun.value) return null
    loading.diversion = true
    requestStatus.value = 'GENERATING_DIVERSION'
    const controller = replaceController('diversion')
    try {
      const result = await getDiversionSuggestions(
        {
          runId: currentRun.value.runId,
          minute: peakPoint.value?.minute,
          targetCrowdLevel: targetCrowdLevel.value,
          profile: buildProfilePayload(),
          strategyParameters: clone(strategyParameters),
        },
        { signal: controller.signal },
      )
      if (activeSession !== sessionId) return null
      diversionResult.value = result
      rememberResponse('diversion-suggestions', result)
      requestStatus.value = 'DIVERSION_READY'
      return result
    } catch (error) {
      handleError(error, '分流建议生成失败')
      throw error
    } finally {
      finishOperation('diversion', controller, () => {
        loading.diversion = false
      })
    }
  }

  async function runComparison() {
    if (!currentRun.value) return null
    const activeSession = sessionId
    loading.comparison = true
    requestStatus.value = 'RUNNING_COMPARISON'
    compareRun.value = null
    comparison.value = null
    const controller = replaceController('comparison')
    try {
      const result = await runDashboardComparison(
        {
          baseRunId: currentRun.value.runId,
          minute: peakPoint.value?.minute,
          targetCrowdLevel: targetCrowdLevel.value,
          autoRunCompare: true,
          strategyParameters: clone(strategyParameters),
        },
        { signal: controller.signal },
      )
      if (activeSession !== sessionId) return null
      let loadedCompareRun = null
      if (result.compareRunId) {
        loadedCompareRun = await getDashboardSimulation(
          result.compareRunId,
          { signal: controller.signal },
        )
      }
      if (activeSession !== sessionId) return null
      diversionResult.value = result.diversionResult || diversionResult.value
      comparison.value = result.comparison || null
      compareRun.value = loadedCompareRun
      rememberResponse('diversion-comparison', result)
      touchCharts()
      requestStatus.value = result.status || 'COMPARISON_COMPLETED'
      return result
    } catch (error) {
      handleError(error, '分流对比运行失败')
      throw error
    } finally {
      finishOperation('comparison', controller, () => {
        loading.comparison = false
      })
    }
  }

  async function startOptimization() {
    if (!currentRun.value) return null
    stopOptimizationPolling()
    abortOperation('optimization')
    optimizationJob.value = null
    optimizationIterations.value = []
    optimizationBest.value = null
    loading.optimization = true
    requestStatus.value = 'STARTING_OPTIMIZATION'
    const activeSession = sessionId
    const controller = replaceController('optimization')
    try {
      const job = await requestOptimization(
        {
          baseRunId: currentRun.value.runId,
          minute: peakPoint.value?.minute,
          targetCrowdLevel: targetCrowdLevel.value,
          iterationCount: optimizationSettings.iterationCount,
          randomSeed: optimizationSettings.randomSeed,
          initialParameters: clone(strategyParameters),
        },
        { signal: controller.signal },
      )
      if (activeSession !== sessionId) return null
      optimizationJob.value = job
      rememberResponse('optimization-start', job)
      requestStatus.value = 'OPTIMIZATION_RUNNING'
      pollOptimization(job.taskId, activeSession, controller)
      return job
    } catch (error) {
      if (isCanceledRequest(error)) return null
      finishOperation('optimization', controller, () => {
        loading.optimization = false
      })
      handleError(error, '优化任务启动失败')
      throw error
    }
  }

  async function pollOptimization(taskId, activeSession, controller) {
    try {
      const [job, iterations, best] = await Promise.all([
        getOptimizationJob(taskId, { signal: controller.signal }),
        getOptimizationIterations(taskId, { signal: controller.signal }),
        getOptimizationBest(taskId, { signal: controller.signal }),
      ])
      if (
        activeSession !== sessionId
        || controllers.get('optimization') !== controller
        || (optimizationJob.value?.taskId && optimizationJob.value.taskId !== taskId)
      ) {
        return
      }
      optimizationJob.value = job
      optimizationIterations.value = iterations || []
      optimizationBest.value = best
      rememberResponse('optimization-progress', {
        job,
        iterationCount: iterations?.length || 0,
        best,
      })
      touchCharts()
      if (job.status === 'COMPLETED' || job.status === 'FAILED') {
        finishOperation('optimization', controller, () => {
          loading.optimization = false
        })
        requestStatus.value =
          job.status === 'COMPLETED' ? 'OPTIMIZATION_COMPLETED' : 'OPTIMIZATION_FAILED'
        return
      }
      pollTimer = window.setTimeout(
        () => pollOptimization(taskId, activeSession, controller),
        900,
      )
    } catch (error) {
      if (
        isCanceledRequest(error)
        || activeSession !== sessionId
        || controllers.get('optimization') !== controller
      ) {
        return
      }
      finishOperation('optimization', controller, () => {
        loading.optimization = false
      })
      handleError(error, '优化进度读取失败')
    }
  }

  function beginRunSession() {
    sessionId += 1
    abortAll()
    stopOptimizationPolling()
    currentRun.value = null
    compareRun.value = null
    diversionResult.value = null
    comparison.value = null
    optimizationJob.value = null
    optimizationIterations.value = []
    optimizationBest.value = null
    lastError.value = null
    rawResponses.value = []
    lastChartUpdatedAt.value = null
    Object.keys(loading).forEach((key) => {
      loading[key] = false
    })
    return sessionId
  }

  function buildSimulationPayload() {
    return {
      profile: buildProfilePayload(),
      scenario: {
        mealPeriod: form.mealPeriod,
        dayType: form.dayType,
        crowdLevel: form.crowdLevel,
        weatherFactor: 1,
        eventFactor: 1,
        closedWindowIds: [],
        virtualUserCount: form.virtualUserCount,
        durationMinutes: form.durationMinutes,
        stepMinutes: 3,
        randomSeed: form.randomSeed,
      },
    }
  }

  function buildProfilePayload() {
    const preset = profiles.value.find((item) => item.userType === form.userType)
    return {
      userType: form.userType,
      tasteTags: preset?.tasteTags || [],
      budgetMin: preset?.budgetMin ?? 10,
      budgetMax: preset?.budgetMax ?? 22,
      waitingToleranceMinutes: preset?.waitingToleranceMinutes ?? 10,
    }
  }

  function applyAvailableDefaults() {
    const availableProfile = profiles.value.find((item) => item.userType === form.userType)
      || profiles.value[0]
    if (availableProfile) {
      form.userType = availableProfile.userType
    }
    const scenario = scenarios.value.find((item) => item.scenarioId === 'weekday-lunch-peak')
      || scenarios.value[0]
    if (scenario) {
      form.mealPeriod = scenario.mealPeriod
      form.dayType = scenario.dayType
      form.crowdLevel = scenario.crowdLevel
      form.virtualUserCount = scenario.virtualUserCount
      form.durationMinutes = scenario.durationMinutes
      form.randomSeed = scenario.randomSeed
    }
  }

  function replaceController(name) {
    abortOperation(name)
    const controller = new AbortController()
    controllers.set(name, controller)
    return controller
  }

  function abortOperation(name) {
    controllers.get(name)?.abort()
    controllers.delete(name)
  }

  function abortAll() {
    ;[...controllers.keys()].forEach(abortOperation)
  }

  function finishOperation(name, controller, callback) {
    if (controllers.get(name) !== controller) return
    controllers.delete(name)
    callback?.()
  }

  function stopOptimizationPolling(abortRequest = false) {
    if (pollTimer != null) {
      window.clearTimeout(pollTimer)
      pollTimer = null
    }
    if (abortRequest) {
      abortOperation('optimization')
      loading.optimization = false
    }
  }

  function handleError(error, fallback) {
    if (isCanceledRequest(error)) return
    lastError.value = {
      message: error?.message || fallback,
      status: error?.status || null,
      code: error?.code || null,
      traceId: error?.traceId || null,
      endpoint: error?.endpoint || null,
      occurredAt: new Date().toISOString(),
    }
    requestStatus.value = 'ERROR'
  }

  function rememberResponse(name, data) {
    let summary = ''
    try {
      summary = JSON.stringify(data)
    } catch {
      summary = String(data)
    }
    rawResponses.value = [
      {
        name,
        at: new Date().toISOString(),
        summary: summary.length > 1200 ? `${summary.slice(0, 1200)}...` : summary,
      },
      ...rawResponses.value,
    ].slice(0, 8)
  }

  function touchCharts() {
    lastChartUpdatedAt.value = new Date().toISOString()
  }

  return {
    profiles,
    scenarios,
    restaurants,
    windows,
    dataOverview,
    flowCurve,
    form,
    strategyParameters,
    optimizationSettings,
    currentRun,
    compareRun,
    diversionResult,
    comparison,
    optimizationJob,
    optimizationIterations,
    optimizationBest,
    loading,
    requestStatus,
    lastError,
    rawResponses,
    lastChartUpdatedAt,
    targetCrowdLevel,
    peakPoint,
    staleDataRisk,
    initialize,
    refreshFlowCurve,
    runBaseline,
    generateDiversion,
    runComparison,
    startOptimization,
    stopOptimizationPolling,
  }
})

function defaultStrategyParameters() {
  return {
    sourcePressureScale: 1,
    targetPressureBufferScale: 1,
    transferScale: 1,
    maxTransferCount: 60,
    acceptanceBias: 0,
    waitReductionWeight: 0.032,
  }
}

function clone(value) {
  return JSON.parse(JSON.stringify(value))
}
