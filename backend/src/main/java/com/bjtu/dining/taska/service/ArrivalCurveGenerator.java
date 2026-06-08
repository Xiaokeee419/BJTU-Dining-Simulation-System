package com.bjtu.dining.taska.service;

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
