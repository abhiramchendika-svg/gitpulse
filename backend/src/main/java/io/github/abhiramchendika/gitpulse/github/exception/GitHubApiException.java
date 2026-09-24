package io.github.abhiramchendika.gitpulse.github.exception;

/** Any other unexpected GitHub response (e.g. 403 without rate-limit headers, 422). */
public class GitHubApiException extends GitHubException {

  private final int status;

  public GitHubApiException(int status, String message) {
    super(message, null);
    this.status = status;
  }

  public int getStatus() {
    return status;
  }
}
