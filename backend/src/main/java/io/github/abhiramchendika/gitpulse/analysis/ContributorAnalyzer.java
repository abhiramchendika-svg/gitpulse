package io.github.abhiramchendika.gitpulse.analysis;

import io.github.abhiramchendika.gitpulse.analysis.ContributorStatistics.Contributor;
import io.github.abhiramchendika.gitpulse.analysis.ContributorStatistics.LineStatsStatus;
import io.github.abhiramchendika.gitpulse.analysis.model.ContributorLineStats;
import io.github.abhiramchendika.gitpulse.analysis.model.ContributorRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Calculates contribution distribution from GitHub's contributor list. Pure; no I/O. */
public class ContributorAnalyzer {

  private final int contributorLimit;

  public ContributorAnalyzer(int contributorLimit) {
    this.contributorLimit = contributorLimit;
  }

  /**
   * @param lineStats per-contributor line statistics, or {@code null} if GitHub is still computing
   *     them (HTTP 202)
   */
  public ContributorStatistics analyze(
      List<ContributorRecord> records, List<ContributorLineStats> lineStats) {
    List<ContributorRecord> sorted =
        records.stream()
            .sorted(
                Comparator.comparingInt(ContributorRecord::commits)
                    .reversed()
                    .thenComparing(ContributorRecord::login))
            .toList();
    long total = sorted.stream().mapToLong(ContributorRecord::commits).sum();

    LineStatsStatus status = lineStatsStatus(lineStats);
    Map<String, ContributorLineStats> linesByLogin =
        status == LineStatsStatus.AVAILABLE
            ? lineStats.stream()
                .collect(
                    Collectors.toMap(ContributorLineStats::login, Function.identity(), (a, b) -> a))
            : Map.of();

    List<Contributor> contributors =
        sorted.stream()
            .limit(contributorLimit)
            .map(
                c -> {
                  ContributorLineStats lines = linesByLogin.get(c.login());
                  return new Contributor(
                      c.login(),
                      c.avatarUrl(),
                      c.htmlUrl(),
                      c.bot(),
                      c.commits(),
                      Numbers.percent(c.commits(), total),
                      lines == null ? null : lines.additions(),
                      lines == null ? null : lines.deletions());
                })
            .toList();

    return new ContributorStatistics(
        sorted.size(),
        total,
        sorted.isEmpty() ? 0 : Numbers.percent(sorted.getFirst().commits(), total),
        contributorsForHalf(sorted, total),
        (int) sorted.stream().filter(ContributorRecord::bot).count(),
        status,
        contributors);
  }

  /**
   * Walks contributors from most to fewest commits, accumulating a running total (a prefix sum),
   * and returns how many it takes to reach 50% of all commits.
   */
  static int contributorsForHalf(List<ContributorRecord> sortedDescending, long total) {
    if (total == 0) {
      return 0;
    }
    long running = 0;
    int count = 0;
    for (ContributorRecord contributor : sortedDescending) {
      running += contributor.commits();
      count++;
      if (running * 2 >= total) {
        break;
      }
    }
    return count;
  }

  /**
   * GitHub returns zeros for additions/deletions on repositories with 10,000+ commits. Zeros
   * alongside non-zero commit counts therefore mean "not provided", not "no lines changed".
   */
  static LineStatsStatus lineStatsStatus(List<ContributorLineStats> lineStats) {
    if (lineStats == null) {
      return LineStatsStatus.PENDING;
    }
    long commits = lineStats.stream().mapToLong(ContributorLineStats::commits).sum();
    long lines = lineStats.stream().mapToLong(s -> s.additions() + s.deletions()).sum();
    if (lineStats.isEmpty() || (commits > 0 && lines == 0)) {
      return LineStatsStatus.UNAVAILABLE;
    }
    return LineStatsStatus.AVAILABLE;
  }
}
