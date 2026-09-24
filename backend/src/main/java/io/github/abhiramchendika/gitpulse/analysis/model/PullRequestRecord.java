package io.github.abhiramchendika.gitpulse.analysis.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A pull request, normalized for analysis.
 *
 * @param mergedAt null unless the pull request was merged
 * @param closedAt null while open; set for both merged and closed-without-merge
 */
public record PullRequestRecord(
    int number,
    String title,
    String authorLogin,
    boolean bot,
    boolean draft,
    Instant createdAt,
    Instant closedAt,
    Instant mergedAt,
    String htmlUrl) {

  public PullRequestRecord {
    Objects.requireNonNull(createdAt, "createdAt");
  }

  public boolean merged() {
    return mergedAt != null;
  }

  public boolean closedWithoutMerge() {
    return closedAt != null && mergedAt == null;
  }

  public boolean open() {
    return closedAt == null;
  }
}
