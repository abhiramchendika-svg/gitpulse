package io.github.abhiramchendika.gitpulse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.abhiramchendika.gitpulse.analysis.ProfileAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.model.PublicEvent;
import io.github.abhiramchendika.gitpulse.api.dto.ProfileResponse;
import io.github.abhiramchendika.gitpulse.github.GitHubClient;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubNotFoundException;
import io.github.abhiramchendika.gitpulse.github.model.GitHubUserProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

  private final UserData data = mock(UserData.class);
  private final ProfileService service =
      new ProfileService(data, new ProfileAnalyzer(5), Clock.fixed(NOW, ZoneOffset.UTC));

  private static GitHubUserProfile profile(String type, String blog) {
    return new GitHubUserProfile(
        "mona", type, "Mona", "a", "h", "bio", null, blog, "Earth", 3, 10, 2, NOW);
  }

  @Test
  void user_includesPublicEvents() {
    when(data.profile("mona")).thenReturn(profile("User", "https://mona.dev"));
    when(data.repositories("mona")).thenReturn(new UserData.Repositories(List.of(), false));
    when(data.events("mona"))
        .thenReturn(List.of(new PublicEvent("PushEvent", "mona/app", NOW.minusSeconds(60))));

    ProfileResponse r = service.analyze("mona");

    assertThat(r.meta().eventsAnalyzed()).isTrue();
    assertThat(r.statistics().events().count()).isEqualTo(1);
    assertThat(r.profile().blog()).isEqualTo("https://mona.dev");
  }

  @Test
  void organization_skipsEvents_andBlankBlogIsNull() {
    when(data.profile("github")).thenReturn(profile("Organization", ""));
    when(data.repositories("github")).thenReturn(new UserData.Repositories(List.of(), true));

    ProfileResponse r = service.analyze("github");

    assertThat(r.meta().eventsAnalyzed()).isFalse();
    assertThat(r.meta().repositoriesTruncated()).isTrue();
    assertThat(r.statistics().events()).isNull();
    assertThat(r.profile().blog()).isNull();
    verify(data, never()).events("github");
  }

  @Test
  void unknownUser_becomesUserNotFound() {
    GitHubClient client = mock(GitHubClient.class);
    when(client.getUser("NoSuchUser")).thenThrow(new GitHubNotFoundException());

    assertThatThrownBy(() -> new UserData(client).profile("NoSuchUser"))
        .isInstanceOf(UserNotFoundException.class)
        .hasMessageContaining("nosuchuser");
  }

  @Test
  void userData_dropsEventsWithoutTypeOrTime() {
    GitHubClient client = mock(GitHubClient.class);
    when(client.listPublicEvents(org.mockito.ArgumentMatchers.eq("mona"), anyInt()))
        .thenReturn(
            new io.github.abhiramchendika.gitpulse.github.PagedResult<>(
                List.of(
                    new io.github.abhiramchendika.gitpulse.github.model.GitHubEvent(
                        "PushEvent",
                        new io.github.abhiramchendika.gitpulse.github.model.GitHubEvent.Repo("a/b"),
                        NOW),
                    new io.github.abhiramchendika.gitpulse.github.model.GitHubEvent(
                        null, null, NOW)),
                1,
                false));

    assertThat(new UserData(client).events("mona")).hasSize(1);
  }
}
