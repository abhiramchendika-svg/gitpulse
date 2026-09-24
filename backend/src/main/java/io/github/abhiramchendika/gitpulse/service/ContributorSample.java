package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.analysis.model.ContributorRecord;
import java.util.List;

/**
 * Normalized contributors of a repository.
 *
 * @param truncated the page cap was hit, so contributors with fewer commits were not fetched
 * @param available false when GitHub refuses to list contributors (very large repositories)
 */
public record ContributorSample(
    List<ContributorRecord> contributors, boolean truncated, boolean available) {

  public ContributorSample {
    contributors = List.copyOf(contributors);
  }

  public static ContributorSample unavailable() {
    return new ContributorSample(List.of(), false, false);
  }
}
