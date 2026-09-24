package io.github.abhiramchendika.gitpulse.github.exception;

import java.time.Duration;
import java.time.Instant;

/** GitHub refused the request because a primary or secondary rate limit was hit. */
public class GitHubRateLimitException extends GitHubException {

  private final Instant resetAt;
  private final Duration retryAfter;

  /**
   * @param resetAt when the primary limit resets ({@code x-ratelimit-reset}), or null if unknown
   * @param retryAfter how long to wait for a secondary limit ({@code Retry-After}), or null
   */
  public GitHubRateLimitException(Instant resetAt, Duration retryAfter) {
    super("GitHub API rate limit exceeded", null);
    this.resetAt = resetAt;
    this.retryAfter = retryAfter;
  }

  public Instant getResetAt() {
    return resetAt;
  }

  public Duration getRetryAfter() {
    return retryAfter;
  }
}
