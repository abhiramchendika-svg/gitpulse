package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.analysis.CommitStatistics;
import io.github.abhiramchendika.gitpulse.analysis.Numbers;
import io.github.abhiramchendika.gitpulse.api.dto.CommitAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.api.dto.ComparisonResponse;
import io.github.abhiramchendika.gitpulse.api.dto.RepositoryOverviewResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Side-by-side facts for several repositories. Reuses the existing services (and therefore their
 * caches), and analyses the repositories in parallel: the work is almost entirely waiting for
 * GitHub, so two repositories take about as long as one.
 */
@Service
public class ComparisonService {

  static final int TOP_LANGUAGES = 3;

  private final RepositoryService repositoryService;
  private final CommitAnalyticsService commitAnalyticsService;
  private final ActivityService activityService;
  private final RepositoryDataService data;
  private final Clock clock;

  public ComparisonService(
      RepositoryService repositoryService,
      CommitAnalyticsService commitAnalyticsService,
      ActivityService activityService,
      RepositoryDataService data,
      Clock clock) {
    this.repositoryService = repositoryService;
    this.commitAnalyticsService = commitAnalyticsService;
    this.activityService = activityService;
    this.data = data;
    this.clock = clock;
  }

  public ComparisonResponse compare(List<RepositoryRef> refs) {
    List<ComparisonResponse.Summary> summaries =
        BoundedParallel.map(refs, refs.size(), this::summarise);
    return new ComparisonResponse(clock.instant(), summaries);
  }

  ComparisonResponse.Summary summarise(RepositoryRef ref) {
    RepositoryOverviewResponse overview = repositoryService.overview(ref);
    CommitAnalyticsResponse commits = commitAnalyticsService.analyze(ref, null, null, false);
    CommitStatistics c = commits.statistics();
    double ageYears =
        overview.createdAt() == null
            ? 0
            : Numbers.round(
                Duration.between(overview.createdAt(), clock.instant()).toDays() / 365.2425, 1);

    return new ComparisonResponse.Summary(
        overview.fullName(),
        overview.htmlUrl(),
        overview.description(),
        overview.stars(),
        overview.forks(),
        overview.watchers(),
        overview.openIssuesAndPullRequests(),
        overview.primaryLanguage(),
        repositoryService.languages(ref).statistics().languages().stream()
            .limit(TOP_LANGUAGES)
            .toList(),
        overview.license() == null ? null : overview.license().spdxId(),
        overview.createdAt(),
        ageYears,
        overview.pushedAt(),
        overview.archived(),
        data.contributorCount(ref),
        new ComparisonResponse.CommitSummary(
            c.totalCommits(),
            c.averagePerWeek(),
            c.activeWeeks(),
            c.totalWeeks(),
            c.distinctAuthors(),
            commits.meta().truncated(),
            commits.meta().since()),
        c.weekly(),
        activityService.analyze(ref).indicators());
  }
}
