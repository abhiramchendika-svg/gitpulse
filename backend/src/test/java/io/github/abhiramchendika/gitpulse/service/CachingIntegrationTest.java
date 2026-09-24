package io.github.abhiramchendika.gitpulse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.abhiramchendika.gitpulse.github.GitHubClient;
import io.github.abhiramchendika.gitpulse.github.StatsResult;
import io.github.abhiramchendika.gitpulse.github.model.GitHubContributorStats;
import io.github.abhiramchendika.gitpulse.github.model.GitHubSearchResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Proves the cache actually saves GitHub requests. This needs the real Spring context, because
 * {@code @Cacheable} only works through Spring's proxy.
 */
@SpringBootTest
class CachingIntegrationTest {

  @MockitoBean private GitHubClient gitHubClient;

  @Autowired private RepositoryDataService data;

  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void clearCaches() {
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  @Test
  void secondCallIsServedFromCache_andNameCaseDoesNotMatter() {
    when(gitHubClient.getLanguages("octocat", "hello")).thenReturn(Map.of("Java", 10L));

    data.languages(new RepositoryRef("octocat", "hello"));
    data.languages(new RepositoryRef("OctoCat", "Hello"));

    verify(gitHubClient, times(1)).getLanguages("octocat", "hello");
  }

  /**
   * Several methods share the "counts" cache with the same argument. Without the method name in the
   * key they would return each other's values.
   */
  @Test
  void countMethodsSharingACache_doNotCollide() {
    RepositoryRef ref = new RepositoryRef("octocat", "hello");
    when(gitHubClient.countPullRequests("octocat", "hello", "open")).thenReturn(3L);
    when(gitHubClient.countPullRequests("octocat", "hello", "closed")).thenReturn(40L);
    when(gitHubClient.searchIssueCount("repo:octocat/hello is:issue is:closed"))
        .thenReturn(new GitHubSearchResult(90, false));
    when(gitHubClient.searchIssueCount("repo:octocat/hello is:pr is:merged"))
        .thenReturn(new GitHubSearchResult(35, false));

    assertThat(data.openPullRequestCount(ref)).isEqualTo(3);
    assertThat(data.closedPullRequestCount(ref)).isEqualTo(40);
    assertThat(data.closedIssueCount(ref)).isEqualTo(90);
    assertThat(data.mergedPullRequestCount(ref)).isEqualTo(35);
    // And each is cached independently.
    assertThat(data.openPullRequestCount(ref)).isEqualTo(3);
    assertThat(data.closedIssueCount(ref)).isEqualTo(90);
    verify(gitHubClient, times(1)).countPullRequests("octocat", "hello", "open");
    verify(gitHubClient, times(1)).searchIssueCount("repo:octocat/hello is:issue is:closed");
  }

  @Test
  void pendingStatsAreNotCached_readyStatsAre() {
    RepositoryRef ref = new RepositoryRef("octocat", "hello");
    when(gitHubClient.getContributorStats("octocat", "hello"))
        .thenReturn(StatsResult.<GitHubContributorStats>computing())
        .thenReturn(StatsResult.<GitHubContributorStats>ready(List.of()));

    assertThat(data.contributorLineStats(ref)).isNull(); // 202: not cached
    assertThat(data.contributorLineStats(ref)).isEmpty(); // asks GitHub again
    assertThat(data.contributorLineStats(ref)).isEmpty(); // now cached

    verify(gitHubClient, times(2)).getContributorStats("octocat", "hello");
  }
}
