package io.github.abhiramchendika.gitpulse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abhiramchendika.gitpulse.config.AnalysisProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AnalysisWindowsTest {

  private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
  private static final LocalDate TODAY = LocalDate.parse("2026-09-25");

  private final AnalysisWindows windows =
      new AnalysisWindows(
          new AnalysisProperties(10, 5, 365, 3650, Duration.ofDays(14), 10, 10, 10, 100, 5, 5),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void defaultRange_isLast365DaysIncludingToday() {
    var range = windows.resolveRange(null, null, TODAY);

    assertThat(range.until()).isEqualTo(TODAY);
    assertThat(range.since()).isEqualTo(TODAY.minusDays(364));
  }

  @Test
  void rejectsFutureUntil_reversedRange_andTooLongRange() {
    assertThatThrownBy(() -> windows.resolveRange(null, TODAY.plusDays(1), TODAY))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("future");
    assertThatThrownBy(() -> windows.resolveRange(TODAY, TODAY.minusDays(1), TODAY))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("before");
    assertThatThrownBy(() -> windows.resolveRange(TODAY.minusDays(3650), TODAY, TODAY))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("3650");
  }

  @Test
  void singleDayRange_isAllowed() {
    var range = windows.resolveRange(TODAY, TODAY, TODAY);

    assertThat(range.since()).isEqualTo(range.until());
  }

  @Test
  void resolve_isDayAligned_andCapsAnalysisAtNow() {
    var resolved = windows.resolve(LocalDate.parse("2026-09-01"), null);

    assertThat(resolved.requestedSince()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    assertThat(resolved.requestedUntil()).isEqualTo(Instant.parse("2026-09-26T00:00:00Z"));
    assertThat(resolved.analysisUntil()).isEqualTo(NOW);
  }

  @Test
  void effectiveSince_onlyMovesForTruncatedSamples() {
    Instant since = Instant.parse("2026-01-01T00:00:00Z");
    Instant oldest = Instant.parse("2026-06-01T00:00:00Z");

    assertThat(AnalysisWindows.effectiveSince(false, Stream.of(oldest), since, NOW))
        .isEqualTo(since);
    assertThat(
            AnalysisWindows.effectiveSince(
                true, Stream.of(NOW.minusSeconds(1), oldest), since, NOW))
        .isEqualTo(oldest);
    // Never before the requested start, never at or after the end.
    assertThat(
            AnalysisWindows.effectiveSince(
                true, Stream.of(Instant.parse("2025-01-01T00:00:00Z")), since, NOW))
        .isEqualTo(since);
    assertThat(AnalysisWindows.effectiveSince(true, Stream.of(), since, NOW)).isEqualTo(since);
  }
}
