package io.github.abhiramchendika.gitpulse.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.abhiramchendika.gitpulse.analysis.CommitStatistics.AuthorActivity;
import io.github.abhiramchendika.gitpulse.analysis.CommitStatistics.InactivityPeriod;
import io.github.abhiramchendika.gitpulse.analysis.CommitStatistics.MonthCount;
import io.github.abhiramchendika.gitpulse.analysis.CommitStatistics.WeekCount;
import io.github.abhiramchendika.gitpulse.analysis.model.AnalysisWindow;
import io.github.abhiramchendika.gitpulse.analysis.model.CommitRecord;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommitAnalyzerTest {

  private final CommitAnalyzer analyzer = new CommitAnalyzer(Duration.ofDays(14), 3, 2, 10);

  private static int shaCounter = 0;

  private static CommitRecord commit(String login, String when) {
    return commit(login, when, false, false);
  }

  private static CommitRecord commit(String login, String when, boolean merge, boolean bot) {
    String sha = String.format("%040d", ++shaCounter);
    Instant at = Instant.parse(when);
    return new CommitRecord(sha, login, login, login, at, at, merge, bot, "msg " + sha, "url");
  }

  private static AnalysisWindow window(String since, String until) {
    return new AnalysisWindow(Instant.parse(since), Instant.parse(until));
  }

  @Test
  void emptyInput_producesZeroFilledSeriesAndNoGaps() {
    // 2026-01-05 is a Monday; two full weeks.
    CommitStatistics stats =
        analyzer.analyze(List.of(), window("2026-01-05T00:00:00Z", "2026-01-19T00:00:00Z"));

    assertThat(stats.totalCommits()).isZero();
    assertThat(stats.weekly()).extracting(WeekCount::commits).containsExactly(0, 0);
    assertThat(stats.averagePerWeek()).isZero();
    assertThat(stats.firstCommitAt()).isNull();
    assertThat(stats.lastCommitAt()).isNull();
    assertThat(stats.inactivityPeriods()).isEmpty();
    assertThat(stats.longestInactivity()).isNull();
    assertThat(stats.recentCommits()).isEmpty();
    assertThat(stats.activeWeeks()).isZero();
  }

  @Test
  void countsTotalsMergesBotsAndDistinctAuthors() {
    List<CommitRecord> commits =
        List.of(
            commit("alice", "2026-01-05T10:00:00Z"),
            commit("alice", "2026-01-06T10:00:00Z", true, false),
            commit("bob", "2026-01-07T10:00:00Z"),
            commit("dependabot[bot]", "2026-01-08T10:00:00Z", false, true));

    CommitStatistics stats =
        analyzer.analyze(commits, window("2026-01-05T00:00:00Z", "2026-01-12T00:00:00Z"));

    assertThat(stats.totalCommits()).isEqualTo(4);
    assertThat(stats.mergeCommits()).isEqualTo(1);
    assertThat(stats.botCommits()).isEqualTo(1);
    assertThat(stats.distinctAuthors()).isEqualTo(3);
  }

  @Test
  void windowIsHalfOpen_includesSinceExcludesUntil() {
    List<CommitRecord> commits =
        List.of(
            commit("a", "2026-01-04T23:59:59Z"), // before
            commit("a", "2026-01-05T00:00:00Z"), // exactly since: included
            commit("a", "2026-01-12T00:00:00Z")); // exactly until: excluded

    CommitStatistics stats =
        analyzer.analyze(commits, window("2026-01-05T00:00:00Z", "2026-01-12T00:00:00Z"));

    assertThat(stats.totalCommits()).isEqualTo(1);
  }

  @Test
  void weeklyBucketsStartOnMondayUtc() {
    List<CommitRecord> commits =
        List.of(
            commit("a", "2026-01-05T00:00:00Z"), // Monday, week 1
            commit("a", "2026-01-11T23:59:59Z"), // Sunday, still week 1
            commit("a", "2026-01-12T00:00:00Z")); // next Monday, week 2

    CommitStatistics stats =
        analyzer.analyze(commits, window("2026-01-05T00:00:00Z", "2026-01-19T00:00:00Z"));

    assertThat(stats.weekly())
        .containsExactly(
            new WeekCount(LocalDate.parse("2026-01-05"), 2),
            new WeekCount(LocalDate.parse("2026-01-12"), 1));
    assertThat(stats.activeWeeks()).isEqualTo(2);
    assertThat(stats.totalWeeks()).isEqualTo(2);
  }

  @Test
  void weekSpanningNewYear_isOneBucket() {
    // Thursday 2026-01-01 belongs to the week starting Monday 2025-12-29.
    CommitStatistics stats =
        analyzer.analyze(
            List.of(commit("a", "2026-01-01T12:00:00Z")),
            window("2025-12-29T00:00:00Z", "2026-01-05T00:00:00Z"));

    assertThat(stats.weekly()).containsExactly(new WeekCount(LocalDate.parse("2025-12-29"), 1));
    assertThat(stats.monthly())
        .containsExactly(
            new MonthCount(YearMonth.parse("2025-12"), 0),
            new MonthCount(YearMonth.parse("2026-01"), 1));
  }

  @Test
  void monthlyBucketsAreZeroFilled() {
    CommitStatistics stats =
        analyzer.analyze(
            List.of(commit("a", "2026-01-15T00:00:00Z"), commit("a", "2026-03-15T00:00:00Z")),
            window("2026-01-01T00:00:00Z", "2026-04-01T00:00:00Z"));

    assertThat(stats.monthly()).extracting(MonthCount::commits).containsExactly(1, 0, 1);
  }

  @Test
  void heatmapUsesUtcDayAndHour() {
    // 2026-01-05 is Monday; 2026-01-11 is Sunday.
    CommitStatistics stats =
        analyzer.analyze(
            List.of(commit("a", "2026-01-05T14:30:00Z"), commit("a", "2026-01-11T23:00:00Z")),
            window("2026-01-05T00:00:00Z", "2026-01-12T00:00:00Z"));

    assertThat(stats.heatmap().get(0).get(14)).isEqualTo(1);
    assertThat(stats.heatmap().get(6).get(23)).isEqualTo(1);
    assertThat(stats.byDayOfWeek()).containsExactly(1, 0, 0, 0, 0, 0, 1);
    assertThat(stats.byHourOfDay().get(14)).isEqualTo(1);
    assertThat(stats.byHourOfDay().stream().mapToInt(Integer::intValue).sum()).isEqualTo(2);
  }

  @Test
  void averagesAreOverTheWholeWindowIncludingQuietWeeks() {
    List<CommitRecord> commits = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      commits.add(commit("a", "2026-01-05T0" + i + ":00:00Z")); // all in the first week
    }

    // 28-day window: 8 commits / 4 weeks = 2.0 per week.
    CommitStatistics stats =
        analyzer.analyze(commits, window("2026-01-05T00:00:00Z", "2026-02-02T00:00:00Z"));

    assertThat(stats.averagePerWeek()).isEqualTo(2.0);
    // 8 / (28 / 30.436875) = 8.696...
    assertThat(stats.averagePerMonth()).isEqualTo(8.7);
    assertThat(stats.activeWeeks()).isEqualTo(1);
    assertThat(stats.totalWeeks()).isEqualTo(4);
  }

  @Test
  void topAuthors_sortedByCommitsThenKey_withShareAndLimit() {
    List<CommitRecord> commits =
        List.of(
            commit("carol", "2026-01-05T01:00:00Z"),
            commit("bob", "2026-01-05T02:00:00Z"),
            commit("bob", "2026-01-05T03:00:00Z"),
            commit("alice", "2026-01-05T04:00:00Z"),
            commit("dave", "2026-01-05T05:00:00Z"));

    CommitStatistics stats =
        analyzer.analyze(commits, window("2026-01-05T00:00:00Z", "2026-01-12T00:00:00Z"));

    // Limit is 3; ties (1 commit each) are broken alphabetically.
    assertThat(stats.topAuthors())
        .extracting(AuthorActivity::key, AuthorActivity::commits, AuthorActivity::sharePercent)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("bob", 2, 40.0),
            org.assertj.core.groups.Tuple.tuple("alice", 1, 20.0),
            org.assertj.core.groups.Tuple.tuple("carol", 1, 20.0));
  }

  @Test
  void inactivity_reportsGapsLongerThanThresholdAndOngoingTrailingGap() {
    List<CommitRecord> commits =
        List.of(
            commit("a", "2026-01-01T00:00:00Z"),
            commit("a", "2026-01-03T00:00:00Z"), // 2-day gap: ignored
            commit("a", "2026-01-20T00:00:00Z")); // 17-day gap: reported

    // Window ends Feb 10: 21 days since the last commit -> ongoing gap.
    CommitStatistics stats =
        analyzer.analyze(commits, window("2026-01-01T00:00:00Z", "2026-02-10T00:00:00Z"));

    assertThat(stats.inactivityPeriods())
        .extracting(InactivityPeriod::days, InactivityPeriod::ongoing)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(17.0, false),
            org.assertj.core.groups.Tuple.tuple(21.0, true));
    assertThat(stats.longestInactivity().days()).isEqualTo(21.0);
  }

  @Test
  void inactivity_gapOfExactlyThreshold_isNotReported() {
    CommitStatistics stats =
        analyzer.analyze(
            List.of(commit("a", "2026-01-01T00:00:00Z"), commit("a", "2026-01-15T00:00:00Z")),
            window("2026-01-01T00:00:00Z", "2026-01-16T00:00:00Z"));

    assertThat(stats.inactivityPeriods()).isEmpty();
  }

  @Test
  void inactivity_gapBeforeFirstCommitIsIgnored() {
    // Repository may not have existed at the start of the window.
    CommitStatistics stats =
        analyzer.analyze(
            List.of(commit("a", "2026-03-01T00:00:00Z")),
            window("2026-01-01T00:00:00Z", "2026-03-02T00:00:00Z"));

    assertThat(stats.inactivityPeriods()).isEmpty();
  }

  @Test
  void recentCommits_newestFirst_limited_withShortSha() {
    List<CommitRecord> commits =
        List.of(
            commit("a", "2026-01-05T01:00:00Z"),
            commit("a", "2026-01-05T03:00:00Z"),
            commit("a", "2026-01-05T02:00:00Z"));

    CommitStatistics stats =
        analyzer.analyze(commits, window("2026-01-05T00:00:00Z", "2026-01-12T00:00:00Z"));

    assertThat(stats.recentCommits()).hasSize(2);
    assertThat(stats.recentCommits().get(0).authoredAt())
        .isEqualTo(Instant.parse("2026-01-05T03:00:00Z"));
    assertThat(stats.recentCommits().get(1).authoredAt())
        .isEqualTo(Instant.parse("2026-01-05T02:00:00Z"));
    assertThat(stats.recentCommits().get(0).shortSha()).hasSize(7);
    assertThat(stats.firstCommitAt()).isEqualTo(Instant.parse("2026-01-05T01:00:00Z"));
    assertThat(stats.lastCommitAt()).isEqualTo(Instant.parse("2026-01-05T03:00:00Z"));
  }

  @Test
  void weekStart_isPreviousOrSameMonday() {
    assertThat(CommitAnalyzer.weekStart(Instant.parse("2026-01-05T00:00:00Z")))
        .isEqualTo(LocalDate.parse("2026-01-05"));
    assertThat(CommitAnalyzer.weekStart(Instant.parse("2026-01-11T23:59:59Z")))
        .isEqualTo(LocalDate.parse("2026-01-05"));
  }
}
