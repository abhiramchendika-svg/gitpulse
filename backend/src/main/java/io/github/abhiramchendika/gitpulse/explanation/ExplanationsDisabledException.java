package io.github.abhiramchendika.gitpulse.explanation;

/** The feature is off because no Anthropic API key is configured. */
public class ExplanationsDisabledException extends RuntimeException {
  public ExplanationsDisabledException() {
    super("Explanations are not configured on this server.");
  }
}
