package io.github.abhiramchendika.gitpulse.github.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * {@code GET /repos/{owner}/{repo}/commits/{sha}}: one commit with the files it changed. Unlike the
 * commit list, this endpoint includes per-file line counts, which is why file activity costs one
 * request per commit.
 *
 * <p>GitHub returns at most 300 files per commit in this response.
 */
public record GitHubCommitDetail(
    String sha,
    @JsonProperty("html_url") String htmlUrl,
    GitHubCommit.Details commit,
    GitHubUser author,
    List<GitHubCommit.Parent> parents,
    List<File> files) {

  /**
   * @param status "added", "removed", "modified", "renamed", "copied", "changed" or "unchanged"
   * @param previousFilename set for renames
   */
  public record File(
      String filename,
      String status,
      int additions,
      int deletions,
      @JsonProperty("previous_filename") String previousFilename) {}
}
