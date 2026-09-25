package io.github.abhiramchendika.gitpulse.service;

/** No GitHub user or organization with this name. */
public class UserNotFoundException extends RuntimeException {

  private final String username;

  public UserNotFoundException(String username) {
    super("User not found: " + username);
    this.username = username;
  }

  public String getUsername() {
    return username;
  }
}
