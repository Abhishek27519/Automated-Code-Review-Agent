package com.codereview.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.util.StringUtils;

public record ReviewRequest(
        @NotBlank(message = "language is required")
        @Pattern(regexp = "^[A-Za-z0-9+#. -]{1,30}$", message = "language must be 1-30 letters, digits or + # . -")
        String language,
        String codeSnippet,
        String gitDiff) {

    @JsonIgnore
    @AssertTrue(message = "Provide exactly one of 'codeSnippet' or 'gitDiff'")
    public boolean isExactlyOneInputProvided() {
        return StringUtils.hasText(codeSnippet) ^ StringUtils.hasText(gitDiff);
    }

    /** The text to review, whichever field was supplied. */
    public String content() {
        return StringUtils.hasText(codeSnippet) ? codeSnippet : gitDiff;
    }

    public boolean isDiff() {
        return !StringUtils.hasText(codeSnippet);
    }
}