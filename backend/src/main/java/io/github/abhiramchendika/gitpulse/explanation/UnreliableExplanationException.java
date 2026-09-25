package io.github.abhiramchendika.gitpulse.explanation;

/**
 * The model answered, but not with something GitPulse can show: it declined, ran out of tokens,
 * returned malformed output, or too few sentences survived verification.
 */
public class UnreliableExplanationException extends RuntimeException {
  public UnreliableExplanationException(String reason) {
    super(reason);
  }
}
