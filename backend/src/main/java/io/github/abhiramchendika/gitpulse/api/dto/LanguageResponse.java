package io.github.abhiramchendika.gitpulse.api.dto;

import io.github.abhiramchendika.gitpulse.analysis.LanguageStatistics;

/** Response of {@code GET /api/v1/repositories/{owner}/{repo}/languages}. */
public record LanguageResponse(String repository, LanguageStatistics statistics) {}
