package io.github.abhiramchendika.gitpulse.github;

import io.github.abhiramchendika.gitpulse.config.GitHubProperties;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubApiException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubAuthenticationException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubEmptyRepositoryException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubNotFoundException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubRateLimitException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubUnavailableException;
import io.github.abhiramchendika.gitpulse.github.model.GitHubCommit;
import io.github.abhiramchendika.gitpulse.github.model.GitHubCommitDetail;
import io.github.abhiramchendika.gitpulse.github.model.GitHubContributor;
import io.github.abhiramchendika.gitpulse.github.model.GitHubContributorStats;
import io.github.abhiramchendika.gitpulse.github.model.GitHubEvent;
import io.github.abhiramchendika.gitpulse.github.model.GitHubIssue;
import io.github.abhiramchendika.gitpulse.github.model.GitHubPullRequest;
import io.github.abhiramchendika.gitpulse.github.model.GitHubRateLimitResponse;
import io.github.abhiramchendika.gitpulse.github.model.GitHubRepository;
import io.github.abhiramchendika.gitpulse.github.model.GitHubRepositorySummary;
import io.github.abhiramchendika.gitpulse.github.model.GitHubSearchResult;
import io.github.abhiramchendika.gitpulse.github.model.GitHubUserProfile;
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
import java.util.function.Predicate;
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

  /** Commit SHAs are only ever passed through from GitHub, but are still checked before use. */
  private static final java.util.regex.Pattern SHA =
      java.util.regex.Pattern.compile("^[0-9a-f]{7,64}$");

  private static final ParameterizedTypeReference<List<GitHubCommit>> COMMITS =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<GitHubContributor>> CONTRIBUTORS =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<GitHubContributorStats>> CONTRIBUTOR_STATS =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<Map<String, Long>> LANGUAGES =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<GitHubPullRequest>> PULL_REQUESTS =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<GitHubIssue>> ISSUES =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<Object>> ANY_LIST =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<GitHubRepositorySummary>> USER_REPOSITORIES =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<GitHubEvent>> EVENTS =
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
    return countItems(uri -> uri.path("/repos/{owner}/{repo}/commits").build(owner, repo));
  }

  /** {@code GET /users/{username}}: a user's or organization's public profile. 1 request. */
  public GitHubUserProfile getUser(String username) {
    return requireBody(
        execute(
            () ->
                restClient
                    .get()
                    .uri("/users/{username}", username)
                    .retrieve()
                    .body(GitHubUserProfile.class)));
  }

  /**
   * {@code GET /users/{username}/repos}: public repositories the account owns, most recently pushed
   * first. One request per 100 repositories, capped at {@code maxPages}.
   */
  public PagedResult<GitHubRepositorySummary> listUserRepositories(String username, int maxPages) {
    return fetchPages(
        uri ->
            uri.path("/users/{username}/repos")
                .queryParam("sort", "pushed")
                .queryParam("per_page", PER_PAGE)
                .build(username),
        USER_REPOSITORIES,
        maxPages);
  }

  /**
   * {@code GET /users/{username}/events/public}: the account's recent public activity. GitHub keeps
   * only the last 90 days and at most 300 events, so at most 3 pages exist.
   */
  public PagedResult<GitHubEvent> listPublicEvents(String username, int maxPages) {
    return fetchPages(
        uri ->
            uri.path("/users/{username}/events/public")
                .queryParam("per_page", PER_PAGE)
                .build(username),
        EVENTS,
        maxPages);
  }

  /**
   * Number of contributors with a linked GitHub account, in one request (this endpoint uses page
   * numbers, so the {@code rel="last"} trick works). GitHub links at most 500.
   */
  public long countContributors(String owner, String repo) {
    return countItems(uri -> uri.path("/repos/{owner}/{repo}/contributors").build(owner, repo));
  }

  /**
   * {@code GET /repos/{owner}/{repo}/commits/{sha}}: one commit with its changed files. One request
   * per commit, which is what makes file activity expensive.
   */
  public GitHubCommitDetail getCommit(String owner, String repo, String sha) {
    if (!SHA.matcher(sha).matches()) {
      throw new IllegalArgumentException("Not a commit SHA: " + sha);
    }
    return requireBody(
        execute(
            () ->
                restClient
                    .get()
                    .uri("/repos/{owner}/{repo}/commits/{sha}", owner, repo, sha)
                    .retrieve()
                    .body(GitHubCommitDetail.class)));
  }

  /**
   * Number of pull requests in a state ("open", "closed" or "all"), in one request. "closed"
   * includes merged pull requests.
   */
  public long countPullRequests(String owner, String repo, String state) {
    return countItems(
        uri ->
            uri.path("/repos/{owner}/{repo}/pulls").queryParam("state", state).build(owner, repo));
  }

  /**
   * {@code GET /search/issues}: the number of issues/pull requests matching a search query. Uses
   * the Search API quota (10 requests/minute anonymous, 30 with a token), not the core quota.
   */
  public GitHubSearchResult searchIssueCount(String query) {
    return requireBody(
        execute(
            () ->
                restClient
                    .get()
                    .uri(
                        uri ->
                            uri.path("/search/issues")
                                .queryParam("q", "{q}")
                                .queryParam("per_page", 1)
                                .build(query))
                    .retrieve()
                    .body(GitHubSearchResult.class)));
  }

  /**
   * Pull requests, newest first, until the page that reaches {@code createdSince}. The pulls
   * endpoint has no date filter, so paging stops early instead: once a page ends with a pull
   * request created before the window, older pages cannot contain anything in it.
   */
  public PagedResult<GitHubPullRequest> listPullRequests(
      String owner, String repo, Instant createdSince, int maxPages) {
    return fetchPages(
        uri ->
            uri.path("/repos/{owner}/{repo}/pulls")
                .queryParam("state", "all")
                .queryParam("sort", "created")
                .queryParam("direction", "desc")
                .queryParam("per_page", PER_PAGE)
                .build(owner, repo),
        PULL_REQUESTS,
        maxPages,
        pr -> pr.createdAt() != null && pr.createdAt().isBefore(createdSince));
  }

  /**
   * Issues <em>and pull requests</em> (GitHub mixes them), newest first, stopping early like {@link
   * #listPullRequests}. The endpoint's own {@code since} parameter filters by <em>update</em> time,
   * which is not what "opened in this window" means, so it is not used.
   */
  public PagedResult<GitHubIssue> listIssues(
      String owner, String repo, Instant createdSince, int maxPages) {
    return fetchPages(
        uri ->
            uri.path("/repos/{owner}/{repo}/issues")
                .queryParam("state", "all")
                .queryParam("sort", "created")
                .queryParam("direction", "desc")
                .queryParam("per_page", PER_PAGE)
                .build(owner, repo),
        ISSUES,
        maxPages,
        issue -> issue.createdAt() != null && issue.createdAt().isBefore(createdSince));
  }

  /**
   * Counts the items behind any list endpoint with a single request: ask for one item per page,
   * then the page number of the {@code rel="last"} link is the total.
   */
  private long countItems(Function<UriBuilder, URI> endpoint) {
    ResponseEntity<List<Object>> response =
        execute(
            () ->
                restClient
                    .get()
                    .uri(uri -> endpoint.apply(uri.queryParam("per_page", 1)))
                    .retrieve()
                    .toEntity(ANY_LIST));
    LinkHeader links = LinkHeader.parse(response.getHeaders().getFirst(HttpHeaders.LINK));
    if (links.lastPageNumber().isPresent()) {
      return links.lastPageNumber().getAsInt();
    }
    if (links.next().isPresent()) {
      // More pages exist but no page number for the last one: the endpoint uses cursor-based
      // pagination (GitHub's issues endpoint does), so the total is unknowable this way. Failing
      // loudly beats silently reporting "1".
      throw new GitHubApiException(
          HttpStatus.OK.value(), "GitHub did not report a total for this list (cursor pagination)");
    }
    // Neither link: everything fit on the first page, i.e. 0 or 1 items.
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

  private <T> PagedResult<T> fetchPages(
      Function<UriBuilder, URI> firstPage, ParameterizedTypeReference<List<T>> type, int maxPages) {
    return fetchPages(firstPage, type, maxPages, item -> false);
  }

  /**
   * Fetches the first page, then keeps following {@code rel="next"} links until there are none,
   * {@code maxPages} requests have been made, or the last item of a page matches {@code pastWindow}
   * (for lists sorted newest first: everything after it is older still).
   *
   * <p>{@code truncated} is true only when the page cap stopped us while more relevant pages
   * existed; stopping because we reached the window's start is complete, not truncated.
   */
  private <T> PagedResult<T> fetchPages(
      Function<UriBuilder, URI> firstPage,
      ParameterizedTypeReference<List<T>> type,
      int maxPages,
      Predicate<T> pastWindow) {
    if (maxPages < 1) {
      throw new IllegalArgumentException("maxPages must be at least 1");
    }
    List<T> items = new ArrayList<>();
    ResponseEntity<List<T>> response =
        execute(() -> restClient.get().uri(firstPage).retrieve().toEntity(type));
    int pages = 1;
    boolean reachedEnd = addAll(items, response, pastWindow);
    Optional<URI> next = reachedEnd ? Optional.empty() : nextPage(response);

    while (next.isPresent() && pages < maxPages) {
      URI nextUri = next.get();
      response = execute(() -> restClient.get().uri(nextUri).retrieve().toEntity(type));
      pages++;
      reachedEnd = addAll(items, response, pastWindow);
      next = reachedEnd ? Optional.empty() : nextPage(response);
    }
    return new PagedResult<>(items, pages, next.isPresent());
  }

  /** Adds a page's items; returns true if its last item is already past the window. */
  private static <T> boolean addAll(
      List<T> items, ResponseEntity<List<T>> response, Predicate<T> pastWindow) {
    List<T> page = response.getBody();
    // 204 No Content (e.g. contributors of an empty repository) has no body.
    if (page == null || page.isEmpty()) {
      return false;
    }
    items.addAll(page);
    return pastWindow.test(page.getLast());
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
