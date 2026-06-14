package com.bjtu.dining.recommendation.service;

import com.bjtu.dining.recommendation.model.SimulationRunResult;

public interface SimulationProvider {

    SimulationRunResult findByRunId(Long runId);
}
