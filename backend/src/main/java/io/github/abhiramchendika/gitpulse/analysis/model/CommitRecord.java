package io.github.abhiramchendika.gitpulse.analysis.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A commit, normalized from GitHub's JSON into only what the analyzers need.
 *
 * @param sha full commit SHA
 * @param authorKey stable grouping key: the GitHub login when the commit is linked to an account,
 *     otherwise {@code "git:" + author name}
 * @param authorName display name (login if linked, otherwise the Git author name)
 * @param authorLogin GitHub login, or {@code null} if the commit is not linked to an account
 * @param authoredAt Git author date (when the change was written, preserved by rebases). All
 *     activity statistics use this date.
 * @param committedAt Git committer date (when the commit landed). GitHub orders and filters the
 *     commit list by this date, so it defines which commits a truncated sample contains.
 * @param merge true if the commit has more than one parent
 * @param bot true if the linked account is a bot (e.g. {@code dependabot[bot]})
 * @param headline first line of the commit message
 * @param htmlUrl link to the commit on github.com
 */
public record CommitRecord(
    String sha,
    String authorKey,
    String authorName,
    String authorLogin,
    Instant authoredAt,
    Instant committedAt,
    boolean merge,
    boolean bot,
    String headline,
    String htmlUrl) {

  public CommitRecord {
    Objects.requireNonNull(sha, "sha");
    Objects.requireNonNull(authorKey, "authorKey");
    Objects.requireNonNull(authoredAt, "authoredAt");
    Objects.requireNonNull(committedAt, "committedAt");
  }
}
