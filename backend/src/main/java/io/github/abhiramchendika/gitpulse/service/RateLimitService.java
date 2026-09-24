package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.api.dto.RateLimitResponse;
import io.github.abhiramchendika.gitpulse.github.GitHubClient;
import io.github.abhiramchendika.gitpulse.github.model.GitHubRateLimitResponse;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Reports how much GitHub API quota the backend has left, so the UI can warn before it runs out.
 */
@Service
public class RateLimitService {

  private final GitHubClient gitHubClient;

  public RateLimitService(GitHubClient gitHubClient) {
    this.gitHubClient = gitHubClient;
  }

  public RateLimitResponse getRateLimit() {
    GitHubRateLimitResponse.Resources resources = gitHubClient.getRateLimit().resources();
    return new RateLimitResponse(
        gitHubClient.isAuthenticated(), toQuota(resources.core()), toQuota(resources.search()));
  }

  private static RateLimitResponse.Quota toQuota(GitHubRateLimitResponse.Bucket bucket) {
    if (bucket == null) {
      return null;
    }
    return new RateLimitResponse.Quota(
        bucket.limit(), bucket.remaining(), bucket.used(), Instant.ofEpochSecond(bucket.reset()));
  }
}
