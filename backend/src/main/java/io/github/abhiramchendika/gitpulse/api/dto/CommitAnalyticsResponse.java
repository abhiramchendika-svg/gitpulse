package io.github.abhiramchendika.gitpulse.api.dto;

import io.github.abhiramchendika.gitpulse.analysis.CommitStatistics;

/**
 * Response of {@code GET /api/v1/repositories/{owner}/{repo}/commits}.
 *
 * @param totalCommitsAllTime all commits on the default branch (GitHub-provided, via pagination)
 * @param emptyRepository the repository has no commits at all
 * @param statistics GitPulse-calculated statistics for the window described in {@code meta}
 */
public record CommitAnalyticsResponse(
    String repository,
    AnalysisMeta meta,
    boolean emptyRepository,
    long totalCommitsAllTime,
    CommitStatistics statistics) {}
