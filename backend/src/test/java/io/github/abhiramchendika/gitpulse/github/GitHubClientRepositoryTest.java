package io.github.abhiramchendika.gitpulse.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.abhiramchendika.gitpulse.config.GitHubProperties;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubEmptyRepositoryException;
import io.github.abhiramchendika.gitpulse.github.model.GitHubCommit;
import io.github.abhiramchendika.gitpulse.github.model.GitHubContributor;
import io.github.abhiramchendika.gitpulse.github.model.GitHubContributorStats;
import io.github.abhiramchendika.gitpulse.github.model.GitHubRepository;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Repository endpoints, pagination and GitHub-specific status codes, against a fake server. */
class GitHubClientRepositoryTest {

  private static final String BASE = "https://api.github.test";
  private static final String COMMITS = BASE + "/repos/octocat/hello/commits";
  private static final Instant SINCE = Instant.parse("2025-09-25T00:00:00Z");
  private static final Instant UNTIL = Instant.parse("2026-09-25T00:00:00Z");

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

  @Test
  void getRepository_mapsSnakeCaseFieldsAndIgnoresUnknownOnes() {
    server
        .expect(requestTo(BASE + "/repos/octocat/hello"))
        .andRespond(withSuccess(fixture("github/repository.json"), MediaType.APPLICATION_JSON));

    GitHubRepository repo = client.getRepository("octocat", "hello");

    assertThat(repo.fullName()).isEqualTo("octocat/Hello-World");
    assertThat(repo.stargazersCount()).isEqualTo(80);
    assertThat(repo.subscribersCount()).isEqualTo(42);
    assertThat(repo.openIssuesCount()).isEqualTo(7);
    assertThat(repo.license().spdxId()).isEqualTo("MIT");
    assertThat(repo.createdAt()).isEqualTo(Instant.parse("2011-01-26T19:01:12Z"));
    assertThat(repo.owner().login()).isEqualTo("octocat");
    assertThat(repo.topics()).containsExactly("octocat", "api");
  }

  @Test
  void getLanguages_returnsBytesPerLanguage() {
    server
        .expect(requestTo(BASE + "/repos/octocat/hello/languages"))
        .andRespond(withSuccess("{\"Java\": 1200, \"CSS\": 34}", MediaType.APPLICATION_JSON));

    assertThat(client.getLanguages("octocat", "hello"))
        .isEqualTo(Map.of("Java", 1200L, "CSS", 34L));
  }

  @Test
  void listCommits_followsNextLinksUntilLastPage() {
    server
        .expect(
            requestTo(
                COMMITS + "?per_page=100&since=2025-09-25T00:00:00Z&until=2026-09-25T00:00:00Z"))
        .andExpect(queryParam("per_page", "100"))
        .andRespond(
            withSuccess(fixture("github/commits_page1.json"), MediaType.APPLICATION_JSON)
                .headers(link("<" + COMMITS + "?per_page=100&page=2>; rel=\"next\"")));
    server
        .expect(requestTo(COMMITS + "?per_page=100&page=2"))
        .andRespond(withSuccess(fixture("github/commits_page2.json"), MediaType.APPLICATION_JSON));

    PagedResult<GitHubCommit> result = client.listCommits("octocat", "hello", SINCE, UNTIL, 10);

    server.verify();
    assertThat(result.items()).hasSize(3);
    assertThat(result.pagesFetched()).isEqualTo(2);
    assertThat(result.truncated()).isFalse();
    GitHubCommit first = result.items().getFirst();
    assertThat(first.author().login()).isEqualTo("octocat");
    assertThat(first.commit().author().date()).isEqualTo(Instant.parse("2026-09-20T10:00:00Z"));
    assertThat(result.items().get(1).author()).isNull();
    assertThat(result.items().get(1).parents()).hasSize(2);
  }

  @Test
  void listCommits_stopsAtPageCapAndReportsTruncation() {
    server
        .expect(
            requestTo(
                COMMITS + "?per_page=100&since=2025-09-25T00:00:00Z&until=2026-09-25T00:00:00Z"))
        .andRespond(
            withSuccess(fixture("github/commits_page1.json"), MediaType.APPLICATION_JSON)
                .headers(link("<" + COMMITS + "?per_page=100&page=2>; rel=\"next\"")));
    server.expect(never(), requestTo(COMMITS + "?per_page=100&page=2"));

    PagedResult<GitHubCommit> result = client.listCommits("octocat", "hello", SINCE, UNTIL, 1);

    server.verify();
    assertThat(result.items()).hasSize(2);
    assertThat(result.truncated()).isTrue();
  }

  @Test
  void listCommits_ignoresNextLinkPointingAtAnotherHost() {
    server
        .expect(
            requestTo(
                COMMITS + "?per_page=100&since=2025-09-25T00:00:00Z&until=2026-09-25T00:00:00Z"))
        .andRespond(
            withSuccess(fixture("github/commits_page1.json"), MediaType.APPLICATION_JSON)
                .headers(link("<https://evil.example/steal?page=2>; rel=\"next\"")));

    PagedResult<GitHubCommit> result = client.listCommits("octocat", "hello", SINCE, UNTIL, 10);

    server.verify(); // no second request was made
    assertThat(result.items()).hasSize(2);
    assertThat(result.pagesFetched()).isEqualTo(1);
  }

  @Test
  void emptyRepository_409_isEmptyRepositoryException() {
    server
        .expect(
            requestTo(
                COMMITS + "?per_page=100&since=2025-09-25T00:00:00Z&until=2026-09-25T00:00:00Z"))
        .andRespond(withStatus(HttpStatus.CONFLICT));

    assertThatThrownBy(() -> client.listCommits("octocat", "hello", SINCE, UNTIL, 10))
        .isInstanceOf(GitHubEmptyRepositoryException.class);
  }

  @Test
  void countCommits_readsLastPageNumberFromOneRequest() {
    server
        .expect(requestTo(COMMITS + "?per_page=1"))
        .andRespond(
            withSuccess("[{\"sha\":\"x\"}]", MediaType.APPLICATION_JSON)
                .headers(
                    link(
                        "<"
                            + COMMITS
                            + "?per_page=1&page=2>; rel=\"next\", <"
                            + COMMITS
                            + "?per_page=1&page=1234>; rel=\"last\"")));

    assertThat(client.countCommits("octocat", "hello")).isEqualTo(1234);
  }

  @Test
  void countCommits_singleCommitHasNoLastLink() {
    server
        .expect(requestTo(COMMITS + "?per_page=1"))
        .andRespond(withSuccess("[{\"sha\":\"x\"}]", MediaType.APPLICATION_JSON));

    assertThat(client.countCommits("octocat", "hello")).isEqualTo(1);
  }

  @Test
  void listContributors_204ForEmptyRepository_isEmptyList() {
    server
        .expect(requestTo(BASE + "/repos/octocat/hello/contributors?per_page=100"))
        .andRespond(withNoContent());

    PagedResult<GitHubContributor> result = client.listContributors("octocat", "hello", 5);

    assertThat(result.items()).isEmpty();
    assertThat(result.truncated()).isFalse();
  }

  @Test
  void contributorStats_202_isPending() {
    server
        .expect(requestTo(BASE + "/repos/octocat/hello/stats/contributors"))
        .andRespond(withStatus(HttpStatus.ACCEPTED).body("{}"));

    StatsResult<GitHubContributorStats> result = client.getContributorStats("octocat", "hello");

    assertThat(result.pending()).isTrue();
    assertThat(result.items()).isEmpty();
  }

  @Test
  void contributorStats_200_isParsed() {
    server
        .expect(requestTo(BASE + "/repos/octocat/hello/stats/contributors"))
        .andRespond(
            withSuccess(
                """
                [{"author":{"login":"octocat","id":1,"type":"User"},"total":3,
                  "weeks":[{"w":1767225600,"a":10,"d":2,"c":1},{"w":1767830400,"a":5,"d":0,"c":2}]}]
                """,
                MediaType.APPLICATION_JSON));

    StatsResult<GitHubContributorStats> result = client.getContributorStats("octocat", "hello");

    assertThat(result.pending()).isFalse();
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().weeks()).hasSize(2);
    assertThat(result.items().getFirst().weeks().getFirst().a()).isEqualTo(10);
  }

  @Test
  void contributorStats_errorsStillMapped() {
    server
        .expect(requestTo(BASE + "/repos/octocat/hello/stats/contributors"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> client.getContributorStats("octocat", "hello"))
        .isInstanceOf(
            io.github.abhiramchendika.gitpulse.github.exception.GitHubNotFoundException.class);
  }

  @Test
  void isSameOrigin_requiresSchemeHostAndPort() {
    assertThat(client.isSameOrigin(URI.create("https://api.github.test/x"))).isTrue();
    assertThat(client.isSameOrigin(URI.create("https://API.GITHUB.TEST/x"))).isTrue();
    assertThat(client.isSameOrigin(URI.create("http://api.github.test/x"))).isFalse();
    assertThat(client.isSameOrigin(URI.create("https://api.github.test:8443/x"))).isFalse();
    assertThat(client.isSameOrigin(URI.create("https://api.github.test.evil.example/x"))).isFalse();
  }

  private static HttpHeaders link(String value) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.LINK, value);
    return headers;
  }

  private static String fixture(String path) {
    try {
      return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Missing test fixture " + path, e);
    }
  }
}
