package com.bjtu.dining.taska.service;

import com.bjtu.dining.common.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SeedDataServiceTest {

    @Autowired
    private SeedDataService seedDataService;

    @Test
    void flowCurveKeepsContinuousBucketsAndCumulativeTotal() {
        var curve = seedDataService.flowCurve("LUNCH", 3);

        assertThat(curve.points()).isNotEmpty();
        assertThat(curve.points().get(0).minute()).isZero();
        for (int index = 1; index < curve.points().size(); index++) {
            assertThat(curve.points().get(index).minute() - curve.points().get(index - 1).minute())
                    .isEqualTo(3);
        }
        assertThat(curve.points()).anyMatch(point -> point.arrivals() == 0);
        assertThat(curve.points().get(curve.points().size() - 1).cumulativeArrivals())
                .isEqualTo(seedDataService.dataOverview().studentCount());
    }

    @Test
    void flowCurveRejectsUnsupportedBucketSize() {
        assertThatThrownBy(() -> seedDataService.flowCurve("LUNCH", 0))
                .isInstanceOf(BadRequestException.class);
    }
}
