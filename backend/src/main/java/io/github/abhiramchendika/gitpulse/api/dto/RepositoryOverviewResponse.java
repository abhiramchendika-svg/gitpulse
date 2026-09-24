package io.github.abhiramchendika.gitpulse.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response of {@code GET /api/v1/repositories/{owner}/{repo}}. Every field is GitHub-provided.
 *
 * @param openIssuesAndPullRequests GitHub's {@code open_issues_count}, which counts open pull
 *     requests as issues; named honestly so clients do not display it as "open issues"
 * @param sizeKb repository size in kilobytes, as reported by GitHub
 * @param pushedAt time of the most recent push to any branch
 */
public record RepositoryOverviewResponse(
    String fullName,
    String name,
    Owner owner,
    String description,
    String htmlUrl,
    String homepage,
    int stars,
    int forks,
    int watchers,
    int openIssuesAndPullRequests,
    String primaryLanguage,
    License license,
    List<String> topics,
    Instant createdAt,
    Instant updatedAt,
    Instant pushedAt,
    int sizeKb,
    String defaultBranch,
    boolean archived,
    boolean fork,
    boolean hasIssues) {

  public record Owner(String login, String type, String avatarUrl, String htmlUrl) {}

  public record License(String spdxId, String name) {}
}
