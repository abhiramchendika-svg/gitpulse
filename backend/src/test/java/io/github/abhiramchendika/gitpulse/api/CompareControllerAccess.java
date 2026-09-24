package io.github.abhiramchendika.gitpulse.api;

import io.github.abhiramchendika.gitpulse.service.RepositoryRef;
import java.util.List;

/** Test-only access to the package-private parser of {@link CompareController}. */
public final class CompareControllerAccess {

  private CompareControllerAccess() {}

  public static List<RepositoryRef> parse(String repos) {
    return CompareController.parse(repos);
  }
}
