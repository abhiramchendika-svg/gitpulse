package io.github.abhiramchendika.gitpulse.github.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One item of {@code GET /repos/{owner}/{repo}/contributors}. {@code contributions} is the number
 * of commits on the default branch attributed to this account (all time).
 */
public record GitHubContributor(
    String login,
    long id,
    String type,
    @JsonProperty("avatar_url") String avatarUrl,
    @JsonProperty("html_url") String htmlUrl,
    int contributions) {}
