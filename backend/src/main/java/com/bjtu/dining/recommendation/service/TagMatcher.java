package com.bjtu.dining.recommendation.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class TagMatcher {

    private TagMatcher() {
    }

    static Set<String> normalize(List<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        return tags.stream()
                .flatMap(tag -> Arrays.stream(tag.split("\\|")))
                .map(TagMatcher::normalizeOne)
                .filter(tag -> !tag.isBlank())
                .collect(Collectors.toSet());
    }

    static Set<String> normalize(String tags) {
        if (tags == null || tags.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(tags.split("\\|"))
                .map(TagMatcher::normalizeOne)
                .filter(tag -> !tag.isBlank())
                .collect(Collectors.toSet());
    }

    static double matchScore(Set<String> preferredTags, String candidateTags) {
        if (preferredTags.isEmpty()) {
            return 55.0;
        }
        Set<String> candidate = normalize(candidateTags);
        long matched = preferredTags.stream().filter(candidate::contains).count();
        if (matched == 0) {
            return 25.0;
        }
        return Math.min(100.0, 45.0 + matched * 55.0 / preferredTags.size());
    }

    private static String normalizeOne(String raw) {
        String tag = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (tag.isBlank()) {
            return "";
        }
        if (tag.contains("辣")) {
            return "辣味";
        }
        if (tag.contains("米线") || tag.contains("米粉") || tag.contains("粉")) {
            return "粉面";
        }
        if (tag.contains("面")) {
            return "面食";
        }
        if (tag.contains("饭") || tag.contains("米饭")) {
            return "米饭";
        }
        if (tag.contains("甜")) {
            return "甜口";
        }
        if (tag.contains("清淡") || tag.contains("少油")) {
            return "清淡";
        }
        if (tag.contains("健康") || tag.contains("轻食")) {
            return "健康";
        }
        if (tag.contains("清真")) {
            return "清真";
        }
        if (tag.contains("素")) {
            return "素食";
        }
        return tag;
    }
}
