package io.github.abhiramchendika.gitpulse.github.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Public profile from {@code GET /users/{username}} (only the fields GitPulse shows). The public
 * email address is deliberately not mapped: the analysis does not need it.
 *
 * @param type "User" or "Organization"
 */
public record GitHubUserProfile(
    String login,
    String type,
    String name,
    @JsonProperty("avatar_url") String avatarUrl,
    @JsonProperty("html_url") String htmlUrl,
    String bio,
    String company,
    String blog,
    String location,
    @JsonProperty("public_repos") int publicRepos,
    int followers,
    int following,
    @JsonProperty("created_at") Instant createdAt) {}
