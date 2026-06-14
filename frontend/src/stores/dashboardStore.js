import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import {
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
import { getStudentPoolSummary, previewArrivalCurve } from '../api/v04'
import { resolvePeakTimePoint } from '../utils/simulationStats'

export const useDashboardStore = defineStore('diversion-dashboard', () => {
  const profiles = ref([])
  const scenarios = ref([])
  const restaurants = ref([])
  const windows = ref([])
  const dataOverview = ref(null)
  const flowCurve = ref(null)
  const flowCurveError = ref('')

  const form = reactive({
    userType: 'STUDENT',
    mealPeriod: 'LUNCH',
    dayType: 'WEEKDAY',
    crowdLevel: 'BUSY',
    weatherFactor: 1,
    eventFactor: 1,
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
  const optimizationBestRun = ref(null)
  const lastRunMeta = ref(loadDashboardLastRunMeta())

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
      const [options, overview, studentPool] = await Promise.all([
        getSimulationOptions({ signal: controller.signal }),
        getDataOverview({ signal: controller.signal }).catch(() => null),
        getStudentPoolSummary({ signal: controller.signal }).catch(() => null),
      ])
      profiles.value = options.profiles || []
      scenarios.value = options.scenarios || []
      restaurants.value = options.restaurants || []
      windows.value = options.windows || []
      dataOverview.value = overview || buildDataOverview(options, studentPool)
      applyAvailableDefaults()
      rememberResponse('data-overview', overview)
      await refreshFlowCurve()

      if (!currentRun.value) {
        await restoreLastRun()
      }

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
      const result = await previewArrivalCurve(buildSimulationPayload(), {
        signal: controller.signal,
      })
      flowCurve.value = result
      flowCurveError.value = ''
      rememberResponse('arrival-curve-preview', result)
      touchCharts()
      return result
    } catch (error) {
      if (isCanceledRequest(error)) return null
      flowCurveError.value = error?.message || '人流曲线预览加载失败'
      return null
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
      lastRunMeta.value = rememberDashboardRun(run)
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

  async function restoreLastRun() {
    const runId = lastRunMeta.value?.runId
    if (!runId) return null

    try {
      const run = await getDashboardSimulation(runId)
      currentRun.value = run
      requestStatus.value = 'SIMULATION_COMPLETED'
      touchCharts()

      try {
        await generateDiversion()
      } catch {
        // Keep restored simulation data even if diversion suggestions fail.
      }

      return run
    } catch {
      currentRun.value = null
      lastRunMeta.value = null
      clearDashboardRememberedRun()
      return null
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
    optimizationBestRun.value = null
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
      const optimizationId = resolveOptimizationJobId(job)
      if (!optimizationId) {
        throw new Error('优化任务启动成功，但响应中缺少 jobId')
      }
      optimizationJob.value = normalizeOptimizationJob(job)
      rememberResponse('optimization-start', job)
      requestStatus.value = 'OPTIMIZATION_RUNNING'
      pollOptimization(optimizationId, activeSession, controller)
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

  async function pollOptimization(jobId, activeSession, controller) {
    try {
      const [job, iterations, best] = await Promise.all([
        getOptimizationJob(jobId, { signal: controller.signal }),
        getOptimizationIterations(jobId, { signal: controller.signal }),
        getOptimizationBest(jobId, { signal: controller.signal }),
      ])
      const activeJobId = resolveOptimizationJobId(optimizationJob.value)
      if (
        activeSession !== sessionId
        || controllers.get('optimization') !== controller
        || (activeJobId && activeJobId !== jobId)
      ) {
        return
      }
      const normalizedBest = normalizeOptimizationBest(best)
      let loadedBestRun = optimizationBestRun.value
      if (
        normalizedBest?.compareRunId
        && Number(loadedBestRun?.runId) !== Number(normalizedBest.compareRunId)
      ) {
        try {
          loadedBestRun = await getDashboardSimulation(
            normalizedBest.compareRunId,
            { signal: controller.signal },
          )
        } catch (error) {
          if (isCanceledRequest(error)) return
          loadedBestRun = null
        }
      }
      if (
        activeSession !== sessionId
        || controllers.get('optimization') !== controller
      ) {
        return
      }
      const normalizedIterations = iterations || []
      const latestIteration = normalizedIterations.at(-1)
      optimizationJob.value = normalizeOptimizationJob({
        ...job,
        currentTemperature: job.currentTemperature ?? latestIteration?.temperature,
        currentLoss: job.currentLoss ?? latestIteration?.loss,
        currentParameters:
          job.currentParameters
          ?? latestIteration?.currentParameters
          ?? latestIteration?.strategyParameters,
      })
      optimizationIterations.value = normalizedIterations
      optimizationBest.value = normalizedBest
      optimizationBestRun.value = loadedBestRun
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
        () => pollOptimization(jobId, activeSession, controller),
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
    optimizationBestRun.value = null
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
        weatherFactor: form.weatherFactor,
        eventFactor: form.eventFactor,
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
      form.weatherFactor = scenario.weatherFactor ?? 1
      form.eventFactor = scenario.eventFactor ?? 1
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
    flowCurveError,
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
    optimizationBestRun,
    loading,
    requestStatus,
    lastError,
    rawResponses,
    lastChartUpdatedAt,
    targetCrowdLevel,
    peakPoint,
    staleDataRisk,
    lastRunMeta,
    initialize,
    refreshFlowCurve,
    runBaseline,
    restoreLastRun,
    generateDiversion,
    runComparison,
    startOptimization,
    stopOptimizationPolling,
  }
})

function resolveOptimizationJobId(job) {
  return job?.jobId ?? job?.taskId ?? null
}

function normalizeOptimizationJob(job) {
  if (!job) return job
  const jobId = resolveOptimizationJobId(job)
  if (!jobId) return job
  return {
    ...job,
    jobId,
    taskId: job.taskId ?? jobId,
  }
}

function normalizeOptimizationBest(result) {
  if (!result?.evaluation) return result
  const evaluation = result.evaluation
  const jobId = result.jobId ?? result.taskId ?? null
  return {
    ...evaluation,
    jobId,
    taskId: result.taskId ?? jobId,
    parameters: evaluation.strategyParameters,
    metrics: evaluation.compareMetrics,
  }
}

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

function buildDataOverview(options, studentPool) {
  const windows = options?.windows || []
  return {
    sourceDirectory: 'data/task-a',
    studentCount: studentPool?.totalStudents ?? null,
    restaurantCount: options?.restaurants?.length ?? 0,
    windowCount: windows.length,
    openWindowCount: windows.filter(
      (window) =>
        window?.status !== 'CLOSED' &&
        window?.open !== false &&
        window?.isOpen !== false,
    ).length,
    userTypes: Object.keys(studentPool?.userTypeCounts || {}),
  }
}

function rememberDashboardRun(run) {
  const meta = {
    runId: run?.runId,
    createdAt: run?.createdAt || new Date().toISOString(),
  }
  if (typeof window !== 'undefined') {
    window.localStorage.setItem('bjtu-dashboard-last-run', JSON.stringify(meta))
  }
  return meta
}

function loadDashboardLastRunMeta() {
  if (typeof window === 'undefined') return null
  const rawCandidates = [
    window.localStorage.getItem('bjtu-dashboard-last-run'),
    window.localStorage.getItem('bjtu-dining-last-run'),
  ]

  for (const raw of rawCandidates) {
    if (!raw) continue
    try {
      const parsed = JSON.parse(raw)
      if (parsed?.runId) {
        return parsed
      }
    } catch {
      continue
    }
  }

  return null
}

function clearDashboardRememberedRun() {
  if (typeof window === 'undefined') return
  window.localStorage.removeItem('bjtu-dashboard-last-run')
}
