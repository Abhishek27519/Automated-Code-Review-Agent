package com.codereview.agent.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "review")
public record ReviewProperties(
        @DefaultValue("20000") @Min(100) int maxInputChars,
        @DefaultValue("PROMPT") StructuredOutputMode structuredOutput,
        @DefaultValue("true") boolean validateSchema) {

    public enum StructuredOutputMode { PROMPT, NATIVE }
}