package io.github.abhiramchendika.gitpulse.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.github.abhiramchendika.gitpulse.analysis.LanguageStatistics.Language;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LanguageAnalyzerTest {

  private final LanguageAnalyzer analyzer = new LanguageAnalyzer();

  @Test
  void sortsByBytesAndCalculatesPercentages() {
    LanguageStatistics stats =
        analyzer.analyze(Map.of("Java", 7000L, "TypeScript", 2000L, "CSS", 1000L));

    assertThat(stats.totalBytes()).isEqualTo(10_000);
    assertThat(stats.languages())
        .extracting(Language::name, Language::percent)
        .containsExactly(tuple("Java", 70.0), tuple("TypeScript", 20.0), tuple("CSS", 10.0));
  }

  @Test
  void roundsToOneDecimal() {
    LanguageStatistics stats = analyzer.analyze(Map.of("A", 1L, "B", 2L));

    assertThat(stats.languages()).extracting(Language::percent).containsExactly(66.7, 33.3);
  }

  @Test
  void equalBytes_sortedByName() {
    LanguageStatistics stats = analyzer.analyze(Map.of("Zig", 5L, "Ada", 5L));

    assertThat(stats.languages()).extracting(Language::name).containsExactly("Ada", "Zig");
  }

  @Test
  void noLanguages_isEmptyNotError() {
    LanguageStatistics stats = analyzer.analyze(Map.of());

    assertThat(stats.totalBytes()).isZero();
    assertThat(stats.languages()).isEmpty();
  }
}
