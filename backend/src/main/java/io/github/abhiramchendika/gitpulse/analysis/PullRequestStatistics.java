package io.github.abhiramchendika.gitpulse.analysis;

import io.github.abhiramchendika.gitpulse.analysis.Rankings.ActorCount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Output of {@link PullRequestAnalyzer}. The counts describe the <em>cohort</em> of pull requests
 * opened in the window: of those, how many were merged, closed without merging, or are still open.
 * See {@code docs/metrics.md}.
 */
public record PullRequestStatistics(
    int opened,
    int merged,
    int closedWithoutMerge,
    int stillOpen,
    int openedByBots,
    /** merged / (merged + closedWithoutMerge) of the cohort; null when none were closed. */
    Double mergedPercentOfClosed,
    /** Created -> merged, for merged pull requests of the cohort; null when none were merged. */
    Durations.Summary timeToMerge,
    List<Week> weekly,
    List<ActorCount> topAuthors,
    List<Recent> recent) {

  /**
   * @param opened pull requests created in this week
   * @param merged pull requests (of the cohort) merged in this week
   */
  public record Week(LocalDate weekStart, int opened, int merged) {}

  /**
   * @param status "open", "merged" or "closed" (closed without merging)
   */
  public record Recent(
      int number,
      String title,
      String authorLogin,
      boolean draft,
      Instant createdAt,
      String status,
      String htmlUrl) {}
}
