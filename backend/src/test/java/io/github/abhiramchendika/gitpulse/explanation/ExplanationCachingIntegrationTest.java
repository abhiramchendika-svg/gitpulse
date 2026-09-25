package io.github.abhiramchendika.gitpulse.explanation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.abhiramchendika.gitpulse.service.ActivityService;
import io.github.abhiramchendika.gitpulse.service.CommitAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.ContributorAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.IssueAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.PullRequestAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.RepositoryRef;
import io.github.abhiramchendika.gitpulse.service.RepositoryService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * A repeated click must be free: served from the cache, not counted against the hourly limit. This
 * needs the real Spring context, because {@code @Cacheable} only works through Spring's proxy.
 */
@SpringBootTest(
    properties = {"gitpulse.ai.api-key=test-key", "gitpulse.ai.max-explanations-per-hour=1"})
class ExplanationCachingIntegrationTest {

  @MockitoBean private ExplanationModel model;
  @MockitoBean private RepositoryService repositoryService;
  @MockitoBean private ActivityService activityService;
  @MockitoBean private CommitAnalyticsService commitService;
  @MockitoBean private PullRequestAnalyticsService pullRequestService;
  @MockitoBean private IssueAnalyticsService issueService;
  @MockitoBean private ContributorAnalyticsService contributorService;

  @Autowired private ExplanationService service;

  @Test
  void repeatedRequestsAreCached_andDoNotUseTheHourlyBudget() {
    FactSheet.Inputs in = ExplanationFixtures.inputs();
    when(repositoryService.overview(any())).thenReturn(in.overview());
    when(repositoryService.languages(any())).thenReturn(in.languages());
    when(activityService.analyze(any())).thenReturn(in.activity());
    when(commitService.analyze(any(), any(), any(), any(Boolean.class))).thenReturn(in.commits());
    when(pullRequestService.analyze(any(), any(), any(), any(Boolean.class)))
        .thenReturn(in.pullRequests());
    when(issueService.analyze(any(), any(), any(), any(Boolean.class))).thenReturn(in.issues());
    when(contributorService.analyze(any())).thenReturn(in.contributors());
    when(model.draft(anyString(), anyString()))
        .thenReturn(
            new ExplanationModel.Reply(
                new ExplanationDraft(
                    List.of(
                        new ExplanationDraft.Sentence(
                            "There were 240 commits.", List.of("commits.total")),
                        new ExplanationDraft.Sentence(
                            "It has 1,734 stars.", List.of("repository.stars")))),
                "claude-opus-5"));
    RepositoryRef ref = new RepositoryRef("octocat", "hello");

    var first = service.explain(ref, null, null, false);
    var again = service.explain(new RepositoryRef("OctoCat", "Hello"), null, null, false);

    assertThat(again).isEqualTo(first);
    verify(model, times(1)).draft(anyString(), anyString());
    // The limit is 1 per hour, so a genuinely new request is refused...
    assertThatThrownBy(() -> service.explain(ref, LocalDate.of(2026, 6, 27), null, false))
        .isInstanceOf(ExplanationLimitException.class);
    // ...and a refused request is not cached as a failure: the cached answer is still served.
    assertThat(service.explain(ref, null, null, false)).isEqualTo(first);
  }
}
