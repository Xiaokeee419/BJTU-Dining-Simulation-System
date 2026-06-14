package com.bjtu.dining.taska.service;

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
    private static final Map<String, CurveProfile> CURVES = Map.of(
            "BREAKFAST",
            new CurveProfile(
                    35,
                    0.12,
                    List.of(new Peak(35, 9, 1.0))
            ),
            "LUNCH",
            new CurveProfile(
                    38,
                    0.10,
                    List.of(
                            new Peak(30, 7, 0.48),
                            new Peak(50, 8, 0.42),
                            new Peak(39, 14, 0.10)
                    )
            ),
            "DINNER",
            new CurveProfile(
                    46,
                    0.10,
                    List.of(
                            new Peak(38, 12, 0.58),
                            new Peak(58, 15, 0.32),
                            new Peak(28, 12, 0.10)
                    )
            )
    );

    public int generateArrivalMinute(
            int baseArrivalMinute,
            String mealPeriod,
            String dayType,
            String crowdLevel,
            int durationMinutes,
            double pressure,
            Random rng
    ) {
        CurveProfile profile = CURVES.getOrDefault(mealPeriod, CURVES.get("LUNCH"));
        Peak peak = choosePeak(profile.peaks(), rng.nextDouble());
        double dayShift = "WEEKEND".equals(dayType) ? 8.0 : 0.0;
        double daySpread = "WEEKEND".equals(dayType) ? 1.22 : 1.0;
        double pressureFactor = Math.sqrt(Math.max(0.55, pressure));
        double spread = peak.standardDeviation()
                * CROWD_SPREAD_FACTOR.getOrDefault(crowdLevel, 1.0)
                * daySpread
                / pressureFactor;
        double baseBias = (baseArrivalMinute - profile.baseReferenceMinute()) * profile.baseInfluence();
        double arrival = peak.centerMinute() + dayShift + baseBias + rng.nextGaussian() * spread;
        return clamp((int) Math.round(arrival), 0, durationMinutes);
    }

    public List<Integer> generateArrivalMinutes(
            String mealPeriod,
            String dayType,
            String crowdLevel,
            int durationMinutes,
            double pressure,
            int dinerCount,
            Random rng
    ) {
        double[] weights = buildMinuteWeights(
                mealPeriod,
                dayType,
                crowdLevel,
                durationMinutes,
                pressure
        );
        double totalWeight = 0.0;
        for (double weight : weights) {
            totalWeight += weight;
        }
        if (totalWeight <= 0.0 || dinerCount <= 0) {
            return List.of();
        }

        List<Integer> arrivalMinutes = new ArrayList<>(dinerCount);
        for (int index = 0; index < dinerCount; index++) {
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
            String mealPeriod,
            String dayType,
            String crowdLevel,
            int durationMinutes,
            double pressure
    ) {
        CurveProfile profile = CURVES.getOrDefault(mealPeriod, CURVES.get("LUNCH"));
        int horizon = Math.max(0, durationMinutes);
        double[] weights = new double[horizon + 1];
        double dayShift = "WEEKEND".equals(dayType) ? 8.0 : 0.0;
        double daySpread = "WEEKEND".equals(dayType) ? 1.22 : 1.0;
        double pressureFactor = Math.sqrt(Math.max(0.55, pressure));
        double spreadFactor = CROWD_SPREAD_FACTOR.getOrDefault(crowdLevel, 1.0)
                * daySpread
                / pressureFactor;

        for (int minute = 0; minute <= horizon; minute++) {
            double minuteWeight = 0.0;
            for (Peak peak : profile.peaks()) {
                double center = peak.centerMinute() + dayShift;
                double spread = Math.max(1.0, peak.standardDeviation() * spreadFactor);
                double distance = minute - center;
                minuteWeight += peak.weight() * Math.exp(-0.5 * distance * distance / (spread * spread));
            }
            weights[minute] = minuteWeight;
        }
        return weights;
    }

    private Peak choosePeak(List<Peak> peaks, double ticket) {
        double cumulative = 0.0;
        for (Peak peak : peaks) {
            cumulative += peak.weight();
            if (ticket <= cumulative) {
                return peak;
            }
        }
        return peaks.get(peaks.size() - 1);
    }

    private int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private record CurveProfile(
            int baseReferenceMinute,
            double baseInfluence,
            List<Peak> peaks
    ) {
    }

    private record Peak(int centerMinute, int standardDeviation, double weight) {
    }
}
