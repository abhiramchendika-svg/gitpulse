package io.github.abhiramchendika.gitpulse.github.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/** Subset of GitHub's {@code GET /repos/{owner}/{repo}} response. */
public record GitHubRepository(
    String name,
    @JsonProperty("full_name") String fullName,
    String description,
    GitHubUser owner,
    @JsonProperty("html_url") String htmlUrl,
    String homepage,
    @JsonProperty("stargazers_count") int stargazersCount,
    @JsonProperty("forks_count") int forksCount,
    /** Real "watchers"; GitHub's {@code watchers_count} is a legacy alias of stars. */
    @JsonProperty("subscribers_count") int subscribersCount,
    /** Open issues <em>plus</em> open pull requests; GitHub counts both here. */
    @JsonProperty("open_issues_count") int openIssuesCount,
    String language,
    License license,
    List<String> topics,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("pushed_at") Instant pushedAt,
    /** Size in kilobytes, as reported by GitHub. */
    int size,
    @JsonProperty("default_branch") String defaultBranch,
    boolean archived,
    boolean fork,
    /**
     * False when the repository has the Issues feature turned off. Boxed on purpose: Jackson 3
     * rejects a missing value for a primitive, and "missing" must not silently mean "disabled".
     */
    @JsonProperty("has_issues") Boolean hasIssues) {

  /** Issues are enabled unless GitHub explicitly says otherwise (enabled is GitHub's default). */
  public boolean issuesEnabled() {
    return hasIssues == null || hasIssues;
  }

  public record License(@JsonProperty("spdx_id") String spdxId, String name) {}
}
