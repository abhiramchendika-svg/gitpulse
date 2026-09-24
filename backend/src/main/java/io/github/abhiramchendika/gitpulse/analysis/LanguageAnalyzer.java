package io.github.abhiramchendika.gitpulse.analysis;

import io.github.abhiramchendika.gitpulse.analysis.LanguageStatistics.Language;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Converts GitHub's bytes-per-language map into a sorted percentage breakdown. Pure; no I/O. */
public class LanguageAnalyzer {

  public LanguageStatistics analyze(Map<String, Long> bytesByLanguage) {
    long total = bytesByLanguage.values().stream().mapToLong(Long::longValue).sum();
    List<Language> languages =
        bytesByLanguage.entrySet().stream()
            .map(e -> new Language(e.getKey(), e.getValue(), Numbers.percent(e.getValue(), total)))
            .sorted(
                Comparator.comparingLong(Language::bytes).reversed().thenComparing(Language::name))
            .toList();
    return new LanguageStatistics(total, languages);
  }
}
