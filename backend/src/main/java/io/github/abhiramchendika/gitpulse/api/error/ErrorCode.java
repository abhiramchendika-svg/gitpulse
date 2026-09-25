package io.github.abhiramchendika.gitpulse.api.error;

/**
 * Stable, machine-readable error codes returned in the {@code code} field of every error response.
 * Clients should branch on these, not on the human-readable {@code detail} text.
 */
public enum ErrorCode {
  INVALID_INPUT,
  NOT_FOUND,
  REPOSITORY_NOT_FOUND,
  USER_NOT_FOUND,
  RATE_LIMITED,
  GITHUB_AUTH_FAILED,
  GITHUB_UNAVAILABLE,
  GITHUB_ERROR,
  AI_NOT_CONFIGURED,
  AI_LIMIT_REACHED,
  AI_UNAVAILABLE,
  AI_UNRELIABLE,
  INTERNAL_ERROR
}
