package io.github.abhiramchendika.gitpulse.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.abhiramchendika.gitpulse.analysis.ContributorStatistics.Contributor;
import io.github.abhiramchendika.gitpulse.analysis.ContributorStatistics.LineStatsStatus;
import io.github.abhiramchendika.gitpulse.analysis.model.ContributorLineStats;
import io.github.abhiramchendika.gitpulse.analysis.model.ContributorRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContributorAnalyzerTest {

  private final ContributorAnalyzer analyzer = new ContributorAnalyzer(2);

  private static ContributorRecord contributor(String login, int commits) {
    return new ContributorRecord(login, "avatar", "html", login.endsWith("[bot]"), commits);
  }

  @Test
  void sortsByCommitsAndCalculatesShares() {
    ContributorStatistics stats =
        analyzer.analyze(
            List.of(contributor("bob", 30), contributor("alice", 50), contributor("carol", 20)),
            List.of());

    assertThat(stats.contributorCount()).isEqualTo(3);
    assertThat(stats.totalCommits()).isEqualTo(100);
    assertThat(stats.topContributorSharePercent()).isEqualTo(50.0);
    // Listing is limited to 2, but totals cover everyone.
    assertThat(stats.contributors())
        .extracting(Contributor::login, Contributor::sharePercent)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("alice", 50.0),
            org.assertj.core.groups.Tuple.tuple("bob", 30.0));
  }

  @Test
  void contributorsForHalf_usesPrefixSumOverSortedCounts() {
    assertThat(
            ContributorAnalyzer.contributorsForHalf(
                List.of(contributor("a", 50), contributor("b", 30), contributor("c", 20)), 100))
        .isEqualTo(1);
    assertThat(
            ContributorAnalyzer.contributorsForHalf(
                List.of(contributor("a", 40), contributor("b", 30), contributor("c", 30)), 100))
        .isEqualTo(2);
    assertThat(ContributorAnalyzer.contributorsForHalf(List.of(), 0)).isZero();
  }

  @Test
  void emptyRepository_hasZeroesNotErrors() {
    ContributorStatistics stats = analyzer.analyze(List.of(), List.of());

    assertThat(stats.contributorCount()).isZero();
    assertThat(stats.topContributorSharePercent()).isZero();
    assertThat(stats.contributorsForHalfOfCommits()).isZero();
    assertThat(stats.contributors()).isEmpty();
  }

  @Test
  void countsBots() {
    ContributorStatistics stats =
        analyzer.analyze(
            List.of(contributor("alice", 5), contributor("dependabot[bot]", 9)), List.of());

    assertThat(stats.botCount()).isEqualTo(1);
  }

  @Test
  void attachesLineStatsWhenAvailable() {
    ContributorStatistics stats =
        analyzer.analyze(
            List.of(contributor("alice", 5), contributor("bob", 3)),
            List.of(new ContributorLineStats("alice", 120, 30, 5)));

    assertThat(stats.lineStatsStatus()).isEqualTo(LineStatsStatus.AVAILABLE);
    assertThat(stats.contributors().get(0).additions()).isEqualTo(120);
    assertThat(stats.contributors().get(0).deletions()).isEqualTo(30);
    // bob is not in GitHub's top-100 stats: null means "not provided", not zero.
    assertThat(stats.contributors().get(1).additions()).isNull();
  }

  @Test
  void lineStatsPending_whenGitHubStillComputing() {
    ContributorStatistics stats = analyzer.analyze(List.of(contributor("alice", 5)), null);

    assertThat(stats.lineStatsStatus()).isEqualTo(LineStatsStatus.PENDING);
    assertThat(stats.contributors().get(0).additions()).isNull();
  }

  @Test
  void lineStatsUnavailable_whenGitHubReportsZeroLinesForNonZeroCommits() {
    // GitHub does this for repositories with 10,000+ commits.
    ContributorStatistics stats =
        analyzer.analyze(
            List.of(contributor("alice", 5)), List.of(new ContributorLineStats("alice", 0, 0, 5)));

    assertThat(stats.lineStatsStatus()).isEqualTo(LineStatsStatus.UNAVAILABLE);
    assertThat(stats.contributors().get(0).additions()).isNull();
  }

  @Test
  void lineStatsUnavailable_whenEmpty() {
    assertThat(ContributorAnalyzer.lineStatsStatus(List.of()))
        .isEqualTo(LineStatsStatus.UNAVAILABLE);
  }
}
