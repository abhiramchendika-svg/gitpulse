package io.github.abhiramchendika.gitpulse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.abhiramchendika.gitpulse.api.dto.RateLimitResponse;
import io.github.abhiramchendika.gitpulse.github.GitHubClient;
import io.github.abhiramchendika.gitpulse.github.model.GitHubRateLimitResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RateLimitServiceTest {

  @Test
  void mapsGitHubBucketsAndConvertsEpochSecondsToInstant() {
    GitHubClient client = mock(GitHubClient.class);
    when(client.isAuthenticated()).thenReturn(false);
    when(client.getRateLimit())
        .thenReturn(
            new GitHubRateLimitResponse(
                new GitHubRateLimitResponse.Resources(
                    new GitHubRateLimitResponse.Bucket(60, 55, 5, 1_790_000_000L),
                    new GitHubRateLimitResponse.Bucket(10, 10, 0, 1_789_996_460L))));

    RateLimitResponse response = new RateLimitService(client).getRateLimit();

    assertThat(response.authenticated()).isFalse();
    assertThat(response.core())
        .isEqualTo(new RateLimitResponse.Quota(60, 55, 5, Instant.ofEpochSecond(1_790_000_000L)));
    assertThat(response.search().limit()).isEqualTo(10);
  }

  @Test
  void missingSearchBucket_isNullNotCrash() {
    GitHubClient client = mock(GitHubClient.class);
    when(client.getRateLimit())
        .thenReturn(
            new GitHubRateLimitResponse(
                new GitHubRateLimitResponse.Resources(
                    new GitHubRateLimitResponse.Bucket(60, 60, 0, 1_790_000_000L), null)));

    RateLimitResponse response = new RateLimitService(client).getRateLimit();

    assertThat(response.search()).isNull();
  }
}
