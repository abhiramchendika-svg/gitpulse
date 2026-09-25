package io.github.abhiramchendika.gitpulse.explanation;

/** The model could not be reached or refused the credentials (network, overload, rate limit). */
public class ExplanationUnavailableException extends RuntimeException {
  public ExplanationUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
