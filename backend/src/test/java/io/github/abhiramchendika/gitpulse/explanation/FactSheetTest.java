package io.github.abhiramchendika.gitpulse.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FactSheetTest {

  @Test
  void containsTheDashboardNumbersWithStableIds() {
    Map<String, Fact> facts = ExplanationFixtures.sheet().byId();

    assertThat(facts.get("window.days").value()).isEqualTo(BigDecimal.valueOf(365));
    assertThat(facts.get("repository.stars").value()).isEqualTo(BigDecimal.valueOf(1734));
    assertThat(facts.get("repository.createdYear").value()).isEqualTo(BigDecimal.valueOf(2011));
    assertThat(facts.get("commits.total").value()).isEqualTo(BigDecimal.valueOf(240));
    assertThat(facts.get("commits.averagePerWeek").value()).isEqualTo(new BigDecimal("4.62"));
    assertThat(facts.get("commits.longestGapDays").value()).isEqualTo(new BigDecimal("19"));
    assertThat(facts.get("pullRequests.timeToMerge.medianHours").value())
        .isEqualTo(new BigDecimal("12.47"));
    assertThat(facts.get("issues.closedAllTime").value()).isEqualTo(BigDecimal.valueOf(410));
    assertThat(facts.get("languages.1.name").value()).isEqualTo("Java");
    assertThat(facts.get("repository.archived").value()).isEqualTo(false);
    // Only the top three languages.
    assertThat(facts).containsKey("languages.3.name").doesNotContainKey("languages.4.name");
  }

  @Test
  void neverContainsNamesLoginsOrFreeText() {
    String serialised =
        ExplanationPrompt.userMessage(ExplanationFixtures.sheet(), JsonMapper.builder().build());

    assertThat(serialised)
        .doesNotContain(ExplanationFixtures.INJECTION)
        .doesNotContain("janedoe")
        .doesNotContain("Jane Doe")
        .doesNotContain("octocat");
  }

  @Test
  void leavesOutPartialCounts_andFlagsATruncatedWindow() {
    Map<String, Fact> complete = ExplanationFixtures.sheet().byId();
    Map<String, Fact> partial = FactSheet.from(ExplanationFixtures.inputs(true, true)).byId();

    assertThat(complete)
        .containsKey("activity.commitsLast30Days")
        .doesNotContainKey("window.analysedDays");
    // Lower bounds could be stated as exact, so they are not offered at all.
    assertThat(partial).doesNotContainKey("activity.commitsLast30Days");
    // 27 June 00:00 to 25 September 12:00 is 90.5 days, rounded half up.
    assertThat(partial.get("window.analysedDays").value()).isEqualTo(BigDecimal.valueOf(91));
  }

  @Test
  void idsAreUnique() {
    FactSheet sheet = ExplanationFixtures.sheet();
    assertThat(sheet.byId()).hasSize(sheet.facts().size());
  }
}
