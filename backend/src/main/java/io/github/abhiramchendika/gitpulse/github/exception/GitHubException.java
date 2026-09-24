package io.github.abhiramchendika.gitpulse.github.exception;

/** Base type for every failure that originates from calling GitHub. */
public abstract class GitHubException extends RuntimeException {

  protected GitHubException(String message, Throwable cause) {
    super(message, cause);
  }
}
