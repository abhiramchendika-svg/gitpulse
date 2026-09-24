package io.github.abhiramchendika.gitpulse.analysis;

import io.github.abhiramchendika.gitpulse.analysis.model.AnalysisWindow;
import io.github.abhiramchendika.gitpulse.analysis.model.PullRequestRecord;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Pull request activity for a window. Pure; no I/O. */
public class PullRequestAnalyzer {

  private final int topAuthorLimit;
  private final int recentLimit;

  public PullRequestAnalyzer(int topAuthorLimit, int recentLimit) {
    this.topAuthorLimit = topAuthorLimit;
    this.recentLimit = recentLimit;
  }

  public PullRequestStatistics analyze(List<PullRequestRecord> all, AnalysisWindow window) {
    // The cohort: pull requests opened inside the window.
    List<PullRequestRecord> cohort =
        all.stream().filter(pr -> window.contains(pr.createdAt())).toList();

    int merged = (int) cohort.stream().filter(PullRequestRecord::merged).count();
    int closedWithoutMerge =
        (int) cohort.stream().filter(PullRequestRecord::closedWithoutMerge).count();

    Map<LocalDate, Integer> openedPerWeek =
        TimeBuckets.weekly(cohort.stream().map(PullRequestRecord::createdAt).toList(), window);
    Map<LocalDate, Integer> mergedPerWeek =
        TimeBuckets.weekly(
            cohort.stream()
                .filter(PullRequestRecord::merged)
                .map(PullRequestRecord::mergedAt)
                .toList(),
            window);

    return new PullRequestStatistics(
        cohort.size(),
        merged,
        closedWithoutMerge,
        (int) cohort.stream().filter(PullRequestRecord::open).count(),
        (int) cohort.stream().filter(PullRequestRecord::bot).count(),
        merged + closedWithoutMerge == 0
            ? null
            : Numbers.percent(merged, merged + closedWithoutMerge),
        Durations.summarise(
            cohort.stream()
                .filter(PullRequestRecord::merged)
                .map(pr -> Duration.between(pr.createdAt(), pr.mergedAt()))
                .toList()),
        openedPerWeek.keySet().stream()
            .map(
                week ->
                    new PullRequestStatistics.Week(
                        week, openedPerWeek.get(week), mergedPerWeek.getOrDefault(week, 0)))
            .toList(),
        Rankings.top(
            cohort.stream().map(pr -> new Rankings.Actor(pr.authorLogin(), pr.bot())).toList(),
            topAuthorLimit),
        cohort.stream()
            .sorted(Comparator.comparing(PullRequestRecord::createdAt).reversed())
            .limit(recentLimit)
            .map(
                pr ->
                    new PullRequestStatistics.Recent(
                        pr.number(),
                        pr.title(),
                        pr.authorLogin(),
                        pr.draft(),
                        pr.createdAt(),
                        pr.merged() ? "merged" : pr.open() ? "open" : "closed",
                        pr.htmlUrl()))
            .toList());
  }
}
