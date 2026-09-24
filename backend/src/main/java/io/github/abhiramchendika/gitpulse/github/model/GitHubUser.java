package io.github.abhiramchendika.gitpulse.github.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A GitHub account as embedded in other responses. {@code type} is "User", "Organization" or "Bot".
 */
public record GitHubUser(
    String login,
    long id,
    String type,
    @JsonProperty("avatar_url") String avatarUrl,
    @JsonProperty("html_url") String htmlUrl) {}
