package io.github.abhiramchendika.gitpulse.explanation;

import java.math.BigDecimal;

/**
 * One number (or short fixed label) that the model may use, identified by a stable id.
 *
 * @param id stable identifier the model cites, e.g. {@code commits.total}
 * @param label what the value means, in plain English
 * @param value a {@link BigDecimal}, {@link String} or {@link Boolean}
 */
public record Fact(String id, String label, Object value) {

  public Fact {
    if (!(value instanceof BigDecimal || value instanceof String || value instanceof Boolean)) {
      throw new IllegalArgumentException("Unsupported fact value for " + id + ": " + value);
    }
  }

  /** The numeric value, or null for text and yes/no facts. */
  public BigDecimal number() {
    return value instanceof BigDecimal n ? n : null;
  }
}
