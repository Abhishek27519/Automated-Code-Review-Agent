package com.codereview.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codereview.agent.config.ReviewProperties;
import com.codereview.agent.config.ReviewProperties.StructuredOutputMode;
import com.codereview.agent.dto.ReviewRequest;
import com.codereview.agent.dto.ReviewResult;
import com.codereview.agent.dto.SecurityVulnerability;
import com.codereview.agent.dto.Severity;
import com.codereview.agent.exception.ReviewAnalysisException;
import com.codereview.agent.exception.ReviewAnalysisException.Reason;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.core.io.ClassPathResource;

/** No Spring context, no network, no API credits: the whole ChatClient fluent chain is mocked. */
class CodeReviewServiceTest {

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);

        // chatClient.prompt().user(text).call().responseEntity(...)
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
    }

    // ---- the two headline tests ----------------------------------------------

    @Test
    void returnsStructuredReview_whenClaudeResponds() {
        ReviewResult expected = review(2, Severity.CRITICAL);
        claudeReturns(expected);

        ReviewResult actual = service(20_000).review(snippet("String q = \"x\" + name;"));

        assertThat(actual).isEqualTo(expected);
        assertThat(actual.securityVulnerabilities()).hasSize(1);
        assertThat(actual.securityVulnerabilities().get(0).cweId()).isEqualTo("CWE-89");
    }

    @Test
    void wrapsUnparseableModelOutput_inReviewAnalysisException() {
        when(callSpec.responseEntity(eq(ReviewResult.class), any()))
                .thenThrow(new IllegalStateException("Unexpected end-of-input"));

        assertThatThrownBy(() -> service(20_000).review(snippet("int x = 1;")))
                .isInstanceOf(ReviewAnalysisException.class)
                .hasFieldOrPropertyWithValue("reason", Reason.MODEL_OUTPUT_INVALID);
    }

    // ---- guards and guardrails -----------------------------------------------

    @Test
    void rejectsOversizedInput_withoutCallingClaude() {
        assertThatThrownBy(() -> service(10).review(snippet("a".repeat(11))))
                .isInstanceOf(ReviewAnalysisException.class)
                .hasFieldOrPropertyWithValue("reason", Reason.INPUT_TOO_LARGE);

        verifyNoInteractions(chatClient);
    }

    @Test
    void capsScoreAt3_whenCriticalFindingIsPresent() {
        claudeReturns(review(9, Severity.CRITICAL));

        ReviewResult result = service(20_000).review(snippet("int x = 1;"));

        assertThat(result.overallScore()).isEqualTo(3);
    }

    @Test
    void rejectsScoreOutsideOneToTen() {
        claudeReturns(review(11, null));

        assertThatThrownBy(() -> service(20_000).review(snippet("int x = 1;")))
                .isInstanceOf(ReviewAnalysisException.class)
                .hasFieldOrPropertyWithValue("reason", Reason.MODEL_OUTPUT_INVALID);
    }

    // ---- prompt construction -------------------------------------------------

    @Test
    void sendsNumberedCodeAndPreservesBraces() {
        claudeReturns(review(10, null));
        String code = "if (ok) {\n    return \"{name}\";\n}";

        service(20_000).review(snippet(code));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(prompt.capture());
        assertThat(prompt.getValue())
                .contains("code snippet written in java")
                .contains("1| if (ok) {")
                .contains("2|     return \"{name}\";")
                .contains("3| }")
                .containsPattern("(?s)BEGIN (REVIEW-[0-9a-f-]{36}).*END \\1");
    }

    @Test
    void sendsGitDiffsWithoutLineNumberPrefixes() {
        claudeReturns(review(8, null));
        String diff = "@@ -1,2 +1,2 @@\n-old line\n+new line";

        service(20_000).review(new ReviewRequest("java", null, diff));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(prompt.capture());
        assertThat(prompt.getValue().replace("\r\n", "\n"))
                .contains("git diff written in java")
                .contains(diff)
                .doesNotContain("1| ");
    }

    @Test
    void numbersLinesAndNormalisesWindowsLineEndings() {
        assertThat(CodeReviewService.withLineNumbers("a\r\nb\n")).isEqualTo("1| a\n2| b\n3| ");
    }

    // ---- helpers -------------------------------------------------------------

    private CodeReviewService service(int maxInputChars) {
        var properties = new ReviewProperties(maxInputChars, StructuredOutputMode.PROMPT, false);
        return new CodeReviewService(chatClient, properties,
                new ClassPathResource("prompts/user-code-review.st"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void claudeReturns(ReviewResult result) {
        ResponseEntity responseEntity = mock(ResponseEntity.class);
        when(responseEntity.entity()).thenReturn(result);
        when(callSpec.responseEntity(eq(ReviewResult.class), any())).thenReturn(responseEntity);
    }

    private static ReviewRequest snippet(String code) {
        return new ReviewRequest("java", code, null);
    }

    private static ReviewResult review(int score, Severity severity) {
        List<SecurityVulnerability> vulnerabilities = severity == null
                ? List.of()
                : List.of(new SecurityVulnerability(severity, "CWE-89", "1", "desc", "fix"));
        return new ReviewResult(score, "Summary", vulnerabilities, List.of(), "fixed code");
    }
}