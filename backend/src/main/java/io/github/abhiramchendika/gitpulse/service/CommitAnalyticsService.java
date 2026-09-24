package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.analysis.CommitAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.CommitStatistics;
import io.github.abhiramchendika.gitpulse.analysis.model.AnalysisWindow;
import io.github.abhiramchendika.gitpulse.analysis.model.CommitRecord;
import io.github.abhiramchendika.gitpulse.api.dto.AnalysisMeta;
import io.github.abhiramchendika.gitpulse.api.dto.CommitAnalyticsResponse;
import io.github.abhiramchendika.gitpulse.config.AnalysisProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Commit activity for a date window. */
@Service
public class CommitAnalyticsService {

  private final RepositoryDataService data;
  private final CommitAnalyzer analyzer;
  private final AnalysisProperties properties;
  private final Clock clock;

  public CommitAnalyticsService(
      RepositoryDataService data,
      CommitAnalyzer analyzer,
      AnalysisProperties properties,
      Clock clock) {
    this.data = data;
    this.analyzer = analyzer;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * @param since first day to include (UTC), or null for the default window
   * @param until last day to include (UTC), or null for today
   */
  public CommitAnalyticsResponse analyze(
      RepositoryRef ref, LocalDate since, LocalDate until, boolean excludeBots) {
    Instant now = clock.instant();
    DateRange range = resolveRange(since, until, LocalDate.ofInstant(now, ZoneOffset.UTC));

    // Day-aligned instants keep the cache key stable for a whole day.
    Instant requestedSince = range.since().atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant requestedUntil = range.until().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

    CommitSample sample = data.commits(ref, requestedSince, requestedUntil);
    long totalAllTime = sample.emptyRepository() ? 0 : data.commitCount(ref);

    // Never analyse the future: "no commits since X" should be measured up to now.
    Instant analysisUntil = requestedUntil.isAfter(now) ? now : requestedUntil;
    Instant analysisSince = effectiveSince(sample, requestedSince, analysisUntil);

    List<CommitRecord> commits =
        excludeBots ? sample.commits().stream().filter(c -> !c.bot()).toList() : sample.commits();
    CommitStatistics statistics =
        analyzer.analyze(commits, new AnalysisWindow(analysisSince, analysisUntil));

    AnalysisMeta meta =
        new AnalysisMeta(
            now,
            analysisSince,
            analysisUntil,
            requestedSince,
            sample.commits().size(),
            sample.truncated(),
            excludeBots,
            "UTC");
    return new CommitAnalyticsResponse(
        ref.fullName(), meta, sample.emptyRepository(), totalAllTime, statistics);
  }

  /**
   * If the page cap cut the sample short, older commits in the window were never fetched. Counting
   * those weeks as "zero commits" would be wrong, so the window is shortened to start at the oldest
   * commit we actually have.
   */
  static Instant effectiveSince(CommitSample sample, Instant requestedSince, Instant until) {
    if (!sample.truncated() || sample.commits().isEmpty()) {
      return requestedSince;
    }
    // GitHub pages by *commit* date, so the oldest commit date marks where coverage ends. Any
    // commit authored after that point was also committed after it, so it is in the sample.
    // (Using the author date here is wrong: patches are often authored long before they land.)
    Instant oldest =
        sample.commits().stream()
            .map(CommitRecord::committedAt)
            .min(Comparator.naturalOrder())
            .orElse(requestedSince);
    if (oldest.isBefore(requestedSince) || !oldest.isBefore(until)) {
      return requestedSince;
    }
    return oldest;
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

  /** Inclusive range of UTC days. */
  record DateRange(LocalDate since, LocalDate until) {}
}
