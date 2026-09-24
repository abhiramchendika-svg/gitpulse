package io.github.abhiramchendika.gitpulse.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.github.abhiramchendika.gitpulse.analysis.model.AnalysisWindow;
import io.github.abhiramchendika.gitpulse.analysis.model.PullRequestRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PullRequestAnalyzerTest {

  private final PullRequestAnalyzer analyzer = new PullRequestAnalyzer(3, 2);

  // 2026-01-05 is a Monday; two full weeks.
  private static final AnalysisWindow WINDOW =
      new AnalysisWindow(
          Instant.parse("2026-01-05T00:00:00Z"), Instant.parse("2026-01-19T00:00:00Z"));

  private static int number = 0;

  private static PullRequestRecord pr(String author, String created, String closed, String merged) {
    return new PullRequestRecord(
        ++number,
        "PR " + number,
        author,
        author.endsWith("[bot]"),
        false,
        Instant.parse(created),
        closed == null ? null : Instant.parse(closed),
        merged == null ? null : Instant.parse(merged),
        "url");
  }

  @Test
  void countsTheCohortOpenedInTheWindow() {
    List<PullRequestRecord> prs =
        List.of(
            // Before the window: not part of the cohort even though merged inside it.
            pr("alice", "2026-01-01T00:00:00Z", "2026-01-06T00:00:00Z", "2026-01-06T00:00:00Z"),
            pr("alice", "2026-01-05T10:00:00Z", "2026-01-05T12:00:00Z", "2026-01-05T12:00:00Z"),
            pr("bob", "2026-01-06T00:00:00Z", "2026-01-07T00:00:00Z", null),
            pr("carol", "2026-01-13T00:00:00Z", null, null),
            pr(
                "dependabot[bot]",
                "2026-01-14T00:00:00Z",
                "2026-01-14T06:00:00Z",
                "2026-01-14T06:00:00Z"));

    PullRequestStatistics s = analyzer.analyze(prs, WINDOW);

    assertThat(s.opened()).isEqualTo(4);
    assertThat(s.merged()).isEqualTo(2);
    assertThat(s.closedWithoutMerge()).isEqualTo(1);
    assertThat(s.stillOpen()).isEqualTo(1);
    assertThat(s.openedByBots()).isEqualTo(1);
    // 2 merged of 3 closed.
    assertThat(s.mergedPercentOfClosed()).isEqualTo(66.7);
    // Merge times: 2h and 6h -> median 4h.
    assertThat(s.timeToMerge().count()).isEqualTo(2);
    assertThat(s.timeToMerge().medianHours()).isEqualTo(4.0);
  }

  @Test
  void weeklySeriesIsZeroFilledWithOpenedAndMerged() {
    List<PullRequestRecord> prs =
        List.of(
            pr("a", "2026-01-05T10:00:00Z", "2026-01-12T01:00:00Z", "2026-01-12T01:00:00Z"),
            pr("b", "2026-01-06T10:00:00Z", null, null));

    PullRequestStatistics s = analyzer.analyze(prs, WINDOW);

    assertThat(s.weekly())
        .containsExactly(
            new PullRequestStatistics.Week(LocalDate.parse("2026-01-05"), 2, 0),
            new PullRequestStatistics.Week(LocalDate.parse("2026-01-12"), 0, 1));
  }

  @Test
  void noClosedPullRequests_meansNoRateAndNoMergeTime() {
    PullRequestStatistics s =
        analyzer.analyze(List.of(pr("a", "2026-01-06T00:00:00Z", null, null)), WINDOW);

    assertThat(s.mergedPercentOfClosed()).isNull();
    assertThat(s.timeToMerge()).isNull();
  }

  @Test
  void topAuthorsAndRecentWithStatus() {
    List<PullRequestRecord> prs =
        List.of(
            pr("alice", "2026-01-05T00:00:00Z", null, null),
            pr("alice", "2026-01-06T00:00:00Z", "2026-01-06T01:00:00Z", "2026-01-06T01:00:00Z"),
            pr("bob", "2026-01-07T00:00:00Z", "2026-01-08T00:00:00Z", null));

    PullRequestStatistics s = analyzer.analyze(prs, WINDOW);

    assertThat(s.topAuthors())
        .extracting(Rankings.ActorCount::login, Rankings.ActorCount::count)
        .containsExactly(tuple("alice", 2), tuple("bob", 1));
    // Newest first, limited to 2.
    assertThat(s.recent())
        .extracting(PullRequestStatistics.Recent::authorLogin, PullRequestStatistics.Recent::status)
        .containsExactly(tuple("bob", "closed"), tuple("alice", "merged"));
  }

  @Test
  void emptyInput() {
    PullRequestStatistics s = analyzer.analyze(List.of(), WINDOW);

    assertThat(s.opened()).isZero();
    assertThat(s.weekly()).hasSize(2);
    assertThat(s.topAuthors()).isEmpty();
  }
}
