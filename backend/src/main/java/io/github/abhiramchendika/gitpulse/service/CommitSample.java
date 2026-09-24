package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.analysis.model.CommitRecord;
import java.util.List;

/**
 * Normalized commits fetched for a window.
 *
 * @param truncated the page cap was hit, so older commits in the window were not fetched
 * @param emptyRepository the repository has no commits at all (GitHub answered 409)
 */
public record CommitSample(List<CommitRecord> commits, boolean truncated, boolean emptyRepository) {

  public CommitSample {
    commits = List.copyOf(commits);
  }
}
