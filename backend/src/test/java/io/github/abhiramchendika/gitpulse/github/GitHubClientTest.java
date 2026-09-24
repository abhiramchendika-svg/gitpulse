package io.github.abhiramchendika.gitpulse.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.abhiramchendika.gitpulse.config.GitHubProperties;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubApiException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubAuthenticationException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubNotFoundException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubRateLimitException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubUnavailableException;
import io.github.abhiramchendika.gitpulse.github.model.GitHubRateLimitResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests the real {@link GitHubClient} against a fake HTTP server ({@link MockRestServiceServer}),
 * so no request ever reaches api.github.com.
 */
class GitHubClientTest {

  private static final String BASE = "https://api.github.test";

  private MockRestServiceServer server;

  private GitHubClient client(String token) {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    GitHubProperties props =
        new GitHubProperties(
            URI.create(BASE), token, "2022-11-28", Duration.ofSeconds(1), Duration.ofSeconds(1));
    return new GitHubClient(builder, props);
  }

  @Test
  void getRateLimit_parsesResponseAndSendsRequiredHeaders() {
    GitHubClient client = client("test-token");
    server
        .expect(requestTo(BASE + "/rate_limit"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
        .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
        .andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
        .andExpect(header(HttpHeaders.USER_AGENT, GitHubClient.USER_AGENT))
        .andRespond(withSuccess(fixture("github/rate_limit.json"), MediaType.APPLICATION_JSON));

    GitHubRateLimitResponse response = client.getRateLimit();

    server.verify();
    assertThat(client.isAuthenticated()).isTrue();
    assertThat(response.resources().core().limit()).isEqualTo(5000);
    assertThat(response.resources().core().remaining()).isEqualTo(4990);
    assertThat(response.resources().core().used()).isEqualTo(10);
    assertThat(response.resources().core().reset()).isEqualTo(1_790_000_000L);
    assertThat(response.resources().search().limit()).isEqualTo(30);
  }

  @Test
  void withoutToken_sendsNoAuthorizationHeader() {
    GitHubClient client = client("  ");
    server
        .expect(requestTo(BASE + "/rate_limit"))
        .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
        .andRespond(withSuccess(fixture("github/rate_limit.json"), MediaType.APPLICATION_JSON));

    client.getRateLimit();

    server.verify();
    assertThat(client.isAuthenticated()).isFalse();
  }

  @Test
  void primaryRateLimit_403WithZeroRemaining_isRateLimitException() {
    GitHubClient client = client(null);
    HttpHeaders headers = new HttpHeaders();
    headers.set("x-ratelimit-remaining", "0");
    headers.set("x-ratelimit-reset", "1790000000");
    server
        .expect(requestTo(BASE + "/rate_limit"))
        .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers));

    assertThatThrownBy(client::getRateLimit)
        .isInstanceOfSatisfying(
            GitHubRateLimitException.class,
            e -> {
              assertThat(e.getResetAt()).isEqualTo(Instant.ofEpochSecond(1_790_000_000L));
              assertThat(e.getRetryAfter()).isNull();
            });
  }

  @Test
  void secondaryRateLimit_429WithRetryAfter_isRateLimitException() {
    GitHubClient client = client(null);
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.RETRY_AFTER, "60");
    server
        .expect(requestTo(BASE + "/rate_limit"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

    assertThatThrownBy(client::getRateLimit)
        .isInstanceOfSatisfying(
            GitHubRateLimitException.class,
            e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(60)));
  }

  @Test
  void plain403WithoutRateLimitHeaders_isNotTreatedAsRateLimit() {
    GitHubClient client = client(null);
    server.expect(requestTo(BASE + "/rate_limit")).andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThatThrownBy(client::getRateLimit)
        .isInstanceOfSatisfying(
            GitHubApiException.class, e -> assertThat(e.getStatus()).isEqualTo(403));
  }

  @Test
  void notFound_isNotFoundException() {
    GitHubClient client = client(null);
    server.expect(requestTo(BASE + "/rate_limit")).andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(client::getRateLimit).isInstanceOf(GitHubNotFoundException.class);
  }

  @Test
  void unauthorized_isAuthenticationException() {
    GitHubClient client = client("revoked-token");
    server.expect(requestTo(BASE + "/rate_limit")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(client::getRateLimit).isInstanceOf(GitHubAuthenticationException.class);
  }

  @Test
  void serverError_isUnavailableException() {
    GitHubClient client = client(null);
    server.expect(requestTo(BASE + "/rate_limit")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

    assertThatThrownBy(client::getRateLimit).isInstanceOf(GitHubUnavailableException.class);
  }

  @Test
  void networkFailure_isUnavailableException() {
    GitHubClient client = client(null);
    server
        .expect(requestTo(BASE + "/rate_limit"))
        .andRespond(withException(new IOException("connection refused")));

    assertThatThrownBy(client::getRateLimit).isInstanceOf(GitHubUnavailableException.class);
  }

  @Test
  void isRateLimited_onlyFor403Or429WithSignals() {
    HttpHeaders none = new HttpHeaders();
    HttpHeaders zeroRemaining = new HttpHeaders();
    zeroRemaining.set("x-ratelimit-remaining", "0");

    assertThat(GitHubClient.isRateLimited(429, none)).isTrue();
    assertThat(GitHubClient.isRateLimited(403, zeroRemaining)).isTrue();
    assertThat(GitHubClient.isRateLimited(403, none)).isFalse();
    assertThat(GitHubClient.isRateLimited(404, zeroRemaining)).isFalse();
  }

  private static String fixture(String path) {
    try {
      return new ClassPathResource(path)
          .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Missing test fixture " + path, e);
    }
  }
}
