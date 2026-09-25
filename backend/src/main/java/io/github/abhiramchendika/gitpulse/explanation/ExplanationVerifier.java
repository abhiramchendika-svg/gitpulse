package io.github.abhiramchendika.gitpulse.explanation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks a model-written explanation against the facts it claims to use. A language model can state
 * a wrong number fluently, so nothing it writes is shown until it passes these checks:
 *
 * <ol>
 *   <li>the sentence cites at least one fact, and every cited fact id exists;
 *   <li>every number in the sentence equals the value of one of its cited facts (thousands
 *       separators allowed; rounding only to fewer decimal places: 12.47 may be written as 12.5 or
 *       12, but 1,734 may not become "about 1,700");
 *   <li>no evaluative wording ("healthy", "productive", "should" …): GitPulse describes activity,
 *       it does not judge projects or people;
 *   <li>a sane length.
 * </ol>
 *
 * <p>Failing sentences are dropped, not repaired. Numbers written as words ("two") are not checked;
 * the prompt asks for digits.
 */
public final class ExplanationVerifier {

  /** Fewer surviving sentences than this and the whole explanation is rejected. */
  public static final int MIN_SENTENCES = 2;

  static final int MAX_SENTENCES = 8;
  static final int MAX_SENTENCE_LENGTH = 300;
  static final int MAX_FACTS_PER_SENTENCE = 8;

  /**
   * Digits with optional thousands separators and decimals. Digits attached to letters ("3rd",
   * "v2") count too, so ordinals and version-like tokens are checked as well.
   */
  private static final Pattern NUMBER =
      Pattern.compile("(?<![\\d.,])(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d+))?(?!\\d)");

  private static final Pattern EVALUATIVE =
      Pattern.compile(
          "\\b(good|bad|great|poor|excellent|best|worst|healthy|unhealthy|health|productive"
              + "|unproductive|productivity|lazy|efficient|inefficient|impressive|disappointing"
              + "|abandoned|dead|dying|thriving|struggling|should|recommend|recommended"
              + "|concerning|worrying|alarming)\\b",
          Pattern.CASE_INSENSITIVE);

  public record Result(List<VerifiedSentence> sentences, int removed) {}

  /** A sentence that passed, with the facts it cites resolved. */
  public record VerifiedSentence(String text, List<Fact> facts) {}

  public Result verify(ExplanationDraft draft, FactSheet sheet) {
    Map<String, Fact> facts = sheet.byId();
    List<VerifiedSentence> kept = new ArrayList<>();
    int removed = 0;
    for (ExplanationDraft.Sentence sentence : draft.sentences()) {
      VerifiedSentence verified = kept.size() < MAX_SENTENCES ? check(sentence, facts) : null;
      if (verified == null) {
        removed++;
      } else {
        kept.add(verified);
      }
    }
    return new Result(List.copyOf(kept), removed);
  }

  /** The verified sentence, or null if it fails any check. */
  VerifiedSentence check(ExplanationDraft.Sentence sentence, Map<String, Fact> facts) {
    String text = sentence.text() == null ? "" : sentence.text().strip();
    if (text.isEmpty() || text.length() > MAX_SENTENCE_LENGTH) {
      return null;
    }
    List<String> ids = sentence.facts().stream().filter(Objects::nonNull).distinct().toList();
    if (ids.isEmpty() || ids.size() > MAX_FACTS_PER_SENTENCE) {
      return null;
    }
    List<Fact> cited = new ArrayList<>();
    for (String id : ids) {
      Fact fact = facts.get(id);
      if (fact == null) {
        return null;
      }
      cited.add(fact);
    }
    if (EVALUATIVE.matcher(text).find()) {
      return null;
    }
    Matcher m = NUMBER.matcher(text);
    while (m.find()) {
      String decimals = m.group(2);
      BigDecimal written =
          new BigDecimal(m.group(1).replace(",", "") + (decimals == null ? "" : "." + decimals));
      if (cited.stream().noneMatch(f -> matches(f.number(), written))) {
        return null;
      }
    }
    return new VerifiedSentence(text, List.copyOf(cited));
  }

  /**
   * Whether {@code written} states {@code value}: exactly, or rounded to the number of decimal
   * places it was written with. Rounding to tens, hundreds etc. is not allowed.
   */
  static boolean matches(BigDecimal value, BigDecimal written) {
    if (value == null) {
      return false;
    }
    if (value.compareTo(written) == 0) {
      return true;
    }
    int scale = Math.max(0, written.scale());
    return scale < value.scale()
        && value.setScale(scale, RoundingMode.HALF_UP).compareTo(written) == 0;
  }
}
