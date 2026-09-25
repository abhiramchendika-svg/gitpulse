package io.github.abhiramchendika.gitpulse.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.abhiramchendika.gitpulse.config.GitHubProperties;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubRateLimitException;
import io.github.abhiramchendika.gitpulse.github.model.GitHubIssue;
import io.github.abhiramchendika.gitpulse.github.model.GitHubPullRequest;
import io.github.abhiramchendika.gitpulse.github.model.GitHubSearchResult;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Pull requests, issues, counting and search, against a fake GitHub. */
class GitHubClientPullRequestTest {

  private static final String BASE = "https://api.github.test";
  private static final String PULLS = BASE + "/repos/o/r/pulls";
  private static final Instant SINCE = Instant.parse("2026-01-01T00:00:00Z");

  private MockRestServiceServer server;
  private GitHubClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client =
        new GitHubClient(
            builder,
            new GitHubProperties(
                URI.create(BASE),
                null,
                "2022-11-28",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)));
  }

  private static String pr(int number, String created) {
    return """
        {"number":%d,"title":"PR %d","state":"open","draft":false,
         "user":{"login":"u%d","id":%d,"type":"User"},
         "created_at":"%s","closed_at":null,"merged_at":null,"html_url":"h"}
        """
        .formatted(number, number, number, number, created);
  }

  private static HttpHeaders next(String url) {
    HttpHeaders h = new HttpHeaders();
    h.set(HttpHeaders.LINK, "<" + url + ">; rel=\"next\"");
    return h;
  }

  @Test
  void listPullRequests_stopsEarlyOncePageReachesBeforeTheWindow() {
    String first = PULLS + "?state=all&sort=created&direction=desc&per_page=100";
    server
        .expect(requestTo(first))
        .andRespond(
            withSuccess(
                    "[" + pr(3, "2026-03-01T00:00:00Z") + "," + pr(2, "2026-02-01T00:00:00Z") + "]",
                    MediaType.APPLICATION_JSON)
                .headers(next(PULLS + "?page=2")));
    server
        .expect(requestTo(PULLS + "?page=2"))
        .andRespond(
            // Last item is older than SINCE: everything after this page is older still.
            withSuccess(
                    "[" + pr(1, "2026-01-15T00:00:00Z") + "," + pr(0, "2025-12-20T00:00:00Z") + "]",
                    MediaType.APPLICATION_JSON)
                .headers(next(PULLS + "?page=3")));
    server.expect(never(), requestTo(PULLS + "?page=3"));

    PagedResult<GitHubPullRequest> result = client.listPullRequests("o", "r", SINCE, 10);

    server.verify();
    assertThat(result.items()).hasSize(4);
    assertThat(result.pagesFetched()).isEqualTo(2);
    // Stopping at the window's start is complete, not truncated.
    assertThat(result.truncated()).isFalse();
  }

  @Test
  void listPullRequests_hittingThePageCapBeforeTheWindowStart_isTruncated() {
    server
        .expect(requestTo(PULLS + "?state=all&sort=created&direction=desc&per_page=100"))
        .andRespond(
            withSuccess("[" + pr(9, "2026-06-01T00:00:00Z") + "]", MediaType.APPLICATION_JSON)
                .headers(next(PULLS + "?page=2")));

    PagedResult<GitHubPullRequest> result = client.listPullRequests("o", "r", SINCE, 1);

    assertThat(result.truncated()).isTrue();
  }

  @Test
  void countPullRequests_usesPerPageOneAndTheLastPageNumber() {
    HttpHeaders link = new HttpHeaders();
    link.set(HttpHeaders.LINK, "<" + PULLS + "?state=open&per_page=1&page=37>; rel=\"last\"");
    server
        .expect(requestTo(PULLS + "?per_page=1&state=open"))
        .andRespond(withSuccess("[{}]", MediaType.APPLICATION_JSON).headers(link));

    assertThat(client.countPullRequests("o", "r", "open")).isEqualTo(37);
  }

  /**
   * Regression test for a bug found against spring-petclinic: GitHub's issues endpoint uses cursor
   * pagination, whose Link header has "next" but no "last". Counting then silently returned the
   * size of the first page (1). It must refuse instead.
   */
  @Test
  void count_withCursorPagination_refusesInsteadOfReturningOne() {
    HttpHeaders cursor = new HttpHeaders();
    cursor.set(HttpHeaders.LINK, "<" + PULLS + "?per_page=1&after=Y3Vyc29y&page=2>; rel=\"next\"");
    server
        .expect(requestTo(PULLS + "?per_page=1&state=open"))
        .andRespond(withSuccess("[{}]", MediaType.APPLICATION_JSON).headers(cursor));

    assertThatThrownBy(() -> client.countPullRequests("o", "r", "open"))
        .isInstanceOf(io.github.abhiramchendika.gitpulse.github.exception.GitHubApiException.class)
        .hasMessageContaining("cursor");
  }

  @Test
  void getCommit_parsesChangedFilesIncludingRenames() {
    server
        .expect(requestTo(BASE + "/repos/o/r/commits/abc1234"))
        .andRespond(
            withSuccess(
                """
                {"sha":"abc1234","html_url":"h",
                 "commit":{"author":{"name":"M","date":"2026-09-01T00:00:00Z"},"message":"x"},
                 "author":{"login":"mona","id":1,"type":"User"},"parents":[{"sha":"p"}],
                 "stats":{"total":12,"additions":10,"deletions":2},
                 "files":[
                   {"filename":"src/New.java","status":"renamed","additions":1,"deletions":1,
                    "changes":2,"previous_filename":"src/Old.java"},
                   {"filename":"README.md","status":"modified","additions":9,"deletions":1,
                    "changes":10}]}
                """,
                MediaType.APPLICATION_JSON));

    var detail = client.getCommit("o", "r", "abc1234");

    assertThat(detail.files()).hasSize(2);
    assertThat(detail.files().getFirst().previousFilename()).isEqualTo("src/Old.java");
    assertThat(detail.files().get(1).additions()).isEqualTo(9);
  }

  @Test
  void getCommit_rejectsSomethingThatIsNotASha() {
    assertThatThrownBy(() -> client.getCommit("o", "r", "../../users/x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void countContributors_readsTheLastPage() {
    HttpHeaders link = new HttpHeaders();
    link.set(HttpHeaders.LINK, "<" + BASE + "/repos/o/r/contributors?page=131>; rel=\"last\"");
    server
        .expect(requestTo(BASE + "/repos/o/r/contributors?per_page=1"))
        .andRespond(withSuccess("[{}]", MediaType.APPLICATION_JSON).headers(link));

    assertThat(client.countContributors("o", "r")).isEqualTo(131);
  }

  /**
   * The repository list lacks fields the single-repository endpoint has (e.g. subscribers_count);
   * the summary record must still deserialize under Jackson 3's strict primitive handling.
   */
  @Test
  void listUserRepositories_parsesListItemsWithoutSingleRepositoryFields() {
    server
        .expect(requestTo(BASE + "/users/mona/repos?sort=pushed&per_page=100"))
        .andRespond(
            withSuccess(
                """
                [{"name":"app","full_name":"mona/app","html_url":"h","description":null,
                  "fork":false,"archived":false,"language":"Java","stargazers_count":5,
                  "forks_count":1,"created_at":"2020-01-01T00:00:00Z",
                  "pushed_at":"2026-09-01T00:00:00Z"}]
                """,
                MediaType.APPLICATION_JSON));

    var result = client.listUserRepositories("mona", 3);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().stargazersCount()).isEqualTo(5);
  }

  @Test
  void getUser_doesNotMapTheEmailAddress() {
    server
        .expect(requestTo(BASE + "/users/mona"))
        .andRespond(
            withSuccess(
                """
                {"login":"mona","type":"User","name":"Mona","email":"mona@example.com",
                 "public_repos":3,"followers":10,"following":2,
                 "created_at":"2015-01-01T00:00:00Z"}
                """,
                MediaType.APPLICATION_JSON));

    var profile = client.getUser("mona");

    assertThat(profile.login()).isEqualTo("mona");
    assertThat(profile.toString()).doesNotContain("mona@example.com");
  }

  @Test
  void countPullRequests_emptyList_isZero() {
    server
        .expect(requestTo(PULLS + "?per_page=1&state=closed"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThat(client.countPullRequests("o", "r", "closed")).isZero();
  }

  @Test
  void listIssues_marksPullRequestsMixedIntoTheIssueList() {
    server
        .expect(
            requestTo(
                BASE + "/repos/o/r/issues?state=all&sort=created&direction=desc&per_page=100"))
        .andRespond(
            withSuccess(
                """
                [{"number":2,"title":"A PR","state":"open","user":{"login":"a","id":1,"type":"User"},
                  "comments":0,"created_at":"2026-02-01T00:00:00Z","html_url":"h",
                  "pull_request":{"url":"x"}},
                 {"number":1,"title":"Bug","state":"closed","state_reason":"completed",
                  "user":{"login":"b","id":2,"type":"User"},"comments":3,
                  "created_at":"2026-01-02T00:00:00Z","closed_at":"2026-01-03T00:00:00Z",
                  "html_url":"h"}]
                """,
                MediaType.APPLICATION_JSON));

    PagedResult<GitHubIssue> result = client.listIssues("o", "r", SINCE, 5);

    assertThat(result.items()).extracting(GitHubIssue::isPullRequest).containsExactly(true, false);
    assertThat(result.items().get(1).stateReason()).isEqualTo("completed");
    assertThat(result.items().get(1).comments()).isEqualTo(3);
  }

  @Test
  void searchIssueCount_encodesTheQueryAndReadsTotalCount() {
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/search/issues?q=")))
        // Template variables are fully percent-encoded (':' and '/' included), so user-controlled
        // text can never inject extra query parameters.
        .andExpect(queryParam("q", "repo%3Ao%2Fr%20is%3Apr%20is%3Amerged"))
        .andExpect(queryParam("per_page", "1"))
        .andRespond(
            withSuccess(
                "{\"total_count\":1234,\"incomplete_results\":false,\"items\":[{}]}",
                MediaType.APPLICATION_JSON));

    GitHubSearchResult result = client.searchIssueCount("repo:o/r is:pr is:merged");

    assertThat(result.totalCount()).isEqualTo(1234);
    assertThat(result.incompleteResults()).isFalse();
  }

  @Test
  void searchRateLimit_isRateLimitException() {
    HttpHeaders limited = new HttpHeaders();
    limited.set("x-ratelimit-remaining", "0");
    limited.set("x-ratelimit-resource", "search");
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/search/issues")))
        .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(limited));

    assertThatThrownBy(() -> client.searchIssueCount("repo:o/r is:pr is:merged"))
        .isInstanceOf(GitHubRateLimitException.class);
  }
}
