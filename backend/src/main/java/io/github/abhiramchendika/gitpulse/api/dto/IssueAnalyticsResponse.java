package io.github.abhiramchendika.gitpulse.api.dto;

import io.github.abhiramchendika.gitpulse.analysis.IssueStatistics;

/**
 * Response of {@code GET /api/v1/repositories/{owner}/{repo}/issues}. Pull requests are excluded
 * everywhere, even though GitHub's issues API mixes them in.
 *
 * @param issuesEnabled false when the repository has GitHub Issues turned off; totals and
 *     statistics are then null
 */
public record IssueAnalyticsResponse(
    String repository,
    AnalysisMeta meta,
    boolean issuesEnabled,
    Totals totals,
    IssueStatistics statistics) {

  /**
   * All-time counts.
   *
   * @param open the repository's {@code open_issues_count} minus open pull requests (GitHub counts
   *     both in that number)
   * @param closed from the Search API ({@code is:issue is:closed}); null when unavailable, e.g.
   *     when the Search API rate limit was reached
   */
  public record Totals(long open, Long closed) {}
}
