package io.github.abhiramchendika.gitpulse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.abhiramchendika.gitpulse.analysis.FileActivityAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.model.CommitFiles;
import io.github.abhiramchendika.gitpulse.analysis.model.CommitRecord;
import io.github.abhiramchendika.gitpulse.api.CompareControllerAccess;
import io.github.abhiramchendika.gitpulse.api.dto.FileActivityResponse;
import io.github.abhiramchendika.gitpulse.config.AnalysisProperties;
import io.github.abhiramchendika.gitpulse.config.FileActivityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileActivityAndComparisonTest {

  private static final RepositoryRef REF = new RepositoryRef("o", "r");
  private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

  private final RepositoryDataService data = mock(RepositoryDataService.class);
  private final AnalysisWindows windows =
      new AnalysisWindows(
          new AnalysisProperties(10, 5, 365, 3650, Duration.ofDays(14), 10, 10, 10, 100, 5, 5),
          Clock.fixed(NOW, ZoneOffset.UTC));
  private final FileActivityService service =
      new FileActivityService(
          data, new FileActivityAnalyzer(10), windows, new FileActivityProperties(3, 5, 8, 2, 10));

  private static CommitRecord commit(int i, boolean merge) {
    Instant at = NOW.minus(Duration.ofDays(i));
    return new CommitRecord(
        "%07x".formatted(i + 0xa000), "a", "a", "a", at, at, merge, false, "h", "u");
  }

  private void givenCommits(List<CommitRecord> commits) {
    when(data.commits(any(), any(), any())).thenReturn(new CommitSample(commits, false, false));
    when(data.commitFiles(any(), anyString()))
        .thenAnswer(
            inv ->
                new CommitFiles(
                    inv.getArgument(1),
                    "a",
                    NOW,
                    List.of(new CommitFiles.FileChange("f.txt", null, "modified", 1, 1)),
                    false));
  }

  @Test
  void anonymous_sampleIsCappedAtTheAnonymousLimit() {
    when(data.authenticated()).thenReturn(false);
    List<CommitRecord> commits = new ArrayList<>();
    for (int i = 0; i < 10; i++) commits.add(commit(i, false));
    givenCommits(commits);

    FileActivityResponse r = service.analyze(REF, 50);

    assertThat(r.meta().sampleLimit()).isEqualTo(3);
    assertThat(r.meta().requestedSample()).isEqualTo(50);
    assertThat(r.statistics().commitsAnalyzed()).isEqualTo(3);
    verify(data, times(3)).commitFiles(any(), anyString());
  }

  @Test
  void authenticated_usesTheDefaultSampleAndSkipsMerges_newestFirst() {
    when(data.authenticated()).thenReturn(true);
    List<CommitRecord> commits = new ArrayList<>();
    for (int i = 0; i < 10; i++) commits.add(commit(i, i % 2 == 0)); // even = merge
    givenCommits(commits);

    FileActivityResponse r = service.analyze(REF, null);

    assertThat(r.meta().requestedSample()).isEqualTo(5);
    assertThat(r.meta().mergeCommitsSkipped()).isEqualTo(5);
    assertThat(r.meta().candidateCommits()).isEqualTo(5);
    // The newest non-merge commit (1 day ago) is included; merges never are.
    verify(data).commitFiles(REF, commit(1, false).sha());
    verify(data, never()).commitFiles(REF, commit(0, true).sha());
  }

  @Test
  void compareParsing_acceptsTwoDistinctValidRepositories() {
    assertThat(CompareControllerAccess.parse("facebook/react, vuejs/core"))
        .containsExactly(
            new RepositoryRef("facebook", "react"), new RepositoryRef("vuejs", "core"));
  }

  @Test
  void compareParsing_rejectsBadInput() {
    for (String bad :
        List.of("a/b", "a/b,c/d,e/f", "a/b,a/b", "A/B,a/b", "a/b,../x", "a/b,c", "a/b,")) {
      assertThatThrownBy(() -> CompareControllerAccess.parse(bad))
          .as(bad)
          .isInstanceOf(InvalidRequestException.class);
    }
  }
}
