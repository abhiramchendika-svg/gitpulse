package io.github.abhiramchendika.gitpulse.explanation;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** The instructions and input sent to the model. Kept in one place so they are easy to review. */
public final class ExplanationPrompt {

  private ExplanationPrompt() {}

  /**
   * States the rules the verifier enforces, so the model rarely writes a sentence that gets
   * dropped. The verifier, not this text, is what guarantees them.
   */
  public static final String SYSTEM =
      """
      You explain a GitHub repository dashboard to a reader who is not a software developer.
      You receive facts that GitPulse calculated from public GitHub data. Each fact has an id, a
      label and a value.

      Write 3 to 6 short, plain-English sentences describing what these numbers show about the
      repository's activity in the selected period and overall.

      Rules:
      - Use only the facts provided. Add no outside knowledge about the repository or its people.
      - For every sentence, list in "facts" the ids of all facts it uses.
      - Write numbers with digits, copied from the fact values. You may add thousands separators
        and round to fewer decimal places, but do not calculate new numbers (no differences,
        ratios, totals or approximations such as "about 1,700").
      - Do not write dates or years unless the year is a fact value. Describe periods by their
        length using the period facts, for example "in the last 365 days".
      - Describe; do not judge. Do not assess quality, health, productivity or popularity, do not
        compare with other projects, and give no advice.
      - Do not mention or characterise individual people.
      - If window.analysedDays is present, say the commit statistics cover a shorter period.
      - If a fact is missing, do not guess it; leave it out.
      """;

  public static String userMessage(FactSheet sheet, JsonMapper json) {
    List<Map<String, Object>> facts =
        sheet.facts().stream()
            .map(f -> Map.<String, Object>of("id", f.id(), "label", f.label(), "value", f.value()))
            .toList();
    return "Facts about the repository, as JSON:\n" + json.writeValueAsString(facts);
  }
}
