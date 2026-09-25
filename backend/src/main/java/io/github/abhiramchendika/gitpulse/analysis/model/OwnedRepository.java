package io.github.abhiramchendika.gitpulse.analysis.model;

import java.time.Instant;

/** A public repository owned by the analysed account, normalized for analysis. */
public record OwnedRepository(
    String fullName,
    String htmlUrl,
    String description,
    boolean fork,
    boolean archived,
    String language,
    int stars,
    int forks,
    Instant createdAt,
    Instant pushedAt) {}
