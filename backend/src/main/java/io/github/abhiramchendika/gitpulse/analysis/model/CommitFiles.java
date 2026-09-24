package io.github.abhiramchendika.gitpulse.analysis.model;

import java.time.Instant;
import java.util.List;

/**
 * The files one commit changed, normalized for analysis.
 *
 * @param authorKey same grouping key as {@link CommitRecord#authorKey()}
 * @param filesTruncated GitHub returned its maximum of 300 files, so the list may be incomplete
 */
public record CommitFiles(
    String sha,
    String authorKey,
    Instant authoredAt,
    List<FileChange> files,
    boolean filesTruncated) {

  public CommitFiles {
    files = List.copyOf(files);
  }

  /**
   * @param previousPath the old path when the file was renamed in this commit, else null
   */
  public record FileChange(
      String path, String previousPath, String status, int additions, int deletions) {}
}
