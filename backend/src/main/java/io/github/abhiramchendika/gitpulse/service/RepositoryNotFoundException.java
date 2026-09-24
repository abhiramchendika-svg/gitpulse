package io.github.abhiramchendika.gitpulse.service;

/** The repository does not exist, or is private (GitHub reports both as 404). */
public class RepositoryNotFoundException extends RuntimeException {

  private final String fullName;

  public RepositoryNotFoundException(RepositoryRef ref) {
    super("Repository not found: " + ref.fullName());
    this.fullName = ref.fullName();
  }

  public String getFullName() {
    return fullName;
  }
}
