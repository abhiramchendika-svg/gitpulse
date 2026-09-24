package io.github.abhiramchendika.gitpulse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.abhiramchendika.gitpulse.analysis.CommitAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.model.CommitRecord;
import io.github.abhiramchendika.gitpulse.api.dto.CommitAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.config.AnalysisProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommitAnalyticsServiceTest {

  private static final RepositoryRef REF = new RepositoryRef("octocat", "hello");
  // Fixed "now": 2026-09-25 12:00 UTC.
  private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
  private static final LocalDate TODAY = LocalDate.parse("2026-09-25");

  private final RepositoryDataService data = mock(RepositoryDataService.class);
  private final CommitAnalyticsService service =
      new CommitAnalyticsService(
          data,
          new CommitAnalyzer(Duration.ofDays(14), 10, 10, 10),
          new AnalysisWindows(
              new AnalysisProperties(10, 5, 365, 3650, Duration.ofDays(14), 10, 10, 10, 100, 5, 5),
              Clock.fixed(NOW, ZoneOffset.UTC)));

  private static CommitRecord commit(String when, boolean bot) {
    return commit(when, when, bot);
  }

  private static CommitRecord commit(String authored, String committed, boolean bot) {
    return new CommitRecord(
        "sha" + authored + committed,
        bot ? "bot[bot]" : "alice",
        "n",
        "l",
        Instant.parse(authored),
        Instant.parse(committed),
        false,
        bot,
        "h",
        "u");
  }

  @Test
  void invalidRange_isRejectedBeforeAnyGitHubCall() {
    assertThatThrownBy(() -> service.analyze(REF, TODAY, TODAY.minusDays(1), false))
        .isInstanceOf(InvalidRequestException.class);
    verify(data, never())
        .commits(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void requestsDayAlignedWindow_butAnalysesOnlyUpToNow() {
    Instant since = Instant.parse("2026-09-01T00:00:00Z");
    Instant untilExclusive = Instant.parse("2026-09-26T00:00:00Z");
    when(data.commits(REF, since, untilExclusive))
        .thenReturn(new CommitSample(List.of(commit("2026-09-10T00:00:00Z", false)), false, false));
    when(data.commitCount(REF)).thenReturn(42L);

    CommitAnalyticsResponse response =
        service.analyze(REF, LocalDate.parse("2026-09-01"), null, false);

    assertThat(response.meta().since()).isEqualTo(since);
    assertThat(response.meta().until()).isEqualTo(NOW);
    assertThat(response.totalCommitsAllTime()).isEqualTo(42);
    // Ongoing gap measured to "now", not to midnight tomorrow: Sep 10 00:00 -> Sep 25 12:00.
    assertThat(response.statistics().longestInactivity().days()).isEqualTo(15.5);
  }

  @Test
  void truncatedSample_shrinksWindowToOldestFetchedCommit() {
    Instant since = Instant.parse("2025-09-26T00:00:00Z");
    when(data.commits(REF, since, Instant.parse("2026-09-26T00:00:00Z")))
        .thenReturn(
            new CommitSample(
                List.of(
                    commit("2026-09-20T00:00:00Z", false), commit("2026-09-01T06:00:00Z", false)),
                true,
                false));

    CommitAnalyticsResponse response = service.analyze(REF, null, null, false);

    assertThat(response.meta().truncated()).isTrue();
    assertThat(response.meta().requestedSince()).isEqualTo(since);
    assertThat(response.meta().since()).isEqualTo(Instant.parse("2026-09-01T06:00:00Z"));
    // Weeks before the oldest fetched commit are not reported as "zero commits".
    assertThat(response.statistics().weekly().getFirst().weekStart())
        .isEqualTo(LocalDate.parse("2026-08-31"));
  }

  /**
   * Regression test for a bug found against torvalds/linux: patches authored long before they are
   * committed made the oldest <em>author</em> date fall before the requested start, so the window
   * was not shortened and early weeks were silently under-counted.
   */
  @Test
  void truncatedSample_usesOldestCommitDate_notAuthorDate() {
    Instant since = Instant.parse("2025-09-26T00:00:00Z");
    when(data.commits(REF, since, Instant.parse("2026-09-26T00:00:00Z")))
        .thenReturn(
            new CommitSample(
                List.of(
                    commit("2026-09-20T00:00:00Z", "2026-09-20T00:00:00Z", false),
                    // Authored a year ago (before the window), landed on Sep 10.
                    commit("2025-08-01T00:00:00Z", "2026-09-10T08:00:00Z", false)),
                true,
                false));

    CommitAnalyticsResponse response = service.analyze(REF, null, null, false);

    assertThat(response.meta().since()).isEqualTo(Instant.parse("2026-09-10T08:00:00Z"));
    // The old-authored commit falls outside the (shortened) window and is not counted.
    assertThat(response.statistics().totalCommits()).isEqualTo(1);
  }

  @Test
  void excludeBots_removesBotCommitsButReportsFullSampleSize() {
    when(data.commits(
            REF, Instant.parse("2025-09-26T00:00:00Z"), Instant.parse("2026-09-26T00:00:00Z")))
        .thenReturn(
            new CommitSample(
                List.of(
                    commit("2026-09-20T00:00:00Z", false), commit("2026-09-21T00:00:00Z", true)),
                false,
                false));

    CommitAnalyticsResponse response = service.analyze(REF, null, null, true);

    assertThat(response.statistics().totalCommits()).isEqualTo(1);
    assertThat(response.statistics().botCommits()).isZero();
    assertThat(response.meta().sampleSize()).isEqualTo(2);
    assertThat(response.meta().botsExcluded()).isTrue();
  }

  @Test
  void emptyRepository_skipsCountRequest() {
    when(data.commits(
            REF, Instant.parse("2025-09-26T00:00:00Z"), Instant.parse("2026-09-26T00:00:00Z")))
        .thenReturn(new CommitSample(List.of(), false, true));

    CommitAnalyticsResponse response = service.analyze(REF, null, null, false);

    assertThat(response.emptyRepository()).isTrue();
    assertThat(response.totalCommitsAllTime()).isZero();
    assertThat(response.statistics().totalCommits()).isZero();
    verify(data, never()).commitCount(REF);
  }
}
