package io.github.abhiramchendika.gitpulse.github.exception;

/** GitHub could not be reached (network failure, timeout) or answered with a 5xx error. */
public class GitHubUnavailableException extends GitHubException {

  public GitHubUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
