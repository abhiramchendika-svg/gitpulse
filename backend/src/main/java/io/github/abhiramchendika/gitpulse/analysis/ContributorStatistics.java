package io.github.abhiramchendika.gitpulse.analysis;

import java.util.List;

/** Output of {@link ContributorAnalyzer}. */
public record ContributorStatistics(
    int contributorCount,
    long totalCommits,
    double topContributorSharePercent,
    /** Smallest number of contributors whose commits add up to at least half of all commits. */
    int contributorsForHalfOfCommits,
    int botCount,
    LineStatsStatus lineStatsStatus,
    List<Contributor> contributors) {

  /**
   * @param commits all-time default-branch commits (GitHub-provided)
   * @param sharePercent commits / total commits of all listed contributors (GitPulse-calculated)
   * @param additions lines added (GitHub-provided), null when unavailable
   * @param deletions lines deleted (GitHub-provided), null when unavailable
   */
  public record Contributor(
      String login,
      String avatarUrl,
      String htmlUrl,
      boolean bot,
      int commits,
      double sharePercent,
      Long additions,
      Long deletions) {}

  public enum LineStatsStatus {
    /** Line counts are included for (up to) the top 100 contributors. */
    AVAILABLE,
    /** GitHub is still computing statistics; ask again in a few seconds. */
    PENDING,
    /** GitHub does not provide line counts here (e.g. repositories with 10,000+ commits). */
    UNAVAILABLE
  }
}
