package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.analysis.model.PullRequestRecord;
import java.util.List;

/**
 * Pull requests created since the start of a window, newest first.
 *
 * @param truncated the page cap was hit before reaching the window's start
 */
public record PullRequestSample(List<PullRequestRecord> pullRequests, boolean truncated) {

  public PullRequestSample {
    pullRequests = List.copyOf(pullRequests);
  }
}
