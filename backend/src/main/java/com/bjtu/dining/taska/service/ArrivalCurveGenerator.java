package com.bjtu.dining.taska.service;

import com.bjtu.dining.taska.service.SeedDataService.ArrivalPeakSeed;
import com.bjtu.dining.taska.service.SeedDataService.ArrivalRuleSeed;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.stereotype.Service;

@Service
public class ArrivalCurveGenerator {
    private static final Map<String, Double> CROWD_SPREAD_FACTOR = Map.of(
            "IDLE", 1.30,
            "NORMAL", 1.05,
            "BUSY", 0.82,
            "EXTREME", 0.68
    );

    public List<Integer> generateArrivalMinutes(
            ArrivalRuleSeed rule,
            String dayType,
            String crowdLevel,
            int durationMinutes,
            double pressure,
            int dinerCount,
            Random rng
    ) {
        double[] weights = buildMinuteWeights(rule, dayType, crowdLevel, durationMinutes, pressure);
        double totalWeight = 0.0;
        for (double weight : weights) {
            totalWeight += weight;
        }
        if (totalWeight <= 0.0) {
            return List.of();
        }

        List<Integer> arrivalMinutes = new ArrayList<>(dinerCount);
        for (int i = 0; i < dinerCount; i++) {
            double threshold = rng.nextDouble() * totalWeight;
            double cumulative = 0.0;
            int selectedMinute = weights.length - 1;
            for (int minute = 0; minute < weights.length; minute++) {
                cumulative += weights[minute];
                if (threshold <= cumulative) {
                    selectedMinute = minute;
                    break;
                }
            }
            arrivalMinutes.add(selectedMinute);
        }
        arrivalMinutes.sort(Comparator.naturalOrder());
        return List.copyOf(arrivalMinutes);
    }

    public double[] buildMinuteWeights(
            ArrivalRuleSeed rule,
            String dayType,
            String crowdLevel,
            int durationMinutes,
            double pressure
    ) {
        int horizon = Math.max(0, durationMinutes);
        double[] weights = new double[horizon + 1];
        if (rule == null || rule.peaks().isEmpty()) {
            return weights;
        }
        double dayShift = "WEEKEND".equals(dayType) ? 8.0 : 0.0;
        double daySpread = "WEEKEND".equals(dayType) ? 1.22 : 1.0;
        double pressureFactor = Math.sqrt(Math.max(0.55, pressure));
        double spreadFactor = CROWD_SPREAD_FACTOR.getOrDefault(crowdLevel, 1.0) * daySpread / pressureFactor;

        for (int minute = 0; minute <= horizon; minute++) {
            double minuteWeight = 0.0;
            for (ArrivalPeakSeed peak : rule.peaks()) {
                double center = peak.centerMinute() + dayShift;
                double spread = Math.max(1.0, peak.standardDeviation() * spreadFactor);
                double distance = minute - center;
                minuteWeight += peak.weight() * Math.exp(-0.5 * distance * distance / (spread * spread));
            }
            weights[minute] = minuteWeight;
        }
        return weights;
    }
}
