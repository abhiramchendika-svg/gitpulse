package io.github.abhiramchendika.gitpulse.github.model;

/**
 * Shape of GitHub's {@code GET /rate_limit} response (only the fields GitPulse uses).
 *
 * <p>{@code reset} is a Unix epoch timestamp in seconds, as sent by GitHub.
 */
public record GitHubRateLimitResponse(Resources resources) {

  public record Resources(Bucket core, Bucket search) {}

  public record Bucket(int limit, int remaining, int used, long reset) {}
}
