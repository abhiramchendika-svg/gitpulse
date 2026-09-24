package io.github.abhiramchendika.gitpulse.analysis;

import java.time.Instant;

/**
 * Factual recent-activity indicators. Deliberately <em>not</em> a score: each value is a count or a
 * date with a documented definition, and interpretation is left to the reader.
 *
 * <p>The {@code *Partial} flags are true when the underlying sample did not reach back the full 90
 * days (a page cap was hit): the matching counts are then lower bounds.
 */
public record ActivityIndicators(
    Instant lastCommitAt,
    Double daysSinceLastCommit,
    Instant lastPushAt,
    int commitsLast30Days,
    int commitsLast90Days,
    /** Weeks (of the last 12 × 7 days) containing at least one commit. */
    int activeWeeksOfLast12,
    boolean commitsPartial,
    int pullRequestsOpenedLast90Days,
    int pullRequestsMergedLast90Days,
    boolean pullRequestsPartial,
    Integer issuesOpenedLast90Days,
    Integer issuesClosedLast90Days,
    boolean issuesPartial) {}
