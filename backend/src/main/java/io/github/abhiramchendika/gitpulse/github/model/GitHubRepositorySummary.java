package io.github.abhiramchendika.gitpulse.github.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * One item of {@code GET /users/{username}/repos}. A separate, smaller record than {@link
 * GitHubRepository} on purpose: list items lack some single-repository fields (such as {@code
 * subscribers_count}), and Jackson 3 rejects missing values for primitive fields.
 */
public record GitHubRepositorySummary(
    String name,
    @JsonProperty("full_name") String fullName,
    @JsonProperty("html_url") String htmlUrl,
    String description,
    boolean fork,
    boolean archived,
    String language,
    @JsonProperty("stargazers_count") int stargazersCount,
    @JsonProperty("forks_count") int forksCount,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("pushed_at") Instant pushedAt) {}
