package io.github.abhiramchendika.gitpulse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.abhiramchendika.gitpulse.analysis.model.CommitRecord;
import io.github.abhiramchendika.gitpulse.config.AnalysisProperties;
import io.github.abhiramchendika.gitpulse.github.GitHubClient;
import io.github.abhiramchendika.gitpulse.github.PagedResult;
import io.github.abhiramchendika.gitpulse.github.StatsResult;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubApiException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubEmptyRepositoryException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubNotFoundException;
import io.github.abhiramchendika.gitpulse.github.model.GitHubCommit;
import io.github.abhiramchendika.gitpulse.github.model.GitHubContributor;
import io.github.abhiramchendika.gitpulse.github.model.GitHubContributorStats;
import io.github.abhiramchendika.gitpulse.github.model.GitHubUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Normalization and error translation, with the GitHub client mocked out. */
class RepositoryDataServiceTest {

  private static final RepositoryRef REF = new RepositoryRef("octocat", "hello");
  private static final Instant T = Instant.parse("2026-09-20T10:00:00Z");

  private final GitHubClient client = mock(GitHubClient.class);
  private final RepositoryDataService service =
      new RepositoryDataService(
          client, new AnalysisProperties(10, 5, 365, 3650, Duration.ofDays(14), 10, 10, 10, 100));

  private static GitHubCommit commit(
      GitHubUser account, String gitName, String message, int parents) {
    List<GitHubCommit.Parent> parentList =
        java.util.stream.IntStream.range(0, parents)
            .mapToObj(i -> new GitHubCommit.Parent("p" + i))
            .toList();
    return new GitHubCommit(
        "0123456789abcdef",
        "url",
        new GitHubCommit.Details(
            new GitHubCommit.GitIdentity(gitName, T),
            new GitHubCommit.GitIdentity("c", T),
            message),
        account,
        parentList);
  }

  @Test
  void linkedCommit_usesLoginAsKey() {
    CommitRecord record =
        RepositoryDataService.toCommitRecord(
            commit(new GitHubUser("octocat", 1, "User", "a", "h"), "Mona", "Fix bug\n\nbody", 1));

    assertThat(record.authorKey()).isEqualTo("octocat");
    assertThat(record.authorLogin()).isEqualTo("octocat");
    assertThat(record.headline()).isEqualTo("Fix bug");
    assertThat(record.merge()).isFalse();
    assertThat(record.bot()).isFalse();
    assertThat(record.authoredAt()).isEqualTo(T);
    assertThat(record.committedAt()).isEqualTo(T);
  }

  @Test
  void unlinkedCommit_isGroupedByGitNameWithoutLogin() {
    CommitRecord record = RepositoryDataService.toCommitRecord(commit(null, "Jane Doe", "x", 1));

    assertThat(record.authorKey()).isEqualTo("git:Jane Doe");
    assertThat(record.authorName()).isEqualTo("Jane Doe");
    assertThat(record.authorLogin()).isNull();
  }

  @Test
  void mergeAndBotAreDetected() {
    CommitRecord record =
        RepositoryDataService.toCommitRecord(
            commit(new GitHubUser("dependabot[bot]", 2, "Bot", "a", "h"), "d", "Merge", 2));

    assertThat(record.merge()).isTrue();
    assertThat(record.bot()).isTrue();
  }

  @Test
  void commitWithoutDetails_isSkipped() {
    GitHubCommit broken = new GitHubCommit("sha", "url", null, null, List.of());

    assertThat(RepositoryDataService.toCommitRecord(broken)).isNull();
  }

  @Test
  void headline_isTruncated() {
    String longLine = "x".repeat(500);

    assertThat(RepositoryDataService.headline(longLine))
        .hasSize(RepositoryDataService.MAX_HEADLINE_LENGTH)
        .endsWith("…");
    assertThat(RepositoryDataService.headline(null)).isEmpty();
  }

  @Test
  void commits_emptyRepository_isEmptySampleNotError() {
    when(client.listCommits(eq("octocat"), eq("hello"), any(), any(), anyInt()))
        .thenThrow(new GitHubEmptyRepositoryException());

    CommitSample sample = service.commits(REF, T.minusSeconds(86400), T);

    assertThat(sample.emptyRepository()).isTrue();
    assertThat(sample.commits()).isEmpty();
  }

  @Test
  void commits_passesTruncationThrough() {
    when(client.listCommits(eq("octocat"), eq("hello"), any(), any(), eq(10)))
        .thenReturn(new PagedResult<>(List.of(commit(null, "a", "m", 1)), 10, true));

    CommitSample sample = service.commits(REF, T.minusSeconds(86400), T);

    assertThat(sample.truncated()).isTrue();
    assertThat(sample.commits()).hasSize(1);
  }

  @Test
  void notFound_becomesRepositoryNotFound() {
    when(client.getRepository("octocat", "hello")).thenThrow(new GitHubNotFoundException());

    assertThatThrownBy(() -> service.repository(REF))
        .isInstanceOf(RepositoryNotFoundException.class)
        .hasMessageContaining("octocat/hello");
  }

  @Test
  void contributors_403FromGitHub_isUnavailableNotError() {
    when(client.listContributors("octocat", "hello", 5))
        .thenThrow(new GitHubApiException(403, "too large"));

    ContributorSample sample = service.contributors(REF);

    assertThat(sample.available()).isFalse();
  }

  @Test
  void contributors_flagsBots() {
    when(client.listContributors("octocat", "hello", 5))
        .thenReturn(
            new PagedResult<>(
                List.of(
                    new GitHubContributor("octocat", 1, "User", "a", "h", 10),
                    new GitHubContributor("renovate[bot]", 2, "Bot", "a", "h", 4)),
                1,
                false));

    ContributorSample sample = service.contributors(REF);

    assertThat(sample.contributors()).hasSize(2);
    assertThat(sample.contributors().get(1).bot()).isTrue();
  }

  @Test
  void contributorLineStats_pendingIsNull_readyIsSummedPerAuthor() {
    when(client.getContributorStats("octocat", "hello")).thenReturn(StatsResult.computing());
    assertThat(service.contributorLineStats(REF)).isNull();

    when(client.getContributorStats("octocat", "hello"))
        .thenReturn(
            StatsResult.ready(
                List.of(
                    new GitHubContributorStats(
                        new GitHubUser("octocat", 1, "User", "a", "h"),
                        3,
                        List.of(
                            new GitHubContributorStats.Week(0, 10, 2, 1),
                            new GitHubContributorStats.Week(1, 5, 1, 2))),
                    // Deleted accounts come back with a null author: skipped.
                    new GitHubContributorStats(null, 1, List.of()))));

    var stats = service.contributorLineStats(REF);

    assertThat(stats).hasSize(1);
    assertThat(stats.getFirst().additions()).isEqualTo(15);
    assertThat(stats.getFirst().deletions()).isEqualTo(3);
    assertThat(stats.getFirst().commits()).isEqualTo(3);
  }

  @Test
  void repositoryRef_isCaseInsensitive() {
    assertThat(new RepositoryRef("OctoCat", "Hello"))
        .isEqualTo(new RepositoryRef("octocat", "hello"));
  }
}
