package io.github.abhiramchendika.gitpulse.api.dto;

import io.github.abhiramchendika.gitpulse.analysis.ActivityIndicators;
import io.github.abhiramchendika.gitpulse.analysis.CommitStatistics;
import io.github.abhiramchendika.gitpulse.analysis.LanguageStatistics;
import java.time.Instant;
import java.util.List;

/**
 * Response of {@code GET /api/v1/compare}: the same facts for each repository, side by side.
 * Deliberately descriptive: there is no score and no "winner".
 */
public record ComparisonResponse(Instant generatedAt, List<Summary> repositories) {

  /**
   * @param contributors linked contributors (GitHub-provided); null when GitHub will not count them
   * @param ageYears years since the repository was created on GitHub
   * @param commits commit activity over the last year (default window)
   * @param weekly commits per week over that year, for side-by-side charts
   */
  public record Summary(
      String fullName,
      String htmlUrl,
      String description,
      int stars,
      int forks,
      int watchers,
      int openIssuesAndPullRequests,
      String primaryLanguage,
      List<LanguageStatistics.Language> topLanguages,
      String license,
      Instant createdAt,
      double ageYears,
      Instant pushedAt,
      boolean archived,
      Long contributors,
      CommitSummary commits,
      List<CommitStatistics.WeekCount> weekly,
      ActivityIndicators activity) {}

  /**
   * @param truncated the year held more commits than GitPulse fetches; the window was shortened
   * @param since start of the period the numbers cover
   */
  public record CommitSummary(
      int total,
      double averagePerWeek,
      int activeWeeks,
      int totalWeeks,
      int distinctAuthors,
      boolean truncated,
      Instant since) {}
}
