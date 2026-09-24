package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.analysis.ContributorAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.ContributorStatistics;
import io.github.abhiramchendika.gitpulse.analysis.model.ContributorLineStats;
import io.github.abhiramchendika.gitpulse.api.dto.ContributorAnalyticsResponse;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

/** Contributor distribution, with line counts where GitHub provides them. */
@Service
public class ContributorAnalyticsService {

  private final RepositoryDataService data;
  private final ContributorAnalyzer analyzer;
  private final Clock clock;

  public ContributorAnalyticsService(
      RepositoryDataService data, ContributorAnalyzer analyzer, Clock clock) {
    this.data = data;
    this.analyzer = analyzer;
    this.clock = clock;
  }

  public ContributorAnalyticsResponse analyze(RepositoryRef ref) {
    ContributorSample sample = data.contributors(ref);
    // Skip the (possibly slow) statistics request when there is nothing to attach it to.
    List<ContributorLineStats> lineStats =
        sample.available() && !sample.contributors().isEmpty()
            ? data.contributorLineStats(ref)
            : List.of();
    ContributorStatistics statistics = analyzer.analyze(sample.contributors(), lineStats);
    return new ContributorAnalyticsResponse(
        ref.fullName(), clock.instant(), sample.available(), sample.truncated(), statistics);
  }
}
