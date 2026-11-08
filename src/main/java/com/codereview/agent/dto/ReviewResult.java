package com.codereview.agent.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record ReviewResult(
        @JsonPropertyDescription("Overall quality score from 1 (dangerous) to 10 (production ready), per the scoring rubric")
        int overallScore,
        @JsonPropertyDescription("Two to four sentence overall assessment")
        String summary,
        @JsonPropertyDescription("Security findings; empty list if none")
        List<SecurityVulnerability> securityVulnerabilities,
        @JsonPropertyDescription("Performance problems and code smells; empty list if none")
        List<CodeSmell> performanceAndCodeSmells,
        @JsonPropertyDescription("Complete corrected code in the same language with all findings fixed, without line-number prefixes")
        String refactoredCode) {

    /** Claude may omit an empty list; normalise so callers never see null. */
    public ReviewResult {
        securityVulnerabilities = securityVulnerabilities == null ? List.of() : List.copyOf(securityVulnerabilities);
        performanceAndCodeSmells = performanceAndCodeSmells == null ? List.of() : List.copyOf(performanceAndCodeSmells);
    }
}