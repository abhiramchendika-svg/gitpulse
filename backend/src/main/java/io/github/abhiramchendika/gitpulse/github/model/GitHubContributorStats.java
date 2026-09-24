package io.github.abhiramchendika.gitpulse.github.model;

import java.util.List;

/**
 * One item of {@code GET /repos/{owner}/{repo}/stats/contributors}: weekly additions, deletions and
 * commits for one of the top 100 contributors. {@code w} is the week start as Unix seconds.
 */
public record GitHubContributorStats(GitHubUser author, int total, List<Week> weeks) {

  public record Week(long w, long a, long d, int c) {}
}
