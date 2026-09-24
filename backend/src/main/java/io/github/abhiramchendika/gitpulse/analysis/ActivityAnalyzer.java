package io.github.abhiramchendika.gitpulse.analysis;

import io.github.abhiramchendika.gitpulse.analysis.model.CommitRecord;
import io.github.abhiramchendika.gitpulse.analysis.model.IssueRecord;
import io.github.abhiramchendika.gitpulse.analysis.model.PullRequestRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Recent-activity indicators over fixed look-back periods ending at {@code now}. Pure; the caller
 * supplies "now" so results are reproducible in tests.
 */
public class ActivityAnalyzer {

  static final Duration DAYS_30 = Duration.ofDays(30);
  static final Duration DAYS_90 = Duration.ofDays(90);

  /**
   * Samples of recent data and how far back each one fully covers.
   *
   * @param issues null when the repository has issues disabled
   * @param commitsCoveredFrom the earliest instant from which the commit sample is complete
   */
  public record Input(
      Instant now,
      Instant lastPushAt,
      List<CommitRecord> commits,
      Instant commitsCoveredFrom,
      List<PullRequestRecord> pullRequests,
      Instant pullRequestsCoveredFrom,
      List<IssueRecord> issues,
      Instant issuesCoveredFrom) {}

  public ActivityIndicators analyze(Input in) {
    Instant now = in.now();
    Instant d30 = now.minus(DAYS_30);
    Instant d90 = now.minus(DAYS_90);

    Instant lastCommit =
        in.commits().stream()
            .map(CommitRecord::authoredAt)
            .filter(t -> !t.isAfter(now))
            .max(Comparator.naturalOrder())
            .orElse(null);

    return new ActivityIndicators(
        lastCommit,
        lastCommit == null
            ? null
            : Numbers.round(Duration.between(lastCommit, now).toSeconds() / 86_400.0, 1),
        in.lastPushAt(),
        countBetween(in.commits().stream().map(CommitRecord::authoredAt).toList(), d30, now),
        countBetween(in.commits().stream().map(CommitRecord::authoredAt).toList(), d90, now),
        activeWeeks(in.commits(), now),
        in.commitsCoveredFrom().isAfter(d90),
        countBetween(
            in.pullRequests().stream().map(PullRequestRecord::createdAt).toList(), d90, now),
        countBetween(
            in.pullRequests().stream()
                .map(PullRequestRecord::mergedAt)
                .filter(Objects::nonNull)
                .toList(),
            d90,
            now),
        in.pullRequestsCoveredFrom().isAfter(d90),
        in.issues() == null
            ? null
            : countBetween(in.issues().stream().map(IssueRecord::createdAt).toList(), d90, now),
        in.issues() == null
            ? null
            : countBetween(
                in.issues().stream().map(IssueRecord::closedAt).filter(Objects::nonNull).toList(),
                d90,
                now),
        in.issues() != null && in.issuesCoveredFrom().isAfter(d90));
  }

  /** Counts instants in {@code (from, to]}. */
  static int countBetween(List<Instant> instants, Instant from, Instant to) {
    return (int) instants.stream().filter(t -> t.isAfter(from) && !t.isAfter(to)).count();
  }

  /**
   * Splits the last 84 days into 12 consecutive 7-day periods ending now (rolling weeks, not
   * calendar weeks, so the current partial week does not distort the count) and counts those with
   * at least one commit.
   */
  static int activeWeeks(List<CommitRecord> commits, Instant now) {
    return (int)
        IntStream.range(0, 12)
            .filter(
                i -> {
                  Instant to = now.minus(Duration.ofDays(7L * i));
                  Instant from = to.minus(Duration.ofDays(7));
                  return commits.stream()
                      .anyMatch(c -> c.authoredAt().isAfter(from) && !c.authoredAt().isAfter(to));
                })
            .count();
  }
}
