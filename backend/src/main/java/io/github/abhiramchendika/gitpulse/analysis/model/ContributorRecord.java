package io.github.abhiramchendika.gitpulse.analysis.model;

/**
 * A contributor as reported by GitHub's contributors endpoint.
 *
 * @param commits all-time commits on the default branch attributed to this account (API-provided)
 */
public record ContributorRecord(
    String login, String avatarUrl, String htmlUrl, boolean bot, int commits) {}
