package io.github.abhiramchendika.gitpulse.github.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The count part of a GitHub Search API response. Items are not needed: GitPulse only asks for
 * {@code total_count}.
 *
 * @param incompleteResults true if GitHub's search timed out and the count may be too low
 */
public record GitHubSearchResult(
    @JsonProperty("total_count") long totalCount,
    @JsonProperty("incomplete_results") boolean incompleteResults) {}
