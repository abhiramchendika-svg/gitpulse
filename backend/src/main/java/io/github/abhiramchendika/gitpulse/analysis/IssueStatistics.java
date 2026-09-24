package io.github.abhiramchendika.gitpulse.analysis;

import io.github.abhiramchendika.gitpulse.analysis.Rankings.ActorCount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Output of {@link IssueAnalyzer}. Counts describe the <em>cohort</em> of issues opened in the
 * window. See {@code docs/metrics.md}.
 */
public record IssueStatistics(
    int opened,
    int closed,
    int stillOpen,
    int openedByBots,
    /** Closed with GitHub's reason "completed". */
    int closedAsCompleted,
    /** Closed with GitHub's reason "not_planned". */
    int closedAsNotPlanned,
    /** Closed as "duplicate", or with no reason recorded. */
    int closedOther,
    /** Created -> closed, for closed issues of the cohort; null when none were closed. */
    Durations.Summary timeToClose,
    List<Week> weekly,
    List<ActorCount> topOpeners,
    List<Recent> recent) {

  /**
   * @param opened issues created in this week
   * @param closed issues (of the cohort) closed in this week
   */
  public record Week(LocalDate weekStart, int opened, int closed) {}

  public record Recent(
      int number,
      String title,
      String authorLogin,
      Instant createdAt,
      boolean open,
      int comments,
      String htmlUrl) {}
}
