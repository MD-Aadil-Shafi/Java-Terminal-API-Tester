package org.terminal;

public record RequestResult(
        int requestId,
        int statusCode,
        long latencyMs,
        boolean success,
        String error
) {
}
