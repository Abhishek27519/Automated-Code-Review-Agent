package com.codereview.agent.exception;

public class ReviewAnalysisException extends RuntimeException {

    public enum Reason {
        INPUT_TOO_LARGE(413, "Input exceeds the configured size limit."),
        MODEL_OUTPUT_INVALID(502, "The AI response could not be turned into a valid review. Retrying may help."),
        UPSTREAM_RATE_LIMITED(429, "The AI provider is rate limiting requests. Retry after a short delay."),
        UPSTREAM_AUTH_ERROR(502, "The service could not authenticate with the AI provider."),
        UPSTREAM_TIMEOUT(504, "The AI provider took too long to respond."),
        UPSTREAM_UNAVAILABLE(503, "The AI provider is temporarily unavailable. Retry shortly."),
        UPSTREAM_ERROR(502, "The AI provider rejected the request.");

        private final int httpStatus;
        private final String defaultMessage;

        Reason(int httpStatus, String defaultMessage) {
            this.httpStatus = httpStatus;
            this.defaultMessage = defaultMessage;
        }

        public int httpStatus() { return httpStatus; }
        public String defaultMessage() { return defaultMessage; }
    }

    private final Reason reason;

    public ReviewAnalysisException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ReviewAnalysisException(Reason reason, Throwable cause) {
        super(reason.defaultMessage(), cause);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}