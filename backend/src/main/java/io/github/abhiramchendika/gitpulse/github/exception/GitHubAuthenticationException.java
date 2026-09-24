package io.github.abhiramchendika.gitpulse.github.exception;

/**
 * GitHub answered 401: the server's configured token is invalid, expired, or revoked. This is a
 * server configuration problem, not something the API caller did wrong.
 */
public class GitHubAuthenticationException extends GitHubException {

  public GitHubAuthenticationException() {
    super("GitHub rejected the configured token", null);
  }
}
