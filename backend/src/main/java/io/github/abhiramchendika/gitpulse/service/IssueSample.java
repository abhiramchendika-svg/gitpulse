package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.analysis.model.IssueRecord;
import java.time.Instant;
import java.util.List;

/**
 * Issues (pull requests removed) created since the start of a window.
 *
 * @param oldestFetchedCreatedAt creation time of the oldest item fetched, pull requests included
 *     (they share pages with issues, so they decide how far back the sample reaches); null if
 *     nothing was fetched
 * @param truncated the page cap was hit before reaching the window's start
 */
public record IssueSample(
    List<IssueRecord> issues, Instant oldestFetchedCreatedAt, boolean truncated) {

  public IssueSample {
    issues = List.copyOf(issues);
  }
}
