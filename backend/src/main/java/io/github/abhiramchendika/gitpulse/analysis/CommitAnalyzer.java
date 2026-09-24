package io.github.abhiramchendika.gitpulse.analysis;

import io.github.abhiramchendika.gitpulse.analysis.CommitStatistics.AuthorActivity;
import io.github.abhiramchendika.gitpulse.analysis.CommitStatistics.InactivityPeriod;
import io.github.abhiramchendika.gitpulse.analysis.CommitStatistics.MonthCount;
import io.github.abhiramchendika.gitpulse.analysis.CommitStatistics.RecentCommit;
import io.github.abhiramchendika.gitpulse.analysis.CommitStatistics.WeekCount;
import io.github.abhiramchendika.gitpulse.analysis.model.AnalysisWindow;
import io.github.abhiramchendika.gitpulse.analysis.model.CommitRecord;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a list of commits into activity statistics. Pure function of its inputs: no I/O, no clock,
 * no Spring. That is what makes every number here unit-testable with hand-written data.
 *
 * <p>Complexity: O(n log n) for n commits (one sort for gaps and one for recent commits); all
 * grouping is done with hash maps in O(n).
 */
public class CommitAnalyzer {

  /** Average Gregorian month length in days (365.2425 / 12). */
  static final double DAYS_PER_MONTH = 30.436875;

  private final Duration inactivityThreshold;
  private final int topAuthorLimit;
  private final int recentCommitLimit;
  private final int inactivityPeriodLimit;

  public CommitAnalyzer(
      Duration inactivityThreshold,
      int topAuthorLimit,
      int recentCommitLimit,
      int inactivityPeriodLimit) {
    this.inactivityThreshold = inactivityThreshold;
    this.topAuthorLimit = topAuthorLimit;
    this.recentCommitLimit = recentCommitLimit;
    this.inactivityPeriodLimit = inactivityPeriodLimit;
  }

  public CommitStatistics analyze(List<CommitRecord> allCommits, AnalysisWindow window) {
    // Only commits authored inside the window count. (GitHub filters by commit date, which can
    // differ from author date after a rebase, so this keeps the maths consistent.)
    List<CommitRecord> commits =
        allCommits.stream().filter(c -> window.contains(c.authoredAt())).toList();

    int total = commits.size();
    int merges = (int) commits.stream().filter(CommitRecord::merge).count();
    int bots = (int) commits.stream().filter(CommitRecord::bot).count();

    List<WeekCount> weekly = weekly(commits, window);
    int activeWeeks = (int) weekly.stream().filter(w -> w.commits() > 0).count();

    int[][] heatmap = new int[7][24];
    for (CommitRecord commit : commits) {
      ZonedDateTime utc = commit.authoredAt().atZone(ZoneOffset.UTC);
      heatmap[utc.getDayOfWeek().getValue() - 1][utc.getHour()]++;
    }

    List<CommitRecord> chronological =
        commits.stream().sorted(Comparator.comparing(CommitRecord::authoredAt)).toList();
    List<InactivityPeriod> gaps = inactivityPeriods(chronological, window);

    return new CommitStatistics(
        total,
        merges,
        bots,
        (int) commits.stream().map(CommitRecord::authorKey).distinct().count(),
        chronological.isEmpty() ? null : chronological.getFirst().authoredAt(),
        chronological.isEmpty() ? null : chronological.getLast().authoredAt(),
        Numbers.round(total / (window.days() / 7.0), 2),
        Numbers.round(total / (window.days() / DAYS_PER_MONTH), 2),
        activeWeeks,
        weekly.size(),
        weekly,
        monthly(commits, window),
        dayTotals(heatmap),
        hourTotals(heatmap),
        toLists(heatmap),
        topAuthors(commits),
        limitPeriods(gaps),
        gaps.stream().max(Comparator.comparingDouble(InactivityPeriod::days)).orElse(null),
        recentCommits(chronological));
  }

  /** Zero-filled weekly buckets covering the whole window, so charts show quiet weeks too. */
  private static List<WeekCount> weekly(List<CommitRecord> commits, AnalysisWindow window) {
    Map<LocalDate, Integer> counts = new HashMap<>();
    for (CommitRecord commit : commits) {
      counts.merge(weekStart(commit.authoredAt()), 1, Integer::sum);
    }
    List<WeekCount> result = new ArrayList<>();
    LocalDate last = weekStart(window.until().minusNanos(1));
    for (LocalDate week = weekStart(window.since());
        !week.isAfter(last);
        week = week.plusWeeks(1)) {
      result.add(new WeekCount(week, counts.getOrDefault(week, 0)));
    }
    return result;
  }

  private static List<MonthCount> monthly(List<CommitRecord> commits, AnalysisWindow window) {
    Map<YearMonth, Integer> counts = new HashMap<>();
    for (CommitRecord commit : commits) {
      counts.merge(YearMonth.from(commit.authoredAt().atZone(ZoneOffset.UTC)), 1, Integer::sum);
    }
    List<MonthCount> result = new ArrayList<>();
    YearMonth last = YearMonth.from(window.until().minusNanos(1).atZone(ZoneOffset.UTC));
    for (YearMonth month = YearMonth.from(window.since().atZone(ZoneOffset.UTC));
        !month.isAfter(last);
        month = month.plusMonths(1)) {
      result.add(new MonthCount(month, counts.getOrDefault(month, 0)));
    }
    return result;
  }

  static LocalDate weekStart(Instant instant) {
    return instant
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  private List<AuthorActivity> topAuthors(List<CommitRecord> commits) {
    // LinkedHashMap keeps the first-seen record per author for name/login/bot details.
    Map<String, CommitRecord> firstByAuthor = new LinkedHashMap<>();
    Map<String, Integer> counts = new HashMap<>();
    for (CommitRecord commit : commits) {
      firstByAuthor.putIfAbsent(commit.authorKey(), commit);
      counts.merge(commit.authorKey(), 1, Integer::sum);
    }
    int total = commits.size();
    return counts.entrySet().stream()
        .sorted(
            Map.Entry.<String, Integer>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .limit(topAuthorLimit)
        .map(
            entry -> {
              CommitRecord sample = firstByAuthor.get(entry.getKey());
              return new AuthorActivity(
                  entry.getKey(),
                  sample.authorName(),
                  sample.authorLogin(),
                  sample.bot(),
                  entry.getValue(),
                  Numbers.percent(entry.getValue(), total));
            })
        .toList();
  }

  /**
   * Gaps between consecutive commits longer than the threshold, plus a trailing "ongoing" gap if
   * the last commit is older than the threshold relative to the end of the window. The gap before
   * the first commit is deliberately ignored: the repository may simply not have existed yet.
   */
  private List<InactivityPeriod> inactivityPeriods(
      List<CommitRecord> chronological, AnalysisWindow window) {
    List<InactivityPeriod> gaps = new ArrayList<>();
    for (int i = 1; i < chronological.size(); i++) {
      addIfLong(
          gaps, chronological.get(i - 1).authoredAt(), chronological.get(i).authoredAt(), false);
    }
    if (!chronological.isEmpty()) {
      addIfLong(gaps, chronological.getLast().authoredAt(), window.until(), true);
    }
    return gaps;
  }

  private void addIfLong(List<InactivityPeriod> gaps, Instant from, Instant to, boolean ongoing) {
    Duration gap = Duration.between(from, to);
    if (gap.compareTo(inactivityThreshold) > 0) {
      gaps.add(
          new InactivityPeriod(from, to, Numbers.round(gap.toSeconds() / 86_400.0, 1), ongoing));
    }
  }

  /** Keeps the longest periods (up to the limit) and returns them in chronological order. */
  private List<InactivityPeriod> limitPeriods(List<InactivityPeriod> gaps) {
    return gaps.stream()
        .sorted(Comparator.comparingDouble(InactivityPeriod::days).reversed())
        .limit(inactivityPeriodLimit)
        .sorted(Comparator.comparing(InactivityPeriod::from))
        .toList();
  }

  private List<RecentCommit> recentCommits(List<CommitRecord> chronological) {
    List<RecentCommit> recent = new ArrayList<>();
    for (int i = chronological.size() - 1; i >= 0 && recent.size() < recentCommitLimit; i--) {
      CommitRecord c = chronological.get(i);
      recent.add(
          new RecentCommit(
              c.sha(),
              c.sha().substring(0, Math.min(7, c.sha().length())),
              c.headline(),
              c.authorName(),
              c.authorLogin(),
              c.authoredAt(),
              c.merge(),
              c.htmlUrl()));
    }
    return recent;
  }

  private static List<Integer> dayTotals(int[][] heatmap) {
    List<Integer> totals = new ArrayList<>(7);
    for (int[] day : heatmap) {
      int sum = 0;
      for (int count : day) {
        sum += count;
      }
      totals.add(sum);
    }
    return totals;
  }

  private static List<Integer> hourTotals(int[][] heatmap) {
    List<Integer> totals = new ArrayList<>(24);
    for (int hour = 0; hour < 24; hour++) {
      int sum = 0;
      for (int[] day : heatmap) {
        sum += day[hour];
      }
      totals.add(sum);
    }
    return totals;
  }

  private static List<List<Integer>> toLists(int[][] heatmap) {
    List<List<Integer>> rows = new ArrayList<>(heatmap.length);
    for (int[] day : heatmap) {
      List<Integer> row = new ArrayList<>(day.length);
      for (int count : day) {
        row.add(count);
      }
      rows.add(List.copyOf(row));
    }
    return List.copyOf(rows);
  }
}
