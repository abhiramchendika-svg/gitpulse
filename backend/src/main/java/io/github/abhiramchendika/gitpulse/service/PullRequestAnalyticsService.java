package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.analysis.PullRequestAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.PullRequestStatistics;
import io.github.abhiramchendika.gitpulse.analysis.model.AnalysisWindow;
import io.github.abhiramchendika.gitpulse.analysis.model.PullRequestRecord;
import io.github.abhiramchendika.gitpulse.api.dto.AnalysisMeta;
import io.github.abhiramchendika.gitpulse.api.dto.PullRequestAnalyticsResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** Pull request activity for a window plus all-time totals. */
@Service
public class PullRequestAnalyticsService {

  private final RepositoryDataService data;
  private final PullRequestAnalyzer analyzer;
  private final AnalysisWindows windows;

  public PullRequestAnalyticsService(
      RepositoryDataService data, PullRequestAnalyzer analyzer, AnalysisWindows windows) {
    this.data = data;
    this.analyzer = analyzer;
    this.windows = windows;
  }

  public PullRequestAnalyticsResponse analyze(
      RepositoryRef ref, LocalDate since, LocalDate until, boolean excludeBots) {
    AnalysisWindows.Resolved window = windows.resolve(since, until);
    PullRequestSample sample = data.pullRequests(ref, window.requestedSince());

    // The list is ordered by creation date, so that is what defines coverage when truncated.
    Instant analysisSince =
        AnalysisWindows.effectiveSince(
            sample.truncated(),
            sample.pullRequests().stream().map(PullRequestRecord::createdAt),
            window.requestedSince(),
            window.analysisUntil());

    List<PullRequestRecord> records =
        excludeBots
            ? sample.pullRequests().stream().filter(pr -> !pr.bot()).toList()
            : sample.pullRequests();
    PullRequestStatistics statistics =
        analyzer.analyze(records, new AnalysisWindow(analysisSince, window.analysisUntil()));

    long open = data.openPullRequestCount(ref);
    long closed = data.closedPullRequestCount(ref);
    Long merged = data.mergedPullRequestCount(ref);
    PullRequestAnalyticsResponse.Totals totals =
        new PullRequestAnalyticsResponse.Totals(
            open, closed, merged, merged == null ? null : Math.max(0, closed - merged));

    AnalysisMeta meta =
        new AnalysisMeta(
            window.now(),
            analysisSince,
            window.analysisUntil(),
            window.requestedSince(),
            sample.pullRequests().size(),
            sample.truncated(),
            excludeBots,
            "UTC");
    return new PullRequestAnalyticsResponse(ref.fullName(), meta, totals, statistics);
  }
}
