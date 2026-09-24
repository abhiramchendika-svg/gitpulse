package io.github.abhiramchendika.gitpulse.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for talking to the GitHub REST API, bound from {@code gitpulse.github.*}.
 *
 * <p>The token is optional: without it GitHub allows 60 requests/hour per IP, with it 5,000/hour.
 * It is read from the {@code GITHUB_TOKEN} environment variable (or a local {@code .env} file) and
 * must never be committed, logged, or sent to the frontend.
 */
@Validated
@ConfigurationProperties(prefix = "gitpulse.github")
public record GitHubProperties(
    @NotNull URI baseUrl,
    String token,
    @NotBlank String apiVersion,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout) {

  public boolean hasToken() {
    return token != null && !token.isBlank();
  }

  /** Never print the token, even by accident (records generate toString from all fields). */
  @Override
  public String toString() {
    return "GitHubProperties[baseUrl=%s, token=%s, apiVersion=%s, connectTimeout=%s, readTimeout=%s]"
        .formatted(
            baseUrl, hasToken() ? "****" : "<none>", apiVersion, connectTimeout, readTimeout);
  }
}
