package io.github.abhiramchendika.gitpulse.github.exception;

/**
 * GitHub answered 404. GitHub also answers 404 for private resources the token cannot see, so this
 * means "does not exist <em>or</em> is not accessible" and must be reported that way.
 */
public class GitHubNotFoundException extends GitHubException {

  public GitHubNotFoundException() {
    super("GitHub resource not found or not accessible", null);
  }
}
