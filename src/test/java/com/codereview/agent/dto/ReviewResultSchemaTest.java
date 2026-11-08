package com.codereview.agent.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

class ReviewResultSchemaTest {

    @Test
    void schemaCarriesDescriptionsAndSeverityEnum() {
        var converter = new BeanOutputConverter<>(ReviewResult.class);

        System.out.println(converter.getJsonSchema());
        System.out.println(converter.getFormat());

        assertThat(converter.getJsonSchema())
                .contains("securityVulnerabilities")
                .contains("CRITICAL")
                .contains("Overall quality score");
    }
}