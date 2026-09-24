package io.github.abhiramchendika.gitpulse.github.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * One item of {@code GET /repos/{owner}/{repo}/commits}.
 *
 * <p>{@code author} (top level) is the linked GitHub account and is {@code null} when the commit's
 * email is not associated with any account. {@code commit.author} is the raw Git author. Emails are
 * intentionally not mapped: GitPulse never needs them.
 */
public record GitHubCommit(
    String sha,
    @JsonProperty("html_url") String htmlUrl,
    Details commit,
    GitHubUser author,
    List<Parent> parents) {

  public record Details(GitIdentity author, GitIdentity committer, String message) {}

  public record GitIdentity(String name, Instant date) {}

  public record Parent(String sha) {}
}
