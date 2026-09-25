package io.github.abhiramchendika.gitpulse.github.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * One item of {@code GET /users/{username}/events/public}. Only the stable fields are mapped: the
 * {@code payload} differs per event type and GitHub has changed it over time.
 *
 * @param type e.g. "PushEvent", "PullRequestEvent", "IssuesEvent", "CreateEvent"
 */
public record GitHubEvent(String type, Repo repo, @JsonProperty("created_at") Instant createdAt) {

  public record Repo(String name) {}
}
