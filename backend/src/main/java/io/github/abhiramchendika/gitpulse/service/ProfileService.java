package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.analysis.ProfileAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.model.PublicEvent;
import io.github.abhiramchendika.gitpulse.api.dto.ProfileResponse;
import io.github.abhiramchendika.gitpulse.github.model.GitHubUserProfile;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** Public profile analysis for a user or organization. */
@Service
public class ProfileService {

  private final UserData data;
  private final ProfileAnalyzer analyzer;
  private final Clock clock;

  public ProfileService(UserData data, ProfileAnalyzer analyzer, Clock clock) {
    this.data = data;
    this.analyzer = analyzer;
    this.clock = clock;
  }

  public ProfileResponse analyze(String username) {
    Instant now = clock.instant();
    GitHubUserProfile profile = data.profile(username);
    UserData.Repositories repositories = data.repositories(username);
    // The public-events endpoint describes a person's own activity; for organizations it lists
    // events of many members, which would be misleading here, so it is skipped.
    boolean isOrganization = "Organization".equals(profile.type());
    List<PublicEvent> events = isOrganization ? null : data.events(username);

    return new ProfileResponse(
        new ProfileResponse.Profile(
            profile.login(),
            profile.type(),
            profile.name(),
            profile.avatarUrl(),
            profile.htmlUrl(),
            profile.bio(),
            profile.company(),
            blank(profile.blog()) ? null : profile.blog(),
            profile.location(),
            profile.publicRepos(),
            profile.followers(),
            profile.following(),
            profile.createdAt()),
        new ProfileResponse.Meta(now, repositories.truncated(), !isOrganization),
        analyzer.analyze(repositories.repositories(), events, now));
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
