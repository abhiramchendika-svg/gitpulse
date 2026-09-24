package io.github.abhiramchendika.gitpulse.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limits that keep analysis affordable in GitHub API requests, bound from {@code
 * gitpulse.analysis.*}.
 *
 * @param maxCommitPages cap on commit pages (100 commits each) fetched per analysis
 * @param maxContributorPages cap on contributor pages (100 each); GitHub links at most 500
 * @param defaultWindowDays window used when the client does not pass {@code since}
 * @param maxWindowDays largest window a client may request
 * @param inactivityThreshold gaps between commits longer than this are reported
 * @param topAuthors authors listed in commit analytics
 * @param recentCommits recent commits listed in commit analytics
 * @param inactivityPeriods inactivity periods listed (the longest ones)
 * @param contributorLimit contributors listed in contributor analytics
 * @param maxPullRequestPages cap on pull request pages (100 each) fetched per window
 * @param maxIssuePages cap on issue pages (100 each, pull requests included) fetched per window
 */
@Validated
@ConfigurationProperties(prefix = "gitpulse.analysis")
public record AnalysisProperties(
    @Min(1) @Max(50) int maxCommitPages,
    @Min(1) @Max(5) int maxContributorPages,
    @Min(1) int defaultWindowDays,
    @Min(1) int maxWindowDays,
    @NotNull Duration inactivityThreshold,
    @Min(1) int topAuthors,
    @Min(1) int recentCommits,
    @Min(1) int inactivityPeriods,
    @Min(1) int contributorLimit,
    @Min(1) @Max(10) int maxPullRequestPages,
    @Min(1) @Max(10) int maxIssuePages) {}
