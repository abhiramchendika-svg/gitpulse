package io.github.abhiramchendika.gitpulse.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.abhiramchendika.gitpulse.analysis.model.AnalysisWindow;
import io.github.abhiramchendika.gitpulse.analysis.model.IssueRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class IssueAnalyzerTest {

  private final IssueAnalyzer analyzer = new IssueAnalyzer(10, 10);

  private static final AnalysisWindow WINDOW =
      new AnalysisWindow(
          Instant.parse("2026-01-05T00:00:00Z"), Instant.parse("2026-01-19T00:00:00Z"));

  private static int number = 0;

  private static IssueRecord issue(String created, String closed, String reason) {
    return new IssueRecord(
        ++number,
        "Issue " + number,
        "user" + number,
        false,
        Instant.parse(created),
        closed == null ? null : Instant.parse(closed),
        reason,
        0,
        "url");
  }

  @Test
  void countsCohortAndCloseReasons() {
    List<IssueRecord> issues =
        List.of(
            issue("2026-01-01T00:00:00Z", "2026-01-06T00:00:00Z", "completed"), // before window
            issue("2026-01-05T00:00:00Z", "2026-01-05T10:00:00Z", "completed"),
            issue("2026-01-06T00:00:00Z", "2026-01-07T00:00:00Z", "not_planned"),
            issue("2026-01-07T00:00:00Z", "2026-01-08T00:00:00Z", "duplicate"),
            issue("2026-01-08T00:00:00Z", "2026-01-09T00:00:00Z", null),
            issue("2026-01-12T00:00:00Z", null, null));

    IssueStatistics s = analyzer.analyze(issues, WINDOW);

    assertThat(s.opened()).isEqualTo(5);
    assertThat(s.closed()).isEqualTo(4);
    assertThat(s.stillOpen()).isEqualTo(1);
    assertThat(s.closedAsCompleted()).isEqualTo(1);
    assertThat(s.closedAsNotPlanned()).isEqualTo(1);
    assertThat(s.closedOther()).isEqualTo(2);
    // Close times: 10h, 24h, 24h, 24h -> median 24h.
    assertThat(s.timeToClose().medianHours()).isEqualTo(24.0);
  }

  @Test
  void weeklyOpenedAndClosed() {
    List<IssueRecord> issues =
        List.of(
            issue("2026-01-05T00:00:00Z", "2026-01-13T00:00:00Z", "completed"),
            issue("2026-01-13T00:00:00Z", null, null));

    IssueStatistics s = analyzer.analyze(issues, WINDOW);

    assertThat(s.weekly())
        .containsExactly(
            new IssueStatistics.Week(LocalDate.parse("2026-01-05"), 1, 0),
            new IssueStatistics.Week(LocalDate.parse("2026-01-12"), 1, 1));
  }

  @Test
  void noClosedIssues_meansNoCloseTime() {
    assertThat(
            analyzer
                .analyze(List.of(issue("2026-01-06T00:00:00Z", null, null)), WINDOW)
                .timeToClose())
        .isNull();
  }
}
