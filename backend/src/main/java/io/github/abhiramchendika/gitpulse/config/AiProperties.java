package io.github.abhiramchendika.gitpulse.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the optional "explain these numbers" feature, bound from {@code gitpulse.ai.*}.
 *
 * <p>The feature is off unless an Anthropic API key is configured ({@code ANTHROPIC_API_KEY}). Like
 * the GitHub token, the key stays on the server: never committed, logged or sent to the browser.
 *
 * @param baseUrl Anthropic API base URL (overridden only in tests)
 * @param model Claude model id
 * @param maxExplanationsPerHour model calls allowed per hour for the whole instance; each one costs
 *     money, so this caps the bill
 * @param maxTokens output token limit per call (includes the model's thinking)
 * @param timeout per-request timeout
 * @param refusalFallback ask the API to retry on another model if a safety classifier declines
 */
@Validated
@ConfigurationProperties(prefix = "gitpulse.ai")
public record AiProperties(
    String apiKey,
    @NotNull URI baseUrl,
    @NotBlank String model,
    @Min(1) @Max(1000) int maxExplanationsPerHour,
    @Min(1024) @Max(16000) long maxTokens,
    @NotNull Duration timeout,
    boolean refusalFallback) {

  public boolean enabled() {
    return apiKey != null && !apiKey.isBlank();
  }

  /** Never print the key, even by accident (records generate toString from all fields). */
  @Override
  public String toString() {
    return ("AiProperties[apiKey=%s, baseUrl=%s, model=%s, maxExplanationsPerHour=%d,"
            + " maxTokens=%d, timeout=%s, refusalFallback=%s]")
        .formatted(
            enabled() ? "****" : "<none>",
            baseUrl,
            model,
            maxExplanationsPerHour,
            maxTokens,
            timeout,
            refusalFallback);
  }
}
