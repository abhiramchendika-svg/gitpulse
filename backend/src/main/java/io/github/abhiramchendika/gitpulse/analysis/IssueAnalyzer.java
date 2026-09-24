package io.github.abhiramchendika.gitpulse.analysis;

import io.github.abhiramchendika.gitpulse.analysis.model.AnalysisWindow;
import io.github.abhiramchendika.gitpulse.analysis.model.IssueRecord;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Issue activity for a window. Pure; no I/O. */
public class IssueAnalyzer {

  private final int topOpenerLimit;
  private final int recentLimit;

  public IssueAnalyzer(int topOpenerLimit, int recentLimit) {
    this.topOpenerLimit = topOpenerLimit;
    this.recentLimit = recentLimit;
  }

  public IssueStatistics analyze(List<IssueRecord> all, AnalysisWindow window) {
    List<IssueRecord> cohort =
        all.stream().filter(issue -> window.contains(issue.createdAt())).toList();
    List<IssueRecord> closed = cohort.stream().filter(i -> !i.open()).toList();

    Map<LocalDate, Integer> openedPerWeek =
        TimeBuckets.weekly(cohort.stream().map(IssueRecord::createdAt).toList(), window);
    Map<LocalDate, Integer> closedPerWeek =
        TimeBuckets.weekly(closed.stream().map(IssueRecord::closedAt).toList(), window);

    int completed = (int) closed.stream().filter(i -> "completed".equals(i.stateReason())).count();
    int notPlanned =
        (int) closed.stream().filter(i -> "not_planned".equals(i.stateReason())).count();

    return new IssueStatistics(
        cohort.size(),
        closed.size(),
        cohort.size() - closed.size(),
        (int) cohort.stream().filter(IssueRecord::bot).count(),
        completed,
        notPlanned,
        closed.size() - completed - notPlanned,
        Durations.summarise(
            closed.stream().map(i -> Duration.between(i.createdAt(), i.closedAt())).toList()),
        openedPerWeek.keySet().stream()
            .map(
                week ->
                    new IssueStatistics.Week(
                        week, openedPerWeek.get(week), closedPerWeek.getOrDefault(week, 0)))
            .toList(),
        Rankings.top(
            cohort.stream().map(i -> new Rankings.Actor(i.authorLogin(), i.bot())).toList(),
            topOpenerLimit),
        cohort.stream()
            .sorted(Comparator.comparing(IssueRecord::createdAt).reversed())
            .limit(recentLimit)
            .map(
                i ->
                    new IssueStatistics.Recent(
                        i.number(),
                        i.title(),
                        i.authorLogin(),
                        i.createdAt(),
                        i.open(),
                        i.comments(),
                        i.htmlUrl()))
            .toList());
  }
}
