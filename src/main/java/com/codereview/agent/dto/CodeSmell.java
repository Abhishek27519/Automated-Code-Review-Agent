package com.codereview.agent.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record CodeSmell(
        @JsonPropertyDescription("The performance problem or code smell, naming the construct involved")
        String issue,
        @JsonPropertyDescription("Why it matters: runtime cost, failure mode or maintainability harm")
        String impact,
        @JsonPropertyDescription("Concrete fix")
        String suggestedFix) {
}