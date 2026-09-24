package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.analysis.IssueAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.IssueStatistics;
import io.github.abhiramchendika.gitpulse.analysis.model.AnalysisWindow;
import io.github.abhiramchendika.gitpulse.analysis.model.IssueRecord;
import io.github.abhiramchendika.gitpulse.api.dto.AnalysisMeta;
import io.github.abhiramchendika.gitpulse.api.dto.IssueAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.github.model.GitHubRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/** Issue activity for a window plus all-time totals, with pull requests excluded throughout. */
@Service
public class IssueAnalyticsService {

  private final RepositoryDataService data;
  private final IssueAnalyzer analyzer;
  private final AnalysisWindows windows;

  public IssueAnalyticsService(
      RepositoryDataService data, IssueAnalyzer analyzer, AnalysisWindows windows) {
    this.data = data;
    this.analyzer = analyzer;
    this.windows = windows;
  }

  public IssueAnalyticsResponse analyze(
      RepositoryRef ref, LocalDate since, LocalDate until, boolean excludeBots) {
    AnalysisWindows.Resolved window = windows.resolve(since, until);
    GitHubRepository repository = data.repository(ref);

    if (!repository.issuesEnabled()) {
      AnalysisMeta meta =
          new AnalysisMeta(
              window.now(),
              window.requestedSince(),
              window.analysisUntil(),
              window.requestedSince(),
              0,
              false,
              excludeBots,
              "UTC");
      return new IssueAnalyticsResponse(ref.fullName(), meta, false, null, null);
    }

    IssueSample sample = data.issues(ref, window.requestedSince());
    // Pull requests share the issue pages, so the oldest item of either kind marks coverage.
    Instant analysisSince =
        AnalysisWindows.effectiveSince(
            sample.truncated(),
            Stream.ofNullable(sample.oldestFetchedCreatedAt()),
            window.requestedSince(),
            window.analysisUntil());

    List<IssueRecord> records =
        excludeBots ? sample.issues().stream().filter(i -> !i.bot()).toList() : sample.issues();
    IssueStatistics statistics =
        analyzer.analyze(records, new AnalysisWindow(analysisSince, window.analysisUntil()));

    // GitHub's open_issues_count includes open pull requests; subtract them.
    IssueAnalyticsResponse.Totals totals =
        new IssueAnalyticsResponse.Totals(
            Math.max(0, repository.openIssuesCount() - data.openPullRequestCount(ref)),
            data.closedIssueCount(ref));

    AnalysisMeta meta =
        new AnalysisMeta(
            window.now(),
            analysisSince,
            window.analysisUntil(),
            window.requestedSince(),
            sample.issues().size(),
            sample.truncated(),
            excludeBots,
            "UTC");
    return new IssueAnalyticsResponse(ref.fullName(), meta, true, totals, statistics);
  }
}
