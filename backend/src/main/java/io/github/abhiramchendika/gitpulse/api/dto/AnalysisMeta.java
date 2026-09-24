package io.github.abhiramchendika.gitpulse.api.dto;

import java.time.Instant;

/**
 * Describes exactly what data an analysis is based on, so clients can present it honestly.
 *
 * @param since start of the analysed window. Later than {@code requestedSince} when the commit
 *     sample was truncated: the statistics then cover only the period that was fully fetched.
 * @param until end of the analysed window (exclusive), capped at the time of analysis
 * @param requestedSince start of the window the client asked for
 * @param sampleSize commits fetched from GitHub (before bot filtering)
 * @param truncated the page cap was hit, so the window was shortened to {@code since}
 * @param botsExcluded commits by bot accounts were removed before analysis
 * @param timezone time zone used for day and hour grouping
 */
public record AnalysisMeta(
    Instant generatedAt,
    Instant since,
    Instant until,
    Instant requestedSince,
    int sampleSize,
    boolean truncated,
    boolean botsExcluded,
    String timezone) {}
