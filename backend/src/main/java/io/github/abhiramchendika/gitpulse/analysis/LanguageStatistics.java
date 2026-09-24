package io.github.abhiramchendika.gitpulse.analysis;

import java.util.List;

/** Output of {@link LanguageAnalyzer}. */
public record LanguageStatistics(long totalBytes, List<Language> languages) {

  /**
   * @param bytes bytes of code in this language (GitHub-provided, measured by GitHub Linguist)
   * @param percent share of total bytes, one decimal (GitPulse-calculated; may not sum to exactly
   *     100 because of rounding)
   */
  public record Language(String name, long bytes, double percent) {}
}
