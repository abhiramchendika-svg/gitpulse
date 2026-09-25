package io.github.abhiramchendika.gitpulse.explanation;

import io.github.abhiramchendika.gitpulse.api.dto.CommitAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.api.dto.ExplanationResponse;
import io.github.abhiramchendika.gitpulse.config.AiProperties;
import io.github.abhiramchendika.gitpulse.service.ActivityService;
import io.github.abhiramchendika.gitpulse.service.CommitAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.ContributorAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.IssueAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.PullRequestAnalyticsService;
import io.github.abhiramchendika.gitpulse.service.RepositoryRef;
import io.github.abhiramchendika.gitpulse.service.RepositoryService;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes a short, verified plain-English summary of a repository's dashboard.
 *
 * <p>Flow: re-run the same analyses the dashboard shows (served from the GitHub-data cache when the
 * dashboard was just opened), reduce them to a {@link FactSheet}, check the hourly budget, ask the
 * model, then keep only sentences that pass {@link ExplanationVerifier}. Results are cached, so a
 * repeated click costs nothing and does not count against the budget.
 */
@Service
public class ExplanationService {

  private static final Logger log = LoggerFactory.getLogger(ExplanationService.class);

  private final AiProperties properties;
  private final ExplanationModel model;
  private final HourlyLimiter limiter;
  private final ExplanationVerifier verifier = new ExplanationVerifier();
  private final JsonMapper json;
  private final Clock clock;
  private final RepositoryService repositoryService;
  private final ActivityService activityService;
  private final CommitAnalyticsService commitService;
  private final PullRequestAnalyticsService pullRequestService;
  private final IssueAnalyticsService issueService;
  private final ContributorAnalyticsService contributorService;

  public ExplanationService(
      AiProperties properties,
      ExplanationModel model,
      JsonMapper json,
      Clock clock,
      RepositoryService repositoryService,
      ActivityService activityService,
      CommitAnalyticsService commitService,
      PullRequestAnalyticsService pullRequestService,
      IssueAnalyticsService issueService,
      ContributorAnalyticsService contributorService) {
    this.properties = properties;
    this.model = model;
    this.limiter = new HourlyLimiter(properties.maxExplanationsPerHour(), clock);
    this.json = json;
    this.clock = clock;
    this.repositoryService = repositoryService;
    this.activityService = activityService;
    this.commitService = commitService;
    this.pullRequestService = pullRequestService;
    this.issueService = issueService;
    this.contributorService = contributorService;
  }

  public boolean enabled() {
    return properties.enabled();
  }

  /**
   * @throws ExplanationsDisabledException no API key is configured
   * @throws ExplanationLimitException the hourly budget is used up
   * @throws ExplanationUnavailableException the model could not be reached
   * @throws UnreliableExplanationException the answer could not be verified
   */
  @Cacheable(value = "explanations", key = "{#ref, #since, #until, #excludeBots}")
  public ExplanationResponse explain(
      RepositoryRef ref, LocalDate since, LocalDate until, boolean excludeBots) {
    if (!enabled()) {
      throw new ExplanationsDisabledException();
    }
    // GitHub errors (not found, rate limited) surface here, before any paid model call.
    CommitAnalyticsResponse commits = commitService.analyze(ref, since, until, excludeBots);
    FactSheet sheet =
        FactSheet.from(
            new FactSheet.Inputs(
                repositoryService.overview(ref),
                activityService.analyze(ref),
                commits,
                pullRequestService.analyze(ref, since, until, excludeBots),
                issueService.analyze(ref, since, until, excludeBots),
                contributorService.analyze(ref),
                repositoryService.languages(ref)));

    limiter.acquire();
    ExplanationModel.Reply reply =
        model.draft(ExplanationPrompt.SYSTEM, ExplanationPrompt.userMessage(sheet, json));
    ExplanationVerifier.Result result = verifier.verify(reply.draft(), sheet);
    log.info(
        "Explanation for {}: {} sentences kept, {} removed by verification",
        ref.fullName(),
        result.sentences().size(),
        result.removed());
    if (result.sentences().size() < ExplanationVerifier.MIN_SENTENCES) {
      throw new UnreliableExplanationException(
          "Too few sentences could be verified against the numbers.");
    }

    return new ExplanationResponse(
        ref.fullName(),
        clock.instant(),
        reply.model(),
        new ExplanationResponse.Window(
            commits.meta().since(), commits.meta().until(), commits.meta().botsExcluded()),
        result.sentences().stream()
            .map(s -> new ExplanationResponse.Sentence(s.text(), s.facts()))
            .toList(),
        result.removed());
  }
}
