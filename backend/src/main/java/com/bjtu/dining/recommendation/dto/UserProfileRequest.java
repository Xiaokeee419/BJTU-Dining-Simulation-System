package com.bjtu.dining.recommendation.dto;

import java.util.List;
import jakarta.validation.constraints.NotBlank;

public record UserProfileRequest(
        @NotBlank(message = "userType cannot be blank")
        String userType,

        List<String> tasteTags,

        Double budgetMin,

        Double budgetMax,

        Integer waitingToleranceMinutes
) {
}
