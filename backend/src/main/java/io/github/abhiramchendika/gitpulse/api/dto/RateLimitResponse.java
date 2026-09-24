package io.github.abhiramchendika.gitpulse.api.dto;

import java.time.Instant;

/**
 * Response of {@code GET /api/v1/rate-limit}.
 *
 * @param authenticated whether the backend is using a GitHub token
 * @param core quota for regular REST calls
 * @param search quota for the Search API (separate, much smaller limit)
 */
public record RateLimitResponse(boolean authenticated, Quota core, Quota search) {

  public record Quota(int limit, int remaining, int used, Instant resetAt) {}
}
