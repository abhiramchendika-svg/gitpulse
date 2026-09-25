package io.github.abhiramchendika.gitpulse.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.github.abhiramchendika.gitpulse.analysis.ProfileStatistics.LanguageCount;
import io.github.abhiramchendika.gitpulse.analysis.ProfileStatistics.RepositoryItem;
import io.github.abhiramchendika.gitpulse.analysis.ProfileStatistics.TypeCount;
import io.github.abhiramchendika.gitpulse.analysis.ProfileStatistics.YearCount;
import io.github.abhiramchendika.gitpulse.analysis.model.OwnedRepository;
import io.github.abhiramchendika.gitpulse.analysis.model.PublicEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileAnalyzerTest {

  private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

  private final ProfileAnalyzer analyzer = new ProfileAnalyzer(3);

  private static OwnedRepository repo(
      String name,
      boolean fork,
      String language,
      int stars,
      int forks,
      String created,
      int pushedDaysAgo) {
    return new OwnedRepository(
        "mona/" + name,
        "https://github.com/mona/" + name,
        null,
        fork,
        false,
        language,
        stars,
        forks,
        Instant.parse(created),
        NOW.minus(Duration.ofDays(pushedDaysAgo)));
  }

  @Test
  void starsAndForksReceivedCountOnlyOwnRepositories() {
    ProfileStatistics s =
        analyzer.analyze(
            List.of(
                repo("app", false, "Java", 50, 5, "2020-01-01T00:00:00Z", 1),
                repo("lib", false, "Java", 10, 1, "2021-01-01T00:00:00Z", 40),
                // A fork's stars belong to the fork, not to the account's own work.
                repo("react", true, "JavaScript", 900, 300, "2022-01-01T00:00:00Z", 400)),
            List.of(),
            NOW);

    assertThat(s.repositoriesAnalyzed()).isEqualTo(3);
    assertThat(s.originalRepositories()).isEqualTo(2);
    assertThat(s.forkedRepositories()).isEqualTo(1);
    assertThat(s.starsReceived()).isEqualTo(60);
    assertThat(s.forksReceived()).isEqualTo(6);
  }

  @Test
  void languagesCountOwnRepositoriesByPrimaryLanguage() {
    ProfileStatistics s =
        analyzer.analyze(
            List.of(
                repo("a", false, "Java", 0, 0, "2020-01-01T00:00:00Z", 1),
                repo("b", false, "Java", 0, 0, "2020-01-01T00:00:00Z", 1),
                repo("c", false, "Go", 0, 0, "2020-01-01T00:00:00Z", 1),
                repo("d", false, null, 0, 0, "2020-01-01T00:00:00Z", 1),
                repo("e", true, "Rust", 0, 0, "2020-01-01T00:00:00Z", 1)),
            List.of(),
            NOW);

    assertThat(s.languages())
        .extracting(LanguageCount::name, LanguageCount::repositories, LanguageCount::percent)
        .containsExactly(tuple("Java", 2, 66.7), tuple("Go", 1, 33.3));
    assertThat(s.repositoriesWithoutLanguage()).isEqualTo(1);
  }

  @Test
  void pushRecencyCountsAllRepositories() {
    ProfileStatistics s =
        analyzer.analyze(
            List.of(
                repo("a", false, "Java", 0, 0, "2020-01-01T00:00:00Z", 5),
                repo("b", false, "Java", 0, 0, "2020-01-01T00:00:00Z", 60),
                repo("c", true, "Java", 0, 0, "2020-01-01T00:00:00Z", 200),
                repo("d", false, "Java", 0, 0, "2020-01-01T00:00:00Z", 800)),
            List.of(),
            NOW);

    assertThat(s.pushedLast30Days()).isEqualTo(1);
    assertThat(s.pushedLast90Days()).isEqualTo(2);
    assertThat(s.pushedLastYear()).isEqualTo(3);
  }

  @Test
  void mostStarredExcludesForks_recentlyPushedIncludesThem() {
    ProfileStatistics s =
        analyzer.analyze(
            List.of(
                repo("small", false, "Java", 1, 0, "2020-01-01T00:00:00Z", 30),
                repo("big", false, "Java", 99, 0, "2020-01-01T00:00:00Z", 20),
                repo("fork", true, "Java", 500, 0, "2020-01-01T00:00:00Z", 1)),
            List.of(),
            NOW);

    assertThat(s.mostStarred())
        .extracting(RepositoryItem::fullName)
        .containsExactly("mona/big", "mona/small");
    assertThat(s.recentlyPushed())
        .extracting(RepositoryItem::fullName)
        .containsExactly("mona/fork", "mona/big", "mona/small");
  }

  @Test
  void createdPerYearIsZeroFilledUpToThisYear() {
    ProfileStatistics s =
        analyzer.analyze(
            List.of(
                repo("a", false, "Java", 0, 0, "2023-03-01T00:00:00Z", 1),
                repo("b", false, "Java", 0, 0, "2023-09-01T00:00:00Z", 1),
                repo("c", false, "Java", 0, 0, "2025-01-01T00:00:00Z", 1)),
            List.of(),
            NOW);

    assertThat(s.createdPerYear())
        .containsExactly(
            new YearCount(2023, 2),
            new YearCount(2024, 0),
            new YearCount(2025, 1),
            new YearCount(2026, 0));
  }

  @Test
  void eventsAreSummarisedByTypeDayAndRepository() {
    List<PublicEvent> events =
        List.of(
            new PublicEvent("PushEvent", "mona/app", Instant.parse("2026-09-20T09:00:00Z")),
            new PublicEvent("PushEvent", "mona/app", Instant.parse("2026-09-20T18:00:00Z")),
            new PublicEvent("PullRequestEvent", "octo/lib", Instant.parse("2026-09-18T10:00:00Z")),
            new PublicEvent("SponsorshipEvent", "mona/app", Instant.parse("2026-09-01T10:00:00Z")));

    ProfileStatistics.Events e = analyzer.analyze(List.of(), events, NOW).events();

    assertThat(e.count()).isEqualTo(4);
    assertThat(e.activeDays()).isEqualTo(3);
    assertThat(e.repositoriesTouched()).isEqualTo(2);
    assertThat(e.from()).isEqualTo(Instant.parse("2026-09-01T10:00:00Z"));
    assertThat(e.byType())
        .extracting(TypeCount::label, TypeCount::count)
        .containsExactly(
            tuple("Pushes", 2), tuple("Pull request activity", 1), tuple("Sponsorship", 1));
    assertThat(e.topRepositories().getFirst().repository()).isEqualTo("mona/app");
  }

  @Test
  void noEvents_isEmptySummary_eventsNotAnalysed_isNull() {
    assertThat(analyzer.analyze(List.of(), List.of(), NOW).events().count()).isZero();
    assertThat(analyzer.analyze(List.of(), null, NOW).events()).isNull();
  }

  @Test
  void noRepositories() {
    ProfileStatistics s = analyzer.analyze(List.of(), List.of(), NOW);

    assertThat(s.starsReceived()).isZero();
    assertThat(s.languages()).isEmpty();
    assertThat(s.createdPerYear()).isEmpty();
  }
}
