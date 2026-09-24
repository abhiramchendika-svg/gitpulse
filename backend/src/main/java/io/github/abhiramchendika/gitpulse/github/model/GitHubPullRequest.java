package io.github.abhiramchendika.gitpulse.github.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * One item of {@code GET /repos/{owner}/{repo}/pulls}. {@code mergedAt} is null for pull requests
 * that were closed without merging (and for open ones).
 */
public record GitHubPullRequest(
    int number,
    String title,
    String state,
    boolean draft,
    GitHubUser user,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("closed_at") Instant closedAt,
    @JsonProperty("merged_at") Instant mergedAt,
    @JsonProperty("html_url") String htmlUrl) {}
