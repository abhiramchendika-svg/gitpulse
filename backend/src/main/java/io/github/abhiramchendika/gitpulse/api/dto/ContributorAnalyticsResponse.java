package io.github.abhiramchendika.gitpulse.api.dto;

import io.github.abhiramchendika.gitpulse.analysis.ContributorStatistics;
import java.time.Instant;

/**
 * Response of {@code GET /api/v1/repositories/{owner}/{repo}/contributors}. Covers all-time commits
 * on the default branch by contributors with a linked GitHub account.
 *
 * @param available false when GitHub declines to list contributors (very large repositories)
 * @param truncated the page cap was hit; contributors with the fewest commits are missing
 */
public record ContributorAnalyticsResponse(
    String repository,
    Instant generatedAt,
    boolean available,
    boolean truncated,
    ContributorStatistics statistics) {}
