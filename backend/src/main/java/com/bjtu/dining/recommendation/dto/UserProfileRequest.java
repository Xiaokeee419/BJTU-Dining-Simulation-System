package com.bjtu.dining.recommendation.dto;

import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserProfileRequest(
        @NotBlank(message = "userType 不能为空")
        String userType,

        List<String> tasteTags,

        @NotNull(message = "budgetMin 不能为空")
        @DecimalMin(value = "0.0", message = "budgetMin 必须大于等于 0")
        Double budgetMin,

        @NotNull(message = "budgetMax 不能为空")
        @DecimalMin(value = "0.0", message = "budgetMax 必须大于等于 0")
        Double budgetMax,

        @NotNull(message = "waitingToleranceMinutes 不能为空")
        @Min(value = 0, message = "waitingToleranceMinutes 必须大于等于 0")
        @Max(value = 120, message = "waitingToleranceMinutes 必须小于等于 120")
        Integer waitingToleranceMinutes
) {
}
