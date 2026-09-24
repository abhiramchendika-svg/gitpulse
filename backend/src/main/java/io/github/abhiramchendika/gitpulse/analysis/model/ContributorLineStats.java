package io.github.abhiramchendika.gitpulse.analysis.model;

/**
 * All-time line changes for one contributor, summed from GitHub's weekly contributor statistics.
 * GitHub only computes these for the top 100 contributors.
 */
public record ContributorLineStats(String login, long additions, long deletions, int commits) {}
