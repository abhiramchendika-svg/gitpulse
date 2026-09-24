package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.config.AnalysisProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Turns the client's optional {@code since}/{@code until} days into the exact instants every
 * windowed analysis uses, so commits, pull requests, issues and activity all agree on what "the
 * last year" means.
 */
@Component
public class AnalysisWindows {

  private final AnalysisProperties properties;
  private final Clock clock;

  public AnalysisWindows(AnalysisProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * @param requestedSince start of the first requested day (inclusive)
   * @param requestedUntil start of the day after the last requested day (exclusive). Day-aligned so
   *     cache keys stay stable for a whole day.
   * @param analysisUntil {@code requestedUntil} capped at now: statistics never cover the future
   */
  public record Resolved(
      Instant now, Instant requestedSince, Instant requestedUntil, Instant analysisUntil) {}

  /**
   * @param since first day to include (UTC), or null for the default window
   * @param until last day to include (UTC), or null for today
   */
  public Resolved resolve(LocalDate since, LocalDate until) {
    Instant now = clock.instant();
    DateRange range = resolveRange(since, until, LocalDate.ofInstant(now, ZoneOffset.UTC));
    Instant requestedSince = range.since().atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant requestedUntil = range.until().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant analysisUntil = requestedUntil.isAfter(now) ? now : requestedUntil;
    return new Resolved(now, requestedSince, requestedUntil, analysisUntil);
  }

  DateRange resolveRange(LocalDate since, LocalDate until, LocalDate today) {
    LocalDate end = until != null ? until : today;
    if (end.isAfter(today)) {
      throw new InvalidRequestException("'until' cannot be in the future.");
    }
    LocalDate start = since != null ? since : end.minusDays(properties.defaultWindowDays() - 1L);
    if (start.isAfter(end)) {
      throw new InvalidRequestException("'since' must be on or before 'until'.");
    }
    long days = ChronoUnit.DAYS.between(start, end) + 1;
    if (days > properties.maxWindowDays()) {
      throw new InvalidRequestException(
          "The date range may span at most " + properties.maxWindowDays() + " days.");
    }
    return new DateRange(start, end);
  }

  /**
   * If a page cap cut a sample short, older items in the window were never fetched. Reporting those
   * weeks as "zero" would be wrong, so the window starts at the oldest point the sample fully
   * covers instead.
   *
   * @param coverageDates for each fetched item, the date GitHub orders the list by (commit date for
   *     commits, creation date for pull requests and issues)
   */
  static Instant effectiveSince(
      boolean truncated, Stream<Instant> coverageDates, Instant requestedSince, Instant until) {
    if (!truncated) {
      return requestedSince;
    }
    Instant oldest = coverageDates.min(Comparator.naturalOrder()).orElse(requestedSince);
    if (oldest.isBefore(requestedSince) || !oldest.isBefore(until)) {
      return requestedSince;
    }
    return oldest;
  }

  /** Inclusive range of UTC days. */
  record DateRange(LocalDate since, LocalDate until) {}
}
