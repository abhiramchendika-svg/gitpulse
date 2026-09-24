package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.analysis.model.CommitRecord;
import io.github.abhiramchendika.gitpulse.analysis.model.ContributorLineStats;
import io.github.abhiramchendika.gitpulse.analysis.model.ContributorRecord;
import io.github.abhiramchendika.gitpulse.config.AnalysisProperties;
import io.github.abhiramchendika.gitpulse.github.GitHubClient;
import io.github.abhiramchendika.gitpulse.github.PagedResult;
import io.github.abhiramchendika.gitpulse.github.StatsResult;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubApiException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubEmptyRepositoryException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubNotFoundException;
import io.github.abhiramchendika.gitpulse.github.model.GitHubCommit;
import io.github.abhiramchendika.gitpulse.github.model.GitHubContributor;
import io.github.abhiramchendika.gitpulse.github.model.GitHubContributorStats;
import io.github.abhiramchendika.gitpulse.github.model.GitHubRepository;
import io.github.abhiramchendika.gitpulse.github.model.GitHubUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Fetches repository data from GitHub, normalizes it into analysis records, and caches it.
 *
 * <p>Caching happens here, on <em>raw data</em>, rather than on final analytics: GitHub requests
 * are the scarce resource, while re-running an analyzer over cached data costs microseconds. So
 * different views of the same data (e.g. with bots excluded) cost zero extra GitHub requests.
 *
 * <p>Note: {@code @Cacheable} works through a Spring proxy, so these methods must be called from
 * <em>other</em> beans. A call from within this class would bypass the cache.
 */
@Service
public class RepositoryDataService {

  private static final Logger log = LoggerFactory.getLogger(RepositoryDataService.class);

  static final int MAX_HEADLINE_LENGTH = 200;

  private final GitHubClient gitHubClient;
  private final AnalysisProperties properties;

  public RepositoryDataService(GitHubClient gitHubClient, AnalysisProperties properties) {
    this.gitHubClient = gitHubClient;
    this.properties = properties;
  }

  @Cacheable("repositories")
  public GitHubRepository repository(RepositoryRef ref) {
    return onRepository(ref, () -> gitHubClient.getRepository(ref.owner(), ref.repo()));
  }

  @Cacheable("languages")
  public Map<String, Long> languages(RepositoryRef ref) {
    return onRepository(ref, () -> gitHubClient.getLanguages(ref.owner(), ref.repo()));
  }

  @Cacheable("commits")
  public CommitSample commits(RepositoryRef ref, Instant since, Instant until) {
    try {
      PagedResult<GitHubCommit> page =
          onRepository(
              ref,
              () ->
                  gitHubClient.listCommits(
                      ref.owner(), ref.repo(), since, until, properties.maxCommitPages()));
      List<CommitRecord> records =
          page.items().stream()
              .map(RepositoryDataService::toCommitRecord)
              .filter(Objects::nonNull)
              .toList();
      return new CommitSample(records, page.truncated(), false);
    } catch (GitHubEmptyRepositoryException e) {
      return new CommitSample(List.of(), false, true);
    }
  }

  /** All-time commit count on the default branch (one request). */
  @Cacheable("commitCounts")
  public long commitCount(RepositoryRef ref) {
    try {
      return onRepository(ref, () -> gitHubClient.countCommits(ref.owner(), ref.repo()));
    } catch (GitHubEmptyRepositoryException e) {
      return 0;
    }
  }

  @Cacheable("contributors")
  public ContributorSample contributors(RepositoryRef ref) {
    try {
      PagedResult<GitHubContributor> page =
          onRepository(
              ref,
              () ->
                  gitHubClient.listContributors(
                      ref.owner(), ref.repo(), properties.maxContributorPages()));
      List<ContributorRecord> records =
          page.items().stream()
              .map(
                  c ->
                      new ContributorRecord(
                          c.login(),
                          c.avatarUrl(),
                          c.htmlUrl(),
                          isBot(c.login(), c.type()),
                          c.contributions()))
              .toList();
      return new ContributorSample(records, page.truncated(), true);
    } catch (GitHubApiException e) {
      // GitHub answers 403 "contributor list is too large" for some huge repositories.
      if (e.getStatus() == 403) {
        log.info("GitHub declined to list contributors for {}", ref.fullName());
        return ContributorSample.unavailable();
      }
      throw e;
    }
  }

  /**
   * All-time line statistics per contributor, or {@code null} while GitHub is still computing them.
   * Null results are not cached, so the next call asks GitHub again.
   */
  @Cacheable(value = "contributorStats", unless = "#result == null")
  public List<ContributorLineStats> contributorLineStats(RepositoryRef ref) {
    StatsResult<GitHubContributorStats> stats;
    try {
      stats = onRepository(ref, () -> gitHubClient.getContributorStats(ref.owner(), ref.repo()));
    } catch (GitHubApiException e) {
      if (e.getStatus() == 403) {
        return List.of();
      }
      throw e;
    }
    if (stats.pending()) {
      return null;
    }
    return stats.items().stream()
        .filter(s -> s.author() != null && s.author().login() != null)
        .map(RepositoryDataService::toLineStats)
        .toList();
  }

  /** Translates "404 from GitHub" into the domain meaning "this repository does not exist". */
  private static <T> T onRepository(RepositoryRef ref, Supplier<T> call) {
    try {
      return call.get();
    } catch (GitHubNotFoundException e) {
      throw new RepositoryNotFoundException(ref);
    }
  }

  static CommitRecord toCommitRecord(GitHubCommit commit) {
    GitHubCommit.Details details = commit.commit();
    if (commit.sha() == null || details == null) {
      return null;
    }
    Instant authoredAt = firstNonNull(dateOf(details.author()), dateOf(details.committer()));
    if (authoredAt == null) {
      return null;
    }
    Instant committedAt = firstNonNull(dateOf(details.committer()), authoredAt);
    GitHubUser account = commit.author();
    String login = account == null ? null : account.login();
    String gitName = details.author() == null ? null : details.author().name();
    String displayName = login != null ? login : (gitName != null ? gitName : "unknown");
    return new CommitRecord(
        commit.sha(),
        login != null ? login : "git:" + displayName,
        displayName,
        login,
        authoredAt,
        committedAt,
        commit.parents() != null && commit.parents().size() > 1,
        account != null && isBot(login, account.type()),
        headline(details.message()),
        commit.htmlUrl());
  }

  static boolean isBot(String login, String type) {
    return "Bot".equals(type) || (login != null && login.endsWith("[bot]"));
  }

  static String headline(String message) {
    if (message == null) {
      return "";
    }
    int newline = message.indexOf('\n');
    String first = (newline >= 0 ? message.substring(0, newline) : message).strip();
    return first.length() <= MAX_HEADLINE_LENGTH
        ? first
        : first.substring(0, MAX_HEADLINE_LENGTH - 1) + "…";
  }

  private static ContributorLineStats toLineStats(GitHubContributorStats stats) {
    long additions = 0;
    long deletions = 0;
    int commits = 0;
    if (stats.weeks() != null) {
      for (GitHubContributorStats.Week week : stats.weeks()) {
        additions += week.a();
        deletions += week.d();
        commits += week.c();
      }
    }
    return new ContributorLineStats(stats.author().login(), additions, deletions, commits);
  }

  private static Instant dateOf(GitHubCommit.GitIdentity identity) {
    return identity == null ? null : identity.date();
  }

  private static Instant firstNonNull(Instant a, Instant b) {
    return a != null ? a : b;
  }
}
