package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.analysis.CommitAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.CommitStatistics;
import io.github.abhiramchendika.gitpulse.analysis.model.AnalysisWindow;
import io.github.abhiramchendika.gitpulse.analysis.model.CommitRecord;
import io.github.abhiramchendika.gitpulse.api.dto.AnalysisMeta;
import io.github.abhiramchendika.gitpulse.api.dto.CommitAnalyticsResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** Commit activity for a date window. */
@Service
public class CommitAnalyticsService {

  private final RepositoryDataService data;
  private final CommitAnalyzer analyzer;
  private final AnalysisWindows windows;

  public CommitAnalyticsService(
      RepositoryDataService data, CommitAnalyzer analyzer, AnalysisWindows windows) {
    this.data = data;
    this.analyzer = analyzer;
    this.windows = windows;
  }

  /**
   * @param since first day to include (UTC), or null for the default window
   * @param until last day to include (UTC), or null for today
   */
  public CommitAnalyticsResponse analyze(
      RepositoryRef ref, LocalDate since, LocalDate until, boolean excludeBots) {
    AnalysisWindows.Resolved window = windows.resolve(since, until);

    CommitSample sample = data.commits(ref, window.requestedSince(), window.requestedUntil());
    long totalAllTime = sample.emptyRepository() ? 0 : data.commitCount(ref);

    // GitHub pages by *commit* date, so the oldest commit date marks where coverage ends. Any
    // commit authored after that point was also committed after it, so it is in the sample.
    // (Using the author date here is wrong: patches are often authored long before they land.)
    Instant analysisSince =
        AnalysisWindows.effectiveSince(
            sample.truncated(),
            sample.commits().stream().map(CommitRecord::committedAt),
            window.requestedSince(),
            window.analysisUntil());

    List<CommitRecord> commits =
        excludeBots ? sample.commits().stream().filter(c -> !c.bot()).toList() : sample.commits();
    CommitStatistics statistics =
        analyzer.analyze(commits, new AnalysisWindow(analysisSince, window.analysisUntil()));

    AnalysisMeta meta =
        new AnalysisMeta(
            window.now(),
            analysisSince,
            window.analysisUntil(),
            window.requestedSince(),
            sample.commits().size(),
            sample.truncated(),
            excludeBots,
            "UTC");
    return new CommitAnalyticsResponse(
        ref.fullName(), meta, sample.emptyRepository(), totalAllTime, statistics);
  }
}
