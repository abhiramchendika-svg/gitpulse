package io.github.abhiramchendika.gitpulse.api.dto;

import io.github.abhiramchendika.gitpulse.analysis.ActivityIndicators;
import java.time.Instant;

/**
 * Response of {@code GET /api/v1/repositories/{owner}/{repo}/activity}: factual indicators of
 * recent activity, measured back from {@code generatedAt}. There is no overall score.
 */
public record ActivityResponse(
    String repository, Instant generatedAt, ActivityIndicators indicators) {}
