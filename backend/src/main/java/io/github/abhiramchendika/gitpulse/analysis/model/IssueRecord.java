package io.github.abhiramchendika.gitpulse.analysis.model;

import java.time.Instant;
import java.util.Objects;

/**
 * An issue (never a pull request), normalized for analysis.
 *
 * @param stateReason GitHub's close reason: "completed", "not_planned", "duplicate", or null
 */
public record IssueRecord(
    int number,
    String title,
    String authorLogin,
    boolean bot,
    Instant createdAt,
    Instant closedAt,
    String stateReason,
    int comments,
    String htmlUrl) {

  public IssueRecord {
    Objects.requireNonNull(createdAt, "createdAt");
  }

  public boolean open() {
    return closedAt == null;
  }
}
