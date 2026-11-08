package com.codereview.agent.service;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.codereview.agent.config.ReviewProperties;
import com.codereview.agent.config.ReviewProperties.StructuredOutputMode;
import com.codereview.agent.dto.ReviewRequest;
import com.codereview.agent.dto.ReviewResult;
import com.codereview.agent.dto.Severity;
import com.codereview.agent.exception.ReviewAnalysisException;
import com.codereview.agent.exception.ReviewAnalysisException.Reason;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CodeReviewService {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewService.class);

    private final ChatClient chatClient;
    private final ReviewProperties properties;
    private final String userPromptTemplate;

    public CodeReviewService(ChatClient chatClient,
            ReviewProperties properties,
            @Value("classpath:/prompts/user-code-review.st") Resource userPrompt) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.userPromptTemplate = readResource(userPrompt);
    }

    public ReviewResult review(ReviewRequest request) {
        String content = request.content();
        if (content.length() > properties.maxInputChars()) {
            throw new ReviewAnalysisException(Reason.INPUT_TOO_LARGE,
                    "Input has %d characters; the limit is %d."
                            .formatted(content.length(), properties.maxInputChars()));
        }

        String userText = renderUserPrompt(request);
        long startNanos = System.nanoTime();

        ReviewResult result;
        ChatResponse chatResponse;
        try {
            var responseEntity = chatClient.prompt()
                    .user(userText)
                    .call()
                    .responseEntity(ReviewResult.class, this::configureStructuredOutput);
            result = responseEntity.entity();
            chatResponse = responseEntity.response();
        }
        catch (RuntimeException ex) {
            throw translate(ex);
        }

        logUsage(chatResponse, startNanos);
        return enforceGuardrails(result);
    }

    // ---- prompt construction -------------------------------------------------

    private String renderUserPrompt(ReviewRequest request) {
        boolean diff = request.isDiff();
        String body = diff ? request.content() : withLineNumbers(request.content());
        return PromptTemplate.builder()
                .template(userPromptTemplate)
                .build()
                .render(Map.of(
                        "inputType", diff ? "git diff" : "code snippet",
                        "language", request.language().trim(),
                        "boundary", "REVIEW-" + UUID.randomUUID(),
                        "content", body));
    }

    /** LLMs count lines poorly, so we number them deterministically. */
    static String withLineNumbers(String code) {
        String[] lines = code.split("\\R", -1);
        StringBuilder sb = new StringBuilder(code.length() + lines.length * 6);
        for (int i = 0; i < lines.length; i++) {
            sb.append(i + 1).append("| ").append(lines[i]);
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private void configureStructuredOutput(ChatClient.EntityParamSpec spec) {
        if (properties.structuredOutput() == StructuredOutputMode.NATIVE) {
            spec.useProviderStructuredOutput();
        }
        if (properties.validateSchema()) {
            spec.validateSchema();
        }
    }

    // ---- error translation ---------------------------------------------------

    private ReviewAnalysisException translate(RuntimeException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof AnthropicServiceException serviceError) {
                return new ReviewAnalysisException(reasonForStatus(serviceError.statusCode()), ex);
            }
            if (t instanceof AnthropicException) {
                return new ReviewAnalysisException(Reason.UPSTREAM_UNAVAILABLE, ex);
            }
        }
        // Nothing from the provider SDK: the call worked but the output could not be mapped.
        return new ReviewAnalysisException(Reason.MODEL_OUTPUT_INVALID, ex);
    }

    private static Reason reasonForStatus(int status) {
        return switch (status) {
            case 429 -> Reason.UPSTREAM_RATE_LIMITED;
            case 401, 403 -> Reason.UPSTREAM_AUTH_ERROR;
            case 408, 504 -> Reason.UPSTREAM_TIMEOUT;
            default -> status >= 500 ? Reason.UPSTREAM_UNAVAILABLE : Reason.UPSTREAM_ERROR;
        };
    }

    // ---- output guardrails ---------------------------------------------------

    /** The scoring rubric lives in the prompt, but we enforce it in code too. */
    private ReviewResult enforceGuardrails(ReviewResult result) {
        if (result == null || !StringUtils.hasText(result.summary())) {
            throw new ReviewAnalysisException(Reason.MODEL_OUTPUT_INVALID,
                    "The AI response was empty or incomplete.");
        }
        int score = result.overallScore();
        if (score < 1 || score > 10) {
            throw new ReviewAnalysisException(Reason.MODEL_OUTPUT_INVALID,
                    "The AI returned a score outside the 1-10 range.");
        }
        int cap = scoreCap(result);
        if (score > cap) {
            log.warn("Score {} exceeds cap {} for the reported severities; clamping", score, cap);
            return new ReviewResult(cap, result.summary(), result.securityVulnerabilities(),
                    result.performanceAndCodeSmells(), result.refactoredCode());
        }
        return result;
    }

    private static int scoreCap(ReviewResult result) {
        if (hasSeverity(result, Severity.CRITICAL)) {
            return 3;
        }
        return hasSeverity(result, Severity.HIGH) ? 5 : 10;
    }

    private static boolean hasSeverity(ReviewResult result, Severity severity) {
        return result.securityVulnerabilities().stream().anyMatch(v -> v.severity() == severity);
    }

    // ---- observability -------------------------------------------------------

    private void logUsage(ChatResponse chatResponse, long startNanos) {
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        var usage = (chatResponse == null || chatResponse.getMetadata() == null)
                ? null : chatResponse.getMetadata().getUsage();
        if (usage == null) {
            log.info("Review completed in {} ms (token usage unavailable)", ms);
            return;
        }
        log.info("Review completed in {} ms, tokens: input={}, output={}, total={}",
                ms, usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    private static String readResource(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Cannot read prompt template " + resource, e);
        }
    }
}