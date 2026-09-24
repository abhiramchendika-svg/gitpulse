package io.github.abhiramchendika.gitpulse.service;

import java.util.Locale;
import java.util.Objects;

/**
 * Identifies a repository. GitHub names are case-insensitive, so both parts are lower-cased: this
 * makes {@code Spring/Boot} and {@code spring/boot} share one cache entry.
 */
public record RepositoryRef(String owner, String repo) {

  public RepositoryRef {
    owner = Objects.requireNonNull(owner, "owner").toLowerCase(Locale.ROOT);
    repo = Objects.requireNonNull(repo, "repo").toLowerCase(Locale.ROOT);
  }

  public String fullName() {
    return owner + "/" + repo;
  }
}
