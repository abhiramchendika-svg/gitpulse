package io.github.abhiramchendika.gitpulse.api;

/**
 * Allow-list patterns for GitHub names in URL paths. Only these characters ever reach the GitHub
 * client, which rules out path tricks such as {@code ..} or encoded slashes.
 */
public final class GitHubNames {

  private GitHubNames() {}

  /** Users and organizations: letters, digits and hyphens; up to 39 characters. */
  public static final String OWNER = "^[A-Za-z0-9][A-Za-z0-9-]{0,38}$";

  /** Repositories: letters, digits, '.', '_' and '-'; up to 100; not "." or "..". */
  public static final String REPO = "^(?!\\.{1,2}$)[A-Za-z0-9._-]{1,100}$";
}
