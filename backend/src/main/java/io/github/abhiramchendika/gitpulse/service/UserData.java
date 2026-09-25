package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.analysis.model.OwnedRepository;
import io.github.abhiramchendika.gitpulse.analysis.model.PublicEvent;
import io.github.abhiramchendika.gitpulse.github.GitHubClient;
import io.github.abhiramchendika.gitpulse.github.PagedResult;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubNotFoundException;
import io.github.abhiramchendika.gitpulse.github.model.GitHubEvent;
import io.github.abhiramchendika.gitpulse.github.model.GitHubRepositorySummary;
import io.github.abhiramchendika.gitpulse.github.model.GitHubUserProfile;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Fetches, normalizes and caches public data about a GitHub account. Separate from {@link
 * RepositoryDataService} because it is keyed by account, not repository. Keys are lower-cased:
 * GitHub logins are case-insensitive.
 */
@Service
public class UserData {

  /** 3 pages x 100 = the 300 most recently pushed repositories. */
  static final int MAX_REPOSITORY_PAGES = 3;

  /** GitHub keeps at most 300 public events, so 3 pages always reach the end. */
  static final int MAX_EVENT_PAGES = 3;

  private final GitHubClient gitHubClient;

  public UserData(GitHubClient gitHubClient) {
    this.gitHubClient = gitHubClient;
  }

  @Cacheable(value = "users", key = "#username.toLowerCase()")
  public GitHubUserProfile profile(String username) {
    return onUser(username, () -> gitHubClient.getUser(username));
  }

  public record Repositories(List<OwnedRepository> repositories, boolean truncated) {}

  @Cacheable(value = "userRepositories", key = "#username.toLowerCase()")
  public Repositories repositories(String username) {
    PagedResult<GitHubRepositorySummary> page =
        onUser(username, () -> gitHubClient.listUserRepositories(username, MAX_REPOSITORY_PAGES));
    return new Repositories(
        page.items().stream().map(UserData::toOwnedRepository).toList(), page.truncated());
  }

  @Cacheable(value = "userEvents", key = "#username.toLowerCase()")
  public List<PublicEvent> events(String username) {
    PagedResult<GitHubEvent> page =
        onUser(username, () -> gitHubClient.listPublicEvents(username, MAX_EVENT_PAGES));
    return page.items().stream()
        .filter(e -> e.type() != null && e.createdAt() != null)
        .map(
            e ->
                new PublicEvent(e.type(), e.repo() == null ? null : e.repo().name(), e.createdAt()))
        .toList();
  }

  private static <T> T onUser(String username, Supplier<T> call) {
    try {
      return call.get();
    } catch (GitHubNotFoundException e) {
      throw new UserNotFoundException(username.toLowerCase(Locale.ROOT));
    }
  }

  static OwnedRepository toOwnedRepository(GitHubRepositorySummary r) {
    return new OwnedRepository(
        r.fullName(),
        r.htmlUrl(),
        r.description(),
        r.fork(),
        r.archived(),
        r.language(),
        r.stargazersCount(),
        r.forksCount(),
        r.createdAt(),
        r.pushedAt());
  }
}
