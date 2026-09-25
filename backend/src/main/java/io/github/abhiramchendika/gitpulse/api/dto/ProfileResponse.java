package io.github.abhiramchendika.gitpulse.api.dto;

import io.github.abhiramchendika.gitpulse.analysis.ProfileStatistics;
import java.time.Instant;

/**
 * Response of {@code GET /api/v1/users/{username}}: public information only. Private repositories
 * and private contributions are not visible through this API and are not estimated.
 */
public record ProfileResponse(Profile profile, Meta meta, ProfileStatistics statistics) {

  /** GitHub-provided public profile fields. */
  public record Profile(
      String login,
      String type,
      String name,
      String avatarUrl,
      String htmlUrl,
      String bio,
      String company,
      String blog,
      String location,
      int publicRepos,
      int followers,
      int following,
      Instant createdAt) {}

  /**
   * @param repositoriesTruncated the account has more public repositories than GitPulse fetches
   *     (the 300 most recently pushed are analysed)
   * @param eventsAnalyzed false for organizations
   */
  public record Meta(Instant generatedAt, boolean repositoriesTruncated, boolean eventsAnalyzed) {}
}
