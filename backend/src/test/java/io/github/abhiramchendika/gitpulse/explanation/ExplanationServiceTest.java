package io.github.abhiramchendika.gitpulse.explanation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.abhiramchendika.gitpulse.api.dto.ExplanationResponse;
import io.github.abhiramchendika.gitpulse.config.AiProperties;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubRateLimitException;
import io.github.abhiramchendika.gitpulse.service.ActivityService;
import io.github.abhiramchendika.gitpulse.service.CommitAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.ContributorAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.IssueAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.PullRequestAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.RepositoryRef;
import io.github.abhiramchendika.gitpulse.service.RepositoryService;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ExplanationServiceTest {

  private static final RepositoryRef REF = new RepositoryRef("octocat", "hello");

  private final RepositoryService repositoryService = mock(RepositoryService.class);
  private final ActivityService activityService = mock(ActivityService.class);
  private final CommitAnalyticsService commitService = mock(CommitAnalyticsService.class);
  private final PullRequestAnalyticsService pullRequestService =
      mock(PullRequestAnalyticsService.class);
  private final IssueAnalyticsService issueService = mock(IssueAnalyticsService.class);
  private final ContributorAnalyticsService contributorService =
      mock(ContributorAnalyticsService.class);

  /** Answers with scripted drafts and records what it was sent. */
  private final List<String> prompts = new ArrayList<>();

  private ExplanationDraft nextDraft;
  private final ExplanationModel fakeModel =
      (system, user) -> {
        prompts.add(user);
        return new ExplanationModel.Reply(nextDraft, "claude-opus-5");
      };

  @BeforeEach
  void stubAnalyses() {
    FactSheet.Inputs in = ExplanationFixtures.inputs();
    when(repositoryService.overview(REF)).thenReturn(in.overview());
    when(repositoryService.languages(REF)).thenReturn(in.languages());
    when(activityService.analyze(REF)).thenReturn(in.activity());
    when(commitService.analyze(REF, null, null, false)).thenReturn(in.commits());
    when(pullRequestService.analyze(REF, null, null, false)).thenReturn(in.pullRequests());
    when(issueService.analyze(REF, null, null, false)).thenReturn(in.issues());
    when(contributorService.analyze(REF)).thenReturn(in.contributors());
  }

  private ExplanationService service(String apiKey, int perHour) {
    AiProperties properties =
        new AiProperties(
            apiKey,
            URI.create("https://api.anthropic.com"),
            "claude-opus-5",
            perHour,
            8000,
            Duration.ofSeconds(60),
            true);
    return new ExplanationService(
        properties,
        fakeModel,
        JsonMapper.builder().build(),
        Clock.fixed(ExplanationFixtures.NOW, ZoneOffset.UTC),
        repositoryService,
        activityService,
        commitService,
        pullRequestService,
        issueService,
        contributorService);
  }

  private static ExplanationDraft.Sentence sentence(String text, String... facts) {
    return new ExplanationDraft.Sentence(text, List.of(facts));
  }

  @Test
  void returnsOnlyVerifiedSentences_withTheirFacts() {
    nextDraft =
        new ExplanationDraft(
            List.of(
                sentence(
                    "In the last 365 days there were 240 commits.", "window.days", "commits.total"),
                sentence("The repository has 1,734 stars.", "repository.stars"),
                sentence("This healthy project has 25 contributors.", "contributors.count")));

    ExplanationResponse response = service("key", 20).explain(REF, null, null, false);

    assertThat(response.sentences())
        .extracting(ExplanationResponse.Sentence::text)
        .containsExactly(
            "In the last 365 days there were 240 commits.", "The repository has 1,734 stars.");
    assertThat(response.sentencesRemoved()).isEqualTo(1);
    assertThat(response.sentences().get(0).basedOn())
        .extracting(Fact::id)
        .containsExactly("window.days", "commits.total");
    assertThat(response.model()).isEqualTo("claude-opus-5");
    assertThat(response.window().since()).isEqualTo(ExplanationFixtures.SINCE);
    // What the model saw: numbers only, no free text from GitHub.
    assertThat(prompts).singleElement().asString().doesNotContain(ExplanationFixtures.INJECTION);
  }

  @Test
  void tooFewVerifiedSentences_rejectsTheWholeExplanation() {
    nextDraft =
        new ExplanationDraft(
            List.of(
                sentence("There were 240 commits.", "commits.total"),
                sentence("There were 9,999 stars.", "repository.stars")));

    assertThatThrownBy(() -> service("key", 20).explain(REF, null, null, false))
        .isInstanceOf(UnreliableExplanationException.class);
  }

  @Test
  void disabledWithoutAKey_beforeAnyWork() {
    ExplanationService service = service("", 20);

    assertThat(service.enabled()).isFalse();
    assertThatThrownBy(() -> service.explain(REF, null, null, false))
        .isInstanceOf(ExplanationsDisabledException.class);
    assertThat(prompts).isEmpty();
  }

  @Test
  void hourlyLimit_countsModelCalls() {
    nextDraft =
        new ExplanationDraft(
            List.of(
                sentence("There were 240 commits.", "commits.total"),
                sentence("It has 1,734 stars.", "repository.stars")));
    ExplanationService service = service("key", 2);

    service.explain(REF, null, null, false);
    service.explain(REF, null, null, false);
    assertThatThrownBy(() -> service.explain(REF, null, null, false))
        .isInstanceOf(ExplanationLimitException.class);
    assertThat(prompts).hasSize(2);
  }

  @Test
  void gitHubErrors_happenBeforeAnyPaidCall() {
    when(commitService.analyze(REF, null, null, false))
        .thenThrow(new GitHubRateLimitException(null, null));

    assertThatThrownBy(() -> service("key", 1).explain(REF, null, null, false))
        .isInstanceOf(GitHubRateLimitException.class);
    assertThat(prompts).isEmpty();
  }
}
