package io.github.abhiramchendika.gitpulse.api.dto;

import io.github.abhiramchendika.gitpulse.analysis.FileActivityStatistics;
import java.time.Instant;

/** Response of {@code GET /api/v1/repositories/{owner}/{repo}/files}. */
public record FileActivityResponse(
    String repository, Meta meta, FileActivityStatistics statistics) {

  /**
   * @param requestedSample commits the client asked for (or the default)
   * @param sampleLimit the most this server will analyse per request (lower without a token)
   * @param authenticated whether the server uses a GitHub token
   * @param candidateCommits non-merge commits of the last year available to sample from
   * @param mergeCommitsSkipped merge commits left out: GitHub diffs a merge against its first
   *     parent, which would count the merged branch's files a second time
   */
  public record Meta(
      Instant generatedAt,
      int requestedSample,
      int sampleLimit,
      boolean authenticated,
      int candidateCommits,
      int mergeCommitsSkipped) {}
}
