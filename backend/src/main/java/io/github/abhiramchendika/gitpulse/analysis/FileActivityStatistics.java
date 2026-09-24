package io.github.abhiramchendika.gitpulse.analysis;

import java.time.Instant;
import java.util.List;

/**
 * Output of {@link FileActivityAnalyzer}, describing a sample of recent commits (not the whole
 * history). See {@code docs/metrics.md}.
 */
public record FileActivityStatistics(
    int commitsAnalyzed,
    Instant sampleFrom,
    Instant sampleTo,
    int filesTouched,
    /** Commits whose file list GitHub capped at 300 entries (their counts are lower bounds). */
    int commitsWithTruncatedFiles,
    /** Most commits touching the file. */
    List<FileStat> mostFrequentlyChanged,
    /** Most lines added + deleted. */
    List<FileStat> highestChurn,
    List<DirectoryStat> directories,
    List<FileStat> recentlyChanged) {

  /**
   * @param path current path (renames within the sample are followed)
   * @param commits sampled commits that changed this file
   * @param churn additions + deletions
   * @param distinctAuthors distinct commit authors who changed it within the sample
   * @param deleted the most recent change in the sample removed the file
   */
  public record FileStat(
      String path,
      int commits,
      long additions,
      long deletions,
      long churn,
      int distinctAuthors,
      Instant lastChangedAt,
      boolean deleted) {}

  /**
   * @param path directory path ("" for the repository root)
   * @param commits sampled commits that changed at least one file directly in it
   */
  public record DirectoryStat(String path, int commits, int filesTouched, long churn) {}
}
