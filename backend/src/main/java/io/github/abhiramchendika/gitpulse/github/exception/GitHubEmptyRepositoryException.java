package io.github.abhiramchendika.gitpulse.github.exception;

/**
 * GitHub answered 409 Conflict, which it does for commit endpoints on a repository with no commits
 * ("Git Repository is empty."). Callers usually turn this into an empty result, not an error.
 */
public class GitHubEmptyRepositoryException extends GitHubException {

  public GitHubEmptyRepositoryException() {
    super("GitHub repository is empty", null);
  }
}
