package io.github.abhiramchendika.gitpulse.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExplanationVerifierTest {

  private final ExplanationVerifier verifier = new ExplanationVerifier();
  private final FactSheet sheet = ExplanationFixtures.sheet();

  private ExplanationVerifier.Result verify(String text, String... facts) {
    return verifier.verify(
        new ExplanationDraft(List.of(new ExplanationDraft.Sentence(text, List.of(facts)))), sheet);
  }

  private boolean passes(String text, String... facts) {
    return verify(text, facts).sentences().size() == 1;
  }

  @Test
  void acceptsSentencesWhoseNumbersMatchTheirFacts() {
    assertThat(
            passes(
                "In the last 365 days there were 240 commits, in 41 of 52 weeks.",
                "window.days",
                "commits.total",
                "commits.activeWeeks",
                "commits.totalWeeks"))
        .isTrue();
    assertThat(passes("The repository has 1,734 stars.", "repository.stars")).isTrue();
    assertThat(passes("It was created in 2011.", "repository.createdYear")).isTrue();
    assertThat(passes("Most of the code is Java.", "languages.1.name")).isTrue();
  }

  @Test
  void keepsTheCitedFactsWithTheSentence() {
    var result = verify("The repository has 1,734 stars.", "repository.stars");
    assertThat(result.sentences().get(0).facts())
        .extracting(Fact::id)
        .containsExactly("repository.stars");
  }

  @Test
  void rejectsNumbersThatAreNotInTheCitedFacts() {
    // 1,734 is a real fact, but not one this sentence cites.
    assertThat(passes("There were 1,734 commits.", "commits.total")).isFalse();
    // A calculated number (240 - 12) is not allowed.
    assertThat(passes("228 commits were not merges.", "commits.total", "commits.mergeCommits"))
        .isFalse();
    // Approximation.
    assertThat(passes("About 1,700 people starred it.", "repository.stars")).isFalse();
    // Ordinals and dates are numbers too.
    assertThat(passes("It is the 3rd most popular.", "repository.stars")).isFalse();
    assertThat(passes("Since 26 September 2025 there were 240 commits.", "commits.total"))
        .isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    "12.47, 12.47, true", // exact
    "12.47, 12.5, true", // fewer decimals
    "12.47, 12, true",
    "12.47, 12.4, false", // wrongly rounded
    "1734, 1734, true",
    "1734, 1700, false", // rounding to hundreds is not allowed
    "4.0, 4, true",
    "96.0, 96, true",
    "30, 30.0, true",
    "30, 30.5, false"
  })
  void numberMatching(String value, String written, boolean expected) {
    assertThat(ExplanationVerifier.matches(new BigDecimal(value), new BigDecimal(written)))
        .isEqualTo(expected);
  }

  @Test
  void acceptsADecimalFactRoundedToFewerPlaces() {
    // The median is 12.47 hours; 12.5 is the same value at one decimal place.
    assertThat(
            passes(
                "Half of the merged pull requests were merged within 12.5 hours.",
                "pullRequests.timeToMerge.medianHours"))
        .isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "This is a healthy project with 240 commits.",
        "The 17 authors were very productive.",
        "Maintainers should merge the 3 open pull requests.",
        "240 commits is an impressive number.",
        "With 240 commits it is clearly not abandoned."
      })
  void rejectsEvaluativeLanguage(String text) {
    assertThat(passes(text, "commits.total", "commits.distinctAuthors", "pullRequests.stillOpen"))
        .isFalse();
  }

  @Test
  void rejectsMissingOrUnknownFactIds() {
    assertThat(passes("The project is written in Java.")).isFalse();
    assertThat(passes("There were 240 commits.", "commits.total", "commits.invented")).isFalse();
  }

  @Test
  void rejectsBlankAndOverlongSentences() {
    assertThat(passes("   ", "commits.total")).isFalse();
    assertThat(passes("Java ".repeat(80), "languages.1.name")).isFalse();
  }

  @Test
  void dropsFailingSentencesAndCountsThem() {
    var result =
        verifier.verify(
            new ExplanationDraft(
                List.of(
                    new ExplanationDraft.Sentence(
                        "There were 240 commits.", List.of("commits.total")),
                    new ExplanationDraft.Sentence(
                        "There were 999 commits.", List.of("commits.total")),
                    new ExplanationDraft.Sentence(
                        "It has 1,734 stars.", List.of("repository.stars")))),
            sheet);

    assertThat(result.sentences())
        .extracting(ExplanationVerifier.VerifiedSentence::text)
        .containsExactly("There were 240 commits.", "It has 1,734 stars.");
    assertThat(result.removed()).isEqualTo(1);
  }

  @Test
  void keepsAtMostEightSentences() {
    var sentence =
        new ExplanationDraft.Sentence("There were 240 commits.", List.of("commits.total"));
    var result =
        verifier.verify(
            new ExplanationDraft(
                List.of(
                    sentence, sentence, sentence, sentence, sentence, sentence, sentence, sentence,
                    sentence, sentence)),
            sheet);
    assertThat(result.sentences()).hasSize(ExplanationVerifier.MAX_SENTENCES);
    assertThat(result.removed()).isEqualTo(2);
  }
}
