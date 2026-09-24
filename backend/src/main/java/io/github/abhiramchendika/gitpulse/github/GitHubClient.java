package io.github.abhiramchendika.gitpulse.github;

import io.github.abhiramchendika.gitpulse.config.GitHubProperties;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubApiException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubAuthenticationException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubEmptyRepositoryException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubNotFoundException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubRateLimitException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubUnavailableException;
import io.github.abhiramchendika.gitpulse.github.model.GitHubCommit;
import io.github.abhiramchendika.gitpulse.github.model.GitHubContributor;
import io.github.abhiramchendika.gitpulse.github.model.GitHubContributorStats;
import io.github.abhiramchendika.gitpulse.github.model.GitHubRateLimitResponse;
import io.github.abhiramchendika.gitpulse.github.model.GitHubRepository;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * The only class that talks to GitHub over HTTP.
 *
 * <p>Responsibilities: set required headers (API version, auth, user agent), follow pagination,
 * translate HTTP failures into typed {@code GitHub*Exception}s, and hide transport details from
 * services. It does no analysis and returns GitHub-shaped records from {@code github.model}.
 */
public class GitHubClient {

  private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);

  static final String USER_AGENT = "GitPulse (+https://github.com/abhiramchendika-svg/gitpulse)";

  /** GitHub's maximum page size. Bigger pages = fewer requests against the rate limit. */
  static final int PER_PAGE = 100;

  private static final ParameterizedTypeReference<List<GitHubCommit>> COMMITS =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<GitHubContributor>> CONTRIBUTORS =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<GitHubContributorStats>> CONTRIBUTOR_STATS =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<Map<String, Long>> LANGUAGES =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;
  private final boolean authenticated;
  private final URI baseUrl;

  public GitHubClient(RestClient.Builder builder, GitHubProperties properties) {
    this.authenticated = properties.hasToken();
    this.baseUrl = properties.baseUrl();
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
    return requireBody(
        execute(
            () ->
                restClient
                    .get()
                    .uri("/rate_limit")
                    .retrieve()
                    .body(GitHubRateLimitResponse.class)));
  }

  /** {@code GET /repos/{owner}/{repo}}. Follows redirects for renamed repositories. 1 request. */
  public GitHubRepository getRepository(String owner, String repo) {
    return requireBody(
        execute(
            () ->
                restClient
                    .get()
                    .uri("/repos/{owner}/{repo}", owner, repo)
                    .retrieve()
                    .body(GitHubRepository.class)));
  }

  /** {@code GET /repos/{owner}/{repo}/languages}: bytes of code per language. 1 request. */
  public Map<String, Long> getLanguages(String owner, String repo) {
    Map<String, Long> languages =
        execute(
            () ->
                restClient
                    .get()
                    .uri("/repos/{owner}/{repo}/languages", owner, repo)
                    .retrieve()
                    .body(LANGUAGES));
    return languages == null ? Map.of() : languages;
  }

  /**
   * {@code GET /repos/{owner}/{repo}/commits} on the default branch, newest first, between {@code
   * since} and {@code until} (GitHub filters on the commit date). Costs one request per 100
   * commits, capped at {@code maxPages}.
   *
   * @throws GitHubEmptyRepositoryException if the repository has no commits at all
   */
  public PagedResult<GitHubCommit> listCommits(
      String owner, String repo, Instant since, Instant until, int maxPages) {
    return fetchPages(
        uri ->
            uri.path("/repos/{owner}/{repo}/commits")
                .queryParam("per_page", PER_PAGE)
                .queryParam("since", since.toString())
                .queryParam("until", until.toString())
                .build(owner, repo),
        COMMITS,
        maxPages);
  }

  /**
   * Total number of commits on the default branch, in a single request: ask for one commit per page
   * and read the page number of the {@code rel="last"} link.
   *
   * @throws GitHubEmptyRepositoryException if the repository has no commits at all
   */
  public long countCommits(String owner, String repo) {
    ResponseEntity<List<GitHubCommit>> response =
        execute(
            () ->
                restClient
                    .get()
                    .uri(
                        uri ->
                            uri.path("/repos/{owner}/{repo}/commits")
                                .queryParam("per_page", 1)
                                .build(owner, repo))
                    .retrieve()
                    .toEntity(COMMITS));
    var lastPage =
        LinkHeader.parse(response.getHeaders().getFirst(HttpHeaders.LINK)).lastPageNumber();
    if (lastPage.isPresent()) {
      return lastPage.getAsInt();
    }
    // No "last" link: everything fit on the first page, i.e. 0 or 1 commits.
    return response.getBody() == null ? 0 : response.getBody().size();
  }

  /**
   * {@code GET /repos/{owner}/{repo}/contributors}: linked GitHub accounts with their all-time
   * commit count on the default branch, sorted by commits. GitHub only links the top 500.
   */
  public PagedResult<GitHubContributor> listContributors(String owner, String repo, int maxPages) {
    return fetchPages(
        uri ->
            uri.path("/repos/{owner}/{repo}/contributors")
                .queryParam("per_page", PER_PAGE)
                .build(owner, repo),
        CONTRIBUTORS,
        maxPages);
  }

  /**
   * {@code GET /repos/{owner}/{repo}/stats/contributors}: weekly additions/deletions/commits for
   * the top 100 contributors. Returns {@link StatsResult#computing()} while GitHub is still
   * generating the statistics (HTTP 202); the caller should simply ask again later.
   */
  public StatsResult<GitHubContributorStats> getContributorStats(String owner, String repo) {
    return execute(
        () ->
            restClient
                .get()
                .uri("/repos/{owner}/{repo}/stats/contributors", owner, repo)
                .exchange(
                    (request, response) -> {
                      int status = response.getStatusCode().value();
                      if (status == HttpStatus.ACCEPTED.value()) {
                        return StatsResult.<GitHubContributorStats>computing();
                      }
                      if (response.getStatusCode().isError()) {
                        throwMappedException(request, response);
                      }
                      if (status == HttpStatus.NO_CONTENT.value()) {
                        return StatsResult.<GitHubContributorStats>ready(List.of());
                      }
                      List<GitHubContributorStats> body = response.bodyTo(CONTRIBUTOR_STATS);
                      return StatsResult.ready(body == null ? List.of() : body);
                    }));
  }

  /**
   * Fetches the first page, then keeps following {@code rel="next"} links until there are none or
   * {@code maxPages} requests have been made.
   */
  private <T> PagedResult<T> fetchPages(
      Function<UriBuilder, URI> firstPage, ParameterizedTypeReference<List<T>> type, int maxPages) {
    if (maxPages < 1) {
      throw new IllegalArgumentException("maxPages must be at least 1");
    }
    List<T> items = new ArrayList<>();
    ResponseEntity<List<T>> response =
        execute(() -> restClient.get().uri(firstPage).retrieve().toEntity(type));
    int pages = 1;
    addAll(items, response);
    Optional<URI> next = nextPage(response);

    while (next.isPresent() && pages < maxPages) {
      URI nextUri = next.get();
      response = execute(() -> restClient.get().uri(nextUri).retrieve().toEntity(type));
      pages++;
      addAll(items, response);
      next = nextPage(response);
    }
    return new PagedResult<>(items, pages, next.isPresent());
  }

  private static <T> void addAll(List<T> items, ResponseEntity<List<T>> response) {
    // 204 No Content (e.g. contributors of an empty repository) has no body.
    if (response.getBody() != null) {
      items.addAll(response.getBody());
    }
  }

  /**
   * The next-page URL comes from a response header. Only follow it if it points at the configured
   * GitHub host: otherwise a malicious or buggy response could make us send our token elsewhere.
   */
  private Optional<URI> nextPage(ResponseEntity<?> response) {
    Optional<URI> next = LinkHeader.parse(response.getHeaders().getFirst(HttpHeaders.LINK)).next();
    if (next.isPresent() && !isSameOrigin(next.get())) {
      log.warn("Ignoring pagination link to unexpected host: {}", next.get().getHost());
      return Optional.empty();
    }
    return next;
  }

  boolean isSameOrigin(URI uri) {
    return Objects.equals(uri.getScheme(), baseUrl.getScheme())
        && uri.getHost() != null
        && uri.getHost().equalsIgnoreCase(baseUrl.getHost())
        && uri.getPort() == baseUrl.getPort();
  }

  /** Runs a request and converts network-level failures (DNS, timeout, refused) into our type. */
  private static <T> T execute(Supplier<T> request) {
    try {
      return request.get();
    } catch (ResourceAccessException e) {
      log.warn("GitHub request failed at network level: {}", e.getMessage());
      throw new GitHubUnavailableException("Could not reach GitHub", e);
    }
  }

  private static <T> T requireBody(T body) {
    if (body == null) {
      throw new GitHubApiException(HttpStatus.OK.value(), "GitHub returned an empty response");
    }
    return body;
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
      case 409 -> throw new GitHubEmptyRepositoryException();
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
