package io.github.abhiramchendika.gitpulse.github.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

/**
 * One item of {@code GET /repos/{owner}/{repo}/issues}.
 *
 * <p>GitHub's issues endpoint also returns <em>pull requests</em> (every PR is an issue in GitHub's
 * model). Those items carry a {@code pull_request} object, which is how they are told apart and
 * filtered out.
 *
 * @param stateReason for closed issues: "completed", "not_planned" or "duplicate"; may be null,
 *     notably for issues closed before GitHub introduced the field
 */
public record GitHubIssue(
    int number,
    String title,
    String state,
    @JsonProperty("state_reason") String stateReason,
    GitHubUser user,
    int comments,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("closed_at") Instant closedAt,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("pull_request") Map<String, Object> pullRequest) {

  public boolean isPullRequest() {
    return pullRequest != null;
  }
}
