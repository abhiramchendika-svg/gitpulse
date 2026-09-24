package io.github.abhiramchendika.gitpulse.analysis;

import io.github.abhiramchendika.gitpulse.analysis.model.AnalysisWindow;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared time grouping. All buckets are UTC; weeks start on Monday (ISO). */
public final class TimeBuckets {

  private TimeBuckets() {}

  public static LocalDate weekStart(Instant instant) {
    return instant
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  /**
   * Counts events per week, returning <em>every</em> week of the window in order, including weeks
   * with zero events, so charts show quiet weeks too. Events outside the window are ignored.
   */
  public static Map<LocalDate, Integer> weekly(Collection<Instant> events, AnalysisWindow window) {
    Map<LocalDate, Integer> counts = new LinkedHashMap<>();
    LocalDate last = weekStart(window.until().minusNanos(1));
    for (LocalDate week = weekStart(window.since());
        !week.isAfter(last);
        week = week.plusWeeks(1)) {
      counts.put(week, 0);
    }
    for (Instant event : events) {
      if (window.contains(event)) {
        counts.merge(weekStart(event), 1, Integer::sum);
      }
    }
    return counts;
  }
}
