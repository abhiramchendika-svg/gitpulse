package io.github.abhiramchendika.gitpulse.analysis.model;

import java.time.Instant;

/** One public GitHub event of the analysed account (type, repository, time). */
public record PublicEvent(String type, String repository, Instant createdAt) {}
