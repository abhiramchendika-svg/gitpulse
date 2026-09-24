package io.github.abhiramchendika.gitpulse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.abhiramchendika.gitpulse.analysis.ActivityAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.IssueAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.PullRequestAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.model.IssueRecord;
import io.github.abhiramchendika.gitpulse.analysis.model.PullRequestRecord;
import io.github.abhiramchendika.gitpulse.api.dto.ActivityResponse;
import io.github.abhiramchendika.gitpulse.api.dto.IssueAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.api.dto.PullRequestAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.config.AnalysisProperties;
import io.github.abhiramchendika.gitpulse.github.GitHubClient;
import io.github.abhiramchendika.gitpulse.github.PagedResult;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubRateLimitException;
import io.github.abhiramchendika.gitpulse.github.model.GitHubIssue;
import io.github.abhiramchendika.gitpulse.github.model.GitHubRepository;
import io.github.abhiramchendika.gitpulse.github.model.GitHubUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Services for pull requests, issues and activity, with the data layer mocked. */
class PullRequestAndIssueServiceTest {

  private static final RepositoryRef REF = new RepositoryRef("o", "r");
  private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

  /** Default window start: 365 days including today. */
  private static final Instant DEFAULT_SINCE = Instant.parse("2025-09-26T00:00:00Z");

  private static final AnalysisProperties PROPS =
      new AnalysisProperties(10, 5, 365, 3650, Duration.ofDays(14), 10, 10, 10, 100, 5, 5);

  private final RepositoryDataService data = mock(RepositoryDataService.class);
  private final AnalysisWindows windows =
      new AnalysisWindows(PROPS, Clock.fixed(NOW, ZoneOffset.UTC));

  private static GitHubRepository repo(int openIssuesAndPrs, Boolean hasIssues) {
    return new GitHubRepository(
        "r",
        "o/r",
        null,
        null,
        "h",
        null,
        0,
        0,
        0,
        openIssuesAndPrs,
        null,
        null,
        List.of(),
        NOW,
        NOW,
        NOW,
        0,
        "main",
        false,
        false,
        hasIssues);
  }

  @Test
  void pullRequestTotals_includeMergedAndDeriveClosedWithoutMerge() {
    when(data.pullRequests(REF, DEFAULT_SINCE)).thenReturn(new PullRequestSample(List.of(), false));
    when(data.openPullRequestCount(REF)).thenReturn(5L);
    when(data.closedPullRequestCount(REF)).thenReturn(100L);
    when(data.mergedPullRequestCount(REF)).thenReturn(80L);

    PullRequestAnalyticsResponse r =
        new PullRequestAnalyticsService(data, new PullRequestAnalyzer(10, 10), windows)
            .analyze(REF, null, null, false);

    assertThat(r.totals()).isEqualTo(new PullRequestAnalyticsResponse.Totals(5, 100, 80L, 20L));
  }

  @Test
  void mergedUnavailable_leavesDerivedValueUnavailableToo() {
    when(data.pullRequests(REF, DEFAULT_SINCE)).thenReturn(new PullRequestSample(List.of(), false));
    when(data.openPullRequestCount(REF)).thenReturn(5L);
    when(data.closedPullRequestCount(REF)).thenReturn(100L);
    when(data.mergedPullRequestCount(REF)).thenReturn(null);

    PullRequestAnalyticsResponse r =
        new PullRequestAnalyticsService(data, new PullRequestAnalyzer(10, 10), windows)
            .analyze(REF, null, null, false);

    assertThat(r.totals().merged()).isNull();
    assertThat(r.totals().closedWithoutMerge()).isNull();
    assertThat(r.totals().open()).isEqualTo(5);
  }

  @Test
  void excludeBots_removesBotPullRequests() {
    PullRequestRecord human =
        new PullRequestRecord(1, "t", "a", false, false, NOW.minusSeconds(60), null, null, "u");
    PullRequestRecord bot =
        new PullRequestRecord(2, "t", "b[bot]", true, false, NOW.minusSeconds(60), null, null, "u");
    when(data.pullRequests(REF, DEFAULT_SINCE))
        .thenReturn(new PullRequestSample(List.of(human, bot), false));

    PullRequestAnalyticsResponse r =
        new PullRequestAnalyticsService(data, new PullRequestAnalyzer(10, 10), windows)
            .analyze(REF, null, null, true);

    assertThat(r.statistics().opened()).isEqualTo(1);
    assertThat(r.meta().sampleSize()).isEqualTo(2);
  }

  @Test
  void issueTotals_subtractOpenPullRequestsThatGitHubCountsAsIssues() {
    when(data.repository(REF)).thenReturn(repo(12, true)); // 12 open issues + PRs
    when(data.issues(REF, DEFAULT_SINCE)).thenReturn(new IssueSample(List.of(), null, false));
    when(data.openPullRequestCount(REF)).thenReturn(5L);
    when(data.closedIssueCount(REF)).thenReturn(60L);

    IssueAnalyticsResponse r =
        new IssueAnalyticsService(data, new IssueAnalyzer(10, 10), windows)
            .analyze(REF, null, null, false);

    assertThat(r.issuesEnabled()).isTrue();
    assertThat(r.totals()).isEqualTo(new IssueAnalyticsResponse.Totals(7, 60L));
  }

  @Test
  void closedIssueCountUnavailable_isNullNotZero() {
    when(data.repository(REF)).thenReturn(repo(12, true));
    when(data.issues(REF, DEFAULT_SINCE)).thenReturn(new IssueSample(List.of(), null, false));
    when(data.openPullRequestCount(REF)).thenReturn(5L);
    when(data.closedIssueCount(REF)).thenReturn(null);

    IssueAnalyticsResponse r =
        new IssueAnalyticsService(data, new IssueAnalyzer(10, 10), windows)
            .analyze(REF, null, null, false);

    assertThat(r.totals().closed()).isNull();
    assertThat(r.totals().open()).isEqualTo(7);
  }

  @Test
  void issuesDisabled_makesNoIssueRequests() {
    when(data.repository(REF)).thenReturn(repo(3, false));

    IssueAnalyticsResponse r =
        new IssueAnalyticsService(data, new IssueAnalyzer(10, 10), windows)
            .analyze(REF, null, null, false);

    assertThat(r.issuesEnabled()).isFalse();
    assertThat(r.totals()).isNull();
    assertThat(r.statistics()).isNull();
    verify(data, never()).issues(any(), any());
    verify(data, never()).closedIssueCount(any());
  }

  @Test
  void missingHasIssuesField_meansEnabled() {
    assertThat(repo(0, null).issuesEnabled()).isTrue();
  }

  @Test
  void activity_reusesTheDefaultWindowSamples() {
    Instant defaultUntil = Instant.parse("2026-09-26T00:00:00Z");
    when(data.repository(REF)).thenReturn(repo(0, true));
    when(data.commits(REF, DEFAULT_SINCE, defaultUntil))
        .thenReturn(new CommitSample(List.of(), false, false));
    when(data.pullRequests(REF, DEFAULT_SINCE)).thenReturn(new PullRequestSample(List.of(), false));
    when(data.issues(REF, DEFAULT_SINCE))
        .thenReturn(
            new IssueSample(
                List.of(
                    new IssueRecord(
                        1, "t", "a", false, NOW.minusSeconds(86400), null, null, 0, "u")),
                null,
                false));

    ActivityResponse r = new ActivityService(data, new ActivityAnalyzer(), windows).analyze(REF);

    // Same cache keys as the dashboard's default commit/PR/issue views: no extra GitHub calls.
    verify(data).commits(REF, DEFAULT_SINCE, defaultUntil);
    verify(data).pullRequests(REF, DEFAULT_SINCE);
    assertThat(r.indicators().issuesOpenedLast90Days()).isEqualTo(1);
    assertThat(r.generatedAt()).isEqualTo(NOW);
  }

  // --- Data layer ---

  private final GitHubClient client = mock(GitHubClient.class);
  private final RepositoryDataService realData = new RepositoryDataService(client, PROPS);

  @Test
  void issues_removePullRequests_butTheyStillDefineHowFarBackTheSampleReaches() {
    GitHubUser user = new GitHubUser("a", 1, "User", "x", "y");
    GitHubIssue pr =
        new GitHubIssue(
            2,
            "PR",
            "open",
            null,
            user,
            0,
            Instant.parse("2026-01-01T00:00:00Z"),
            null,
            "h",
            Map.of("url", "x"));
    GitHubIssue issue =
        new GitHubIssue(
            1,
            "Bug",
            "open",
            null,
            user,
            0,
            Instant.parse("2026-03-01T00:00:00Z"),
            null,
            "h",
            null);
    when(client.listIssues(eq("o"), eq("r"), any(), anyInt()))
        .thenReturn(new PagedResult<>(List.of(issue, pr), 5, true));

    IssueSample sample = realData.issues(REF, DEFAULT_SINCE);

    assertThat(sample.issues()).extracting(IssueRecord::number).containsExactly(1);
    assertThat(sample.oldestFetchedCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(sample.truncated()).isTrue();
  }

  @Test
  void mergedCount_isNullWhenTheSearchQuotaIsExhausted() {
    when(client.searchIssueCount(any())).thenThrow(new GitHubRateLimitException(NOW, null));

    assertThat(realData.mergedPullRequestCount(REF)).isNull();
  }
}
