package io.github.abhiramchendika.gitpulse.analysis;

import java.time.Instant;
import java.util.List;

/**
 * Output of {@link ProfileAnalyzer}: descriptive facts about an account's public repositories and
 * recent public activity. No score, no ranking. See {@code docs/metrics.md}.
 */
public record ProfileStatistics(
    int repositoriesAnalyzed,
    int originalRepositories,
    int forkedRepositories,
    int archivedRepositories,
    /** Sum of stars on the account's own (non-fork) repositories. */
    long starsReceived,
    /** Sum of forks of the account's own (non-fork) repositories. */
    long forksReceived,
    /** Own repositories per primary language (not bytes of code). */
    List<LanguageCount> languages,
    int repositoriesWithoutLanguage,
    int pushedLast30Days,
    int pushedLast90Days,
    int pushedLastYear,
    List<RepositoryItem> mostStarred,
    List<RepositoryItem> recentlyPushed,
    List<YearCount> createdPerYear,
    /** Null for organizations, whose events are not analysed. */
    Events events) {

  /**
   * @param percent share of own repositories that have a detected primary language
   */
  public record LanguageCount(String name, int repositories, double percent) {}

  public record RepositoryItem(
      String fullName,
      String htmlUrl,
      String description,
      String language,
      int stars,
      int forks,
      boolean fork,
      boolean archived,
      Instant pushedAt) {}

  public record YearCount(int year, int repositories) {}

  /**
   * Public events GitHub still keeps (at most 90 days and 300 events).
   *
   * @param activeDays distinct UTC days with at least one public event
   */
  public record Events(
      int count,
      Instant from,
      Instant to,
      int activeDays,
      int repositoriesTouched,
      List<TypeCount> byType,
      List<RepositoryCount> topRepositories) {}

  /**
   * @param label readable name, e.g. "Pushes" for PushEvent
   */
  public record TypeCount(String type, String label, int count) {}

  public record RepositoryCount(String repository, int events) {}
}
