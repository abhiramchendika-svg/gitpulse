package io.github.abhiramchendika.gitpulse.analysis;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Output of {@link CommitAnalyzer}. All times are UTC. Every value here is calculated by GitPulse
 * from the commit sample; see {@code docs/metrics.md} for the formulas.
 */
public record CommitStatistics(
    int totalCommits,
    int mergeCommits,
    int botCommits,
    int distinctAuthors,
    Instant firstCommitAt,
    Instant lastCommitAt,
    double averagePerWeek,
    double averagePerMonth,
    int activeWeeks,
    int totalWeeks,
    List<WeekCount> weekly,
    List<MonthCount> monthly,
    /** Index 0 = Monday ... 6 = Sunday. */
    List<Integer> byDayOfWeek,
    /** Index 0..23 = hour of day (UTC). */
    List<Integer> byHourOfDay,
    /** {@code heatmap.get(day).get(hour)}; day index 0 = Monday. */
    List<List<Integer>> heatmap,
    List<AuthorActivity> topAuthors,
    List<InactivityPeriod> inactivityPeriods,
    InactivityPeriod longestInactivity,
    List<RecentCommit> recentCommits) {

  /** Commits in the ISO week (Monday–Sunday, UTC) starting on {@code weekStart}. */
  public record WeekCount(LocalDate weekStart, int commits) {}

  public record MonthCount(YearMonth month, int commits) {}

  public record AuthorActivity(
      String key, String name, String login, boolean bot, int commits, double sharePercent) {}

  /**
   * A stretch with no commits longer than the inactivity threshold.
   *
   * @param ongoing true if the period runs to the end of the window (no commit since {@code from})
   */
  public record InactivityPeriod(Instant from, Instant to, double days, boolean ongoing) {}

  public record RecentCommit(
      String sha,
      String shortSha,
      String headline,
      String authorName,
      String authorLogin,
      Instant authoredAt,
      boolean merge,
      String htmlUrl) {}
}
