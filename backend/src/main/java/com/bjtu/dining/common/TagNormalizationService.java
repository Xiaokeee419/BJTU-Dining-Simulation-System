package com.bjtu.dining.common;

import com.bjtu.dining.taska.service.SeedDataService;
import com.bjtu.dining.taska.service.SeedDataService.SeedData;
import com.bjtu.dining.taska.service.SeedDataService.TagKeywordMappingSeed;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TagNormalizationService {

    private final SeedDataService seedDataService;

    public TagNormalizationService(SeedDataService seedDataService) {
        this.seedDataService = seedDataService;
    }

    public Set<String> normalize(List<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            normalized.addAll(normalizePipeSeparated(tag));
        }
        return Set.copyOf(normalized);
    }

    public Set<String> normalize(Collection<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            normalized.addAll(normalizePipeSeparated(tag));
        }
        return Set.copyOf(normalized);
    }

    public Set<String> normalize(String tags) {
        if (tags == null || tags.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(normalizePipeSeparated(tags));
    }

    public double matchScore(Set<String> preferredTags, String candidateTags) {
        if (preferredTags == null || preferredTags.isEmpty()) {
            return 55.0;
        }
        Set<String> candidate = normalize(candidateTags);
        long matched = preferredTags.stream().filter(candidate::contains).count();
        if (matched == 0) {
            return 25.0;
        }
        return Math.min(100.0, 45.0 + matched * 55.0 / preferredTags.size());
    }

    private Set<String> normalizePipeSeparated(String rawTags) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        Arrays.stream(rawTags.split("\\|"))
                .map(item -> item == null ? "" : item.trim())
                .filter(item -> !item.isBlank())
                .forEach(item -> normalized.addAll(normalizeOne(item)));
        return normalized;
    }

    private Set<String> normalizeOne(String raw) {
        SeedData seedData = seedDataService.seedData();
        Set<String> exact = seedData.exactTagMappings().get(raw);
        if (exact != null && !exact.isEmpty()) {
            return exact;
        }
        LinkedHashSet<String> keywordMatches = new LinkedHashSet<>();
        for (TagKeywordMappingSeed keywordMapping : seedData.keywordTagMappings()) {
            if (raw.contains(keywordMapping.keyword())) {
                keywordMatches.addAll(keywordMapping.normalizedTags());
            }
        }
        if (!keywordMatches.isEmpty()) {
            return Set.copyOf(keywordMatches);
        }
        return Set.of(raw);
    }
}
