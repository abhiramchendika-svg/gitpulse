package io.github.abhiramchendika.gitpulse.explanation;

import java.time.Instant;

/** This instance used its hourly allowance of model calls. */
public class ExplanationLimitException extends RuntimeException {

  private final Instant resetAt;

  public ExplanationLimitException(Instant resetAt) {
    super("Hourly explanation limit reached.");
    this.resetAt = resetAt;
  }

  /** When the oldest counted call leaves the one-hour window, freeing a slot. */
  public Instant getResetAt() {
    return resetAt;
  }
}
