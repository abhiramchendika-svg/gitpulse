package io.github.abhiramchendika.gitpulse.github;

import io.github.abhiramchendika.gitpulse.config.GitHubProperties;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubApiException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubAuthenticationException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubNotFoundException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubRateLimitException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubUnavailableException;
import io.github.abhiramchendika.gitpulse.github.model.GitHubRateLimitResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * The only class that talks to GitHub over HTTP.
 *
 * <p>Responsibilities: set required headers (API version, auth, user agent), translate HTTP
 * failures into typed {@code GitHub*Exception}s, and hide transport details from services. It does
 * no analysis and returns GitHub-shaped records from {@code github.model}.
 */
public class GitHubClient {

  private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);

  static final String USER_AGENT = "GitPulse (+https://github.com/abhiramchendika-svg/gitpulse)";

  private final RestClient restClient;
  private final boolean authenticated;

  public GitHubClient(RestClient.Builder builder, GitHubProperties properties) {
    this.authenticated = properties.hasToken();
    this.restClient =
        builder
            .baseUrl(properties.baseUrl().toString())
            .defaultHeaders(
                headers -> {
                  headers.setAccept(List.of(MediaType.valueOf("application/vnd.github+json")));
                  headers.set("X-GitHub-Api-Version", properties.apiVersion());
                  // GitHub rejects requests without a User-Agent.
                  headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
                  if (properties.hasToken()) {
                    headers.setBearerAuth(properties.token());
                  }
                })
            .defaultStatusHandler(HttpStatusCode::isError, GitHubClient::throwMappedException)
            .build();
  }

  /** Whether requests carry a token (5,000 req/h) or are anonymous (60 req/h). */
  public boolean isAuthenticated() {
    return authenticated;
  }

  /** {@code GET /rate_limit}. This call does not count against the rate limit. */
  public GitHubRateLimitResponse getRateLimit() {
    return execute(
        () -> restClient.get().uri("/rate_limit").retrieve().body(GitHubRateLimitResponse.class));
  }

  /** Runs a request and converts network-level failures (DNS, timeout, refused) into our type. */
  private static <T> T execute(Supplier<T> request) {
    try {
      T body = request.get();
      if (body == null) {
        throw new GitHubApiException(HttpStatus.OK.value(), "GitHub returned an empty response");
      }
      return body;
    } catch (ResourceAccessException e) {
      log.warn("GitHub request failed at network level: {}", e.getMessage());
      throw new GitHubUnavailableException("Could not reach GitHub", e);
    }
  }

  private static void throwMappedException(HttpRequest request, ClientHttpResponse response)
      throws IOException {
    int status = response.getStatusCode().value();
    HttpHeaders headers = response.getHeaders();
    log.debug("GitHub responded {} for {} {}", status, request.getMethod(), request.getURI());

    if (isRateLimited(status, headers)) {
      throw new GitHubRateLimitException(resetTime(headers), retryAfter(headers));
    }
    switch (status) {
      case 401 -> throw new GitHubAuthenticationException();
      case 404 -> throw new GitHubNotFoundException();
      default -> {
        if (status >= 500) {
          throw new GitHubUnavailableException("GitHub returned HTTP " + status, null);
        }
        throw new GitHubApiException(status, "GitHub returned HTTP " + status);
      }
    }
  }

  /**
   * GitHub signals rate limiting in two ways: the primary limit (403/429 with {@code
   * x-ratelimit-remaining: 0}) and secondary "abuse" limits (403/429 with {@code Retry-After}). A
   * plain 403 without those headers is a permission problem, not a rate limit.
   */
  static boolean isRateLimited(int status, HttpHeaders headers) {
    if (status != 403 && status != 429) {
      return false;
    }
    return status == 429
        || "0".equals(headers.getFirst("x-ratelimit-remaining"))
        || headers.getFirst(HttpHeaders.RETRY_AFTER) != null;
  }

  private static Instant resetTime(HttpHeaders headers) {
    String reset = headers.getFirst("x-ratelimit-reset");
    try {
      return reset == null ? null : Instant.ofEpochSecond(Long.parseLong(reset.trim()));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Duration retryAfter(HttpHeaders headers) {
    String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
    try {
      return retryAfter == null ? null : Duration.ofSeconds(Long.parseLong(retryAfter.trim()));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
