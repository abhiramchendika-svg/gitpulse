package io.github.abhiramchendika.gitpulse.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.abhiramchendika.gitpulse.analysis.model.CommitRecord;
import io.github.abhiramchendika.gitpulse.analysis.model.IssueRecord;
import io.github.abhiramchendika.gitpulse.analysis.model.PullRequestRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActivityAnalyzerTest {

  private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
  private static final Instant YEAR_AGO = NOW.minus(Duration.ofDays(365));

  private final ActivityAnalyzer analyzer = new ActivityAnalyzer();

  private static CommitRecord commitDaysAgo(double days) {
    Instant at = NOW.minusSeconds((long) (days * 86_400));
    return new CommitRecord("s" + days, "a", "a", "a", at, at, false, false, "h", "u");
  }

  private static PullRequestRecord prDaysAgo(int createdDaysAgo, Integer mergedDaysAgo) {
    Instant created = NOW.minus(Duration.ofDays(createdDaysAgo));
    Instant merged = mergedDaysAgo == null ? null : NOW.minus(Duration.ofDays(mergedDaysAgo));
    return new PullRequestRecord(1, "t", "a", false, false, created, merged, merged, "u");
  }

  private static IssueRecord issueDaysAgo(int createdDaysAgo, Integer closedDaysAgo) {
    return new IssueRecord(
        1,
        "t",
        "a",
        false,
        NOW.minus(Duration.ofDays(createdDaysAgo)),
        closedDaysAgo == null ? null : NOW.minus(Duration.ofDays(closedDaysAgo)),
        null,
        0,
        "u");
  }

  private ActivityIndicators analyze(
      List<CommitRecord> commits, List<PullRequestRecord> prs, List<IssueRecord> issues) {
    return analyzer.analyze(
        new ActivityAnalyzer.Input(
            NOW, NOW.minusSeconds(3600), commits, YEAR_AGO, prs, YEAR_AGO, issues, YEAR_AGO));
  }

  @Test
  void countsCommitsInLast30And90Days() {
    ActivityIndicators a =
        analyze(
            List.of(commitDaysAgo(1.5), commitDaysAgo(29), commitDaysAgo(31), commitDaysAgo(95)),
            List.of(),
            List.of());

    assertThat(a.commitsLast30Days()).isEqualTo(2);
    assertThat(a.commitsLast90Days()).isEqualTo(3);
    assertThat(a.daysSinceLastCommit()).isEqualTo(1.5);
    assertThat(a.lastPushAt()).isEqualTo(NOW.minusSeconds(3600));
    assertThat(a.commitsPartial()).isFalse();
  }

  @Test
  void activeWeeksUseRollingSevenDayPeriods() {
    // Days 1 and 3 fall in the same period; day 8 in the next; day 83 in the 12th; day 85 outside.
    ActivityIndicators a =
        analyze(
            List.of(
                commitDaysAgo(1),
                commitDaysAgo(3),
                commitDaysAgo(8),
                commitDaysAgo(83),
                commitDaysAgo(85)),
            List.of(),
            List.of());

    assertThat(a.activeWeeksOfLast12()).isEqualTo(3);
  }

  @Test
  void noCommits_meansNullLastCommit() {
    ActivityIndicators a = analyze(List.of(), List.of(), List.of());

    assertThat(a.lastCommitAt()).isNull();
    assertThat(a.daysSinceLastCommit()).isNull();
    assertThat(a.activeWeeksOfLast12()).isZero();
  }

  @Test
  void pullRequestsAndIssuesInLast90Days() {
    ActivityIndicators a =
        analyze(
            List.of(),
            List.of(prDaysAgo(10, 5), prDaysAgo(100, 20), prDaysAgo(95, null), prDaysAgo(1, null)),
            List.of(issueDaysAgo(5, null), issueDaysAgo(200, 30), issueDaysAgo(60, 59)));

    assertThat(a.pullRequestsOpenedLast90Days()).isEqualTo(2);
    // Merged recently even though opened earlier counts as merged in the last 90 days.
    assertThat(a.pullRequestsMergedLast90Days()).isEqualTo(2);
    assertThat(a.issuesOpenedLast90Days()).isEqualTo(2);
    assertThat(a.issuesClosedLast90Days()).isEqualTo(2);
  }

  @Test
  void issuesDisabled_isNullNotZero() {
    ActivityIndicators a = analyze(List.of(), List.of(), null);

    assertThat(a.issuesOpenedLast90Days()).isNull();
    assertThat(a.issuesClosedLast90Days()).isNull();
    assertThat(a.issuesPartial()).isFalse();
  }

  @Test
  void samplesNotReachingBack90Days_areFlaggedPartial() {
    Instant tenDaysAgo = NOW.minus(Duration.ofDays(10));
    ActivityIndicators a =
        analyzer.analyze(
            new ActivityAnalyzer.Input(
                NOW, null, List.of(), tenDaysAgo, List.of(), YEAR_AGO, List.of(), tenDaysAgo));

    assertThat(a.commitsPartial()).isTrue();
    assertThat(a.pullRequestsPartial()).isFalse();
    assertThat(a.issuesPartial()).isTrue();
  }
}
