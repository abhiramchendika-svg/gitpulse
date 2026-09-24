package io.github.abhiramchendika.gitpulse.analysis.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** A half-open time range {@code [since, until)} that an analysis covers. */
public record AnalysisWindow(Instant since, Instant until) {

  public AnalysisWindow {
    Objects.requireNonNull(since, "since");
    Objects.requireNonNull(until, "until");
    if (!since.isBefore(until)) {
      throw new IllegalArgumentException("since must be before until");
    }
  }

  public boolean contains(Instant instant) {
    return !instant.isBefore(since) && instant.isBefore(until);
  }

  /** Length of the window in (fractional) days. */
  public double days() {
    return Duration.between(since, until).toSeconds() / 86_400.0;
  }
}
