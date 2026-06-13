package com.bjtu.dining.recommendation.service;

import com.bjtu.dining.common.ApiException;
import com.bjtu.dining.recommendation.dto.DiversionRequest;
import com.bjtu.dining.recommendation.dto.RecommendationGenerateRequest;
import com.bjtu.dining.recommendation.dto.StrategyCompareRequest;
import com.bjtu.dining.recommendation.dto.UserProfileRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RecommendationServiceTest {

    @Autowired
    private RecommendationService recommendationService;

    @Test
    void generateReturnsRestaurantWindowAndDishRecommendations() {
        var request = new RecommendationGenerateRequest(
                10001L,
                30,
                new UserProfileRequest("STUDENT", List.of("偏辣", "米饭"), 10.0, 20.0, 10),
                3
        );

        var result = recommendationService.generate(request);

        assertThat(result.runId()).isEqualTo(10001L);
        assertThat(result.minute()).isEqualTo(30);
        assertThat(result.restaurants()).hasSize(3);
        assertThat(result.windows()).hasSize(3);
        assertThat(result.dishes()).hasSize(3);
        assertThat(result.windows()).isSortedAccordingTo((a, b) -> Double.compare(b.score(), a.score()));
        assertThat(result.windows().get(0).rank()).isEqualTo(1);
        assertThat(result.windows().get(0).scoreBreakdown()).containsKeys("taste", "budget", "wait", "crowd", "popularity");
        assertThat(result.windows().get(0).matchedTags()).contains("米饭");
        assertThat(result.dishes().get(0).reason()).contains("预计");
        assertThat(result.diversionSuggestion()).isNotBlank();
    }

    @Test
    void generateAppliesDifferentUserTypeWeights() {
        var hurry = recommendationService.generate(new RecommendationGenerateRequest(
                10001L,
                30,
                new UserProfileRequest("HURRY", List.of("辣味", "米饭"), 10.0, 22.0, 6),
                5
        ));
        var budgetSensitive = recommendationService.generate(new RecommendationGenerateRequest(
                10001L,
                30,
                new UserProfileRequest("BUDGET_SENSITIVE", List.of("辣味", "米饭"), 10.0, 22.0, 6),
                5
        ));

        assertThat(hurry.windows().get(0).score()).isNotEqualTo(budgetSensitive.windows().get(0).score());
        assertThat(hurry.windows().get(0).scoreBreakdown().get("wait"))
                .isGreaterThan(hurry.windows().get(0).scoreBreakdown().get("budget"));
        assertThat(budgetSensitive.windows().get(0).scoreBreakdown().get("budget"))
                .isGreaterThan(budgetSensitive.windows().get(0).scoreBreakdown().get("wait"));
    }

    @Test
    void getGeneratedReturnsLatestGeneratedResult() {
        var request = new RecommendationGenerateRequest(
                10002L,
                null,
                new UserProfileRequest("HURRY", List.of("快餐", "米饭"), 8.0, 22.0, 8),
                2
        );

        var generated = recommendationService.generate(request);
        var found = recommendationService.getGenerated(10002L, null);

        assertThat(found).isEqualTo(generated);
        assertThat(found.windows()).hasSize(2);
    }

    @Test
    void generateUsesLatestMinuteAndDefaultLimit() {
        var request = new RecommendationGenerateRequest(
                10003L,
                null,
                new UserProfileRequest("STUDENT", List.of("米饭"), 10.0, 20.0, 10),
                null
        );

        var result = recommendationService.generate(request);

        assertThat(result.minute()).isEqualTo(30);
        assertThat(result.restaurants()).hasSize(3);
        assertThat(result.windows()).hasSize(3);
        assertThat(result.dishes()).hasSize(3);
    }

    @Test
    void getGeneratedRejectsMissingResult() {
        assertThatThrownBy(() -> recommendationService.getGenerated(90909L, 30))
                .isInstanceOf(ApiException.class)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.code()).isEqualTo(40400));
    }

    @Test
    void generateRejectsInvalidLimit() {
        var request = new RecommendationGenerateRequest(
                10001L,
                30,
                new UserProfileRequest("STUDENT", List.of("偏辣"), 10.0, 20.0, 10),
                11
        );

        assertThatThrownBy(() -> recommendationService.generate(request))
                .isInstanceOf(ApiException.class)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.code()).isEqualTo(40001));
    }

    @Test
    void generateRejectsInvalidBudgetRange() {
        var request = new RecommendationGenerateRequest(
                10001L,
                30,
                new UserProfileRequest("STUDENT", List.of("偏辣"), 25.0, 10.0, 10),
                3
        );

        assertThatThrownBy(() -> recommendationService.generate(request))
                .isInstanceOf(ApiException.class)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.code()).isEqualTo(40001));
    }

    @Test
    void generateDiversionReturnsSuggestionsForCrowdedWindows() {
        var result = recommendationService.generateDiversion(new DiversionRequest(
                10001L,
                30,
                "NORMAL",
                new UserProfileRequest("HURRY", List.of("辣味", "米饭"), 10.0, 22.0, 8)
        ));

        assertThat(result.runId()).isEqualTo(10001L);
        assertThat(result.minute()).isEqualTo(30);
        assertThat(result.reason()).contains("分流建议");
        assertThat(result.suggestions()).isNotEmpty();
        assertThat(result.suggestions().get(0).suggestedUserCount()).isPositive();
        assertThat(result.suggestions().get(0).acceptanceRate()).isBetween(0.0, 1.0);
        assertThat(result.suggestions().get(0).estimatedAcceptedCount()).isPositive();
        assertThat(result.suggestions().get(0).estimatedWaitReduction()).isPositive();
        assertThat(result.suggestions().get(0).tagSimilarity()).isBetween(0.0, 1.0);
        assertThat(result.suggestions().get(0).reason()).contains("用户接受分流");
    }

    @Test
    void generateDiversionRejectsInvalidTargetCrowdLevel() {
        assertThatThrownBy(() -> recommendationService.generateDiversion(new DiversionRequest(10001L, 30, "HOT", null)))
                .isInstanceOf(ApiException.class)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.code()).isEqualTo(40002));
    }

    @Test
    void compareStrategiesReturnsMetricDeltasAndConclusion() {
        var result = recommendationService.compareStrategies(
                new StrategyCompareRequest(10001L, 10002L, "DIVERSION_AFTER")
        );

        assertThat(result.baseRunId()).isEqualTo(10001L);
        assertThat(result.compareRunId()).isEqualTo(10002L);
        assertThat(result.compareType()).isEqualTo("DIVERSION_AFTER");
        assertThat(result.avgWaitDelta()).isLessThan(0);
        assertThat(result.maxWaitDelta()).isLessThanOrEqualTo(0);
        assertThat(result.maxQueueDelta()).isLessThan(0);
        assertThat(result.busyWindowCountDelta()).isLessThan(0);
        assertThat(result.extremeWindowCountDelta()).isLessThanOrEqualTo(0);
        assertThat(result.servedUserCountDelta()).isGreaterThanOrEqualTo(0);
        assertThat(result.unservedUserCountDelta()).isLessThanOrEqualTo(0);
        assertThat(result.conclusion())
                .contains("\u6267\u884c\u5206\u6d41\u5efa\u8bae\u540e")
                .contains("\u5206\u6d41\u7b56\u7565");
    }

    @Test
    void compareStrategiesDefaultsCompareTypeToScenario() {
        var result = recommendationService.compareStrategies(
                new StrategyCompareRequest(10001L, 10002L, null)
        );

        assertThat(result.compareType()).isEqualTo("SCENARIO");
    }

    @Test
    void compareStrategiesRejectsMissingRunId() {
        assertThatThrownBy(() -> recommendationService.compareStrategies(new StrategyCompareRequest(0L, 10002L, null)))
                .isInstanceOf(ApiException.class)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.code()).isEqualTo(40401));
    }
}
