package io.github.abhiramchendika.gitpulse.api.dto;

import io.github.abhiramchendika.gitpulse.analysis.PullRequestStatistics;

/**
 * Response of {@code GET /api/v1/repositories/{owner}/{repo}/pull-requests}.
 *
 * @param totals all-time counts (GitHub-provided)
 * @param statistics pull requests opened in the window described by {@code meta}
 */
public record PullRequestAnalyticsResponse(
    String repository, AnalysisMeta meta, Totals totals, PullRequestStatistics statistics) {

  /**
   * @param merged all-time merged count from the Search API; null when unavailable (e.g. the Search
   *     API rate limit was reached)
   * @param closedWithoutMerge {@code closed - merged}; null when {@code merged} is unavailable
   */
  public record Totals(long open, long closed, Long merged, Long closedWithoutMerge) {}
}
