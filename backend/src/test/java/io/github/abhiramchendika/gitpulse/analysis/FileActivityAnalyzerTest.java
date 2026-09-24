package io.github.abhiramchendika.gitpulse.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.github.abhiramchendika.gitpulse.analysis.FileActivityStatistics.DirectoryStat;
import io.github.abhiramchendika.gitpulse.analysis.FileActivityStatistics.FileStat;
import io.github.abhiramchendika.gitpulse.analysis.model.CommitFiles;
import io.github.abhiramchendika.gitpulse.analysis.model.CommitFiles.FileChange;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FileActivityAnalyzerTest {

  private final FileActivityAnalyzer analyzer = new FileActivityAnalyzer(10);

  private static CommitFiles commit(String sha, String author, String when, FileChange... files) {
    return new CommitFiles(sha, author, Instant.parse(when), List.of(files), false);
  }

  private static FileChange modified(String path, int add, int del) {
    return new FileChange(path, null, "modified", add, del);
  }

  private static FileStat stat(FileActivityStatistics s, String path) {
    return s.mostFrequentlyChanged().stream()
        .filter(f -> f.path().equals(path))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void countsCommitsChurnAndAuthorsPerFile() {
    FileActivityStatistics s =
        analyzer.analyze(
            List.of(
                commit("c1", "alice", "2026-01-01T00:00:00Z", modified("src/App.java", 10, 2)),
                commit(
                    "c2",
                    "bob",
                    "2026-01-02T00:00:00Z",
                    modified("src/App.java", 5, 5),
                    modified("README.md", 1, 0)),
                commit("c3", "alice", "2026-01-03T00:00:00Z", modified("src/App.java", 1, 1))));

    FileStat app = stat(s, "src/App.java");
    assertThat(app.commits()).isEqualTo(3);
    assertThat(app.additions()).isEqualTo(16);
    assertThat(app.deletions()).isEqualTo(8);
    assertThat(app.churn()).isEqualTo(24);
    assertThat(app.distinctAuthors()).isEqualTo(2);
    assertThat(app.lastChangedAt()).isEqualTo(Instant.parse("2026-01-03T00:00:00Z"));
    assertThat(s.commitsAnalyzed()).isEqualTo(3);
    assertThat(s.filesTouched()).isEqualTo(2);
    assertThat(s.sampleFrom()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(s.sampleTo()).isEqualTo(Instant.parse("2026-01-03T00:00:00Z"));
  }

  @Test
  void frequencyAndChurnRankingsDiffer() {
    FileActivityStatistics s =
        analyzer.analyze(
            List.of(
                commit("c1", "a", "2026-01-01T00:00:00Z", modified("often.txt", 1, 0)),
                commit("c2", "a", "2026-01-02T00:00:00Z", modified("often.txt", 1, 0)),
                commit("c3", "a", "2026-01-03T00:00:00Z", modified("big.txt", 500, 100))));

    assertThat(s.mostFrequentlyChanged().getFirst().path()).isEqualTo("often.txt");
    assertThat(s.highestChurn().getFirst().path()).isEqualTo("big.txt");
    assertThat(s.recentlyChanged().getFirst().path()).isEqualTo("big.txt");
  }

  @Test
  void renamesInTheSampleAreCreditedToTheCurrentName() {
    FileActivityStatistics s =
        analyzer.analyze(
            List.of(
                // Oldest: edits under the old name.
                commit("c1", "a", "2026-01-01T00:00:00Z", modified("old/Name.java", 10, 0)),
                // Then renamed old -> mid.
                commit(
                    "c2",
                    "a",
                    "2026-01-02T00:00:00Z",
                    new FileChange("mid/Name.java", "old/Name.java", "renamed", 0, 0)),
                // Then renamed mid -> new (a chain).
                commit(
                    "c3",
                    "a",
                    "2026-01-03T00:00:00Z",
                    new FileChange("new/Name.java", "mid/Name.java", "renamed", 1, 1))));

    assertThat(s.mostFrequentlyChanged())
        .extracting(FileStat::path, FileStat::commits, FileStat::churn)
        .containsExactly(tuple("new/Name.java", 3, 12L));
  }

  @Test
  void resolve_followsChainsAndSurvivesCycles() {
    assertThat(FileActivityAnalyzer.resolve("a", Map.of("a", "b", "b", "c"))).isEqualTo("c");
    assertThat(FileActivityAnalyzer.resolve("x", Map.of("x", "y", "y", "x"))).isIn("x", "y");
  }

  @Test
  void aFileWhoseLatestChangeRemovedItIsMarkedDeleted() {
    FileActivityStatistics s =
        analyzer.analyze(
            List.of(
                commit("c1", "a", "2026-01-01T00:00:00Z", modified("gone.txt", 3, 0)),
                commit(
                    "c2",
                    "a",
                    "2026-01-02T00:00:00Z",
                    new FileChange("gone.txt", null, "removed", 0, 3))));

    assertThat(stat(s, "gone.txt").deleted()).isTrue();
  }

  @Test
  void directoriesCountDistinctCommitsAndFiles() {
    FileActivityStatistics s =
        analyzer.analyze(
            List.of(
                commit(
                    "c1",
                    "a",
                    "2026-01-01T00:00:00Z",
                    modified("src/A.java", 1, 0),
                    modified("src/B.java", 1, 0)),
                commit("c2", "a", "2026-01-02T00:00:00Z", modified("src/A.java", 2, 0)),
                commit("c3", "a", "2026-01-03T00:00:00Z", modified("README.md", 1, 0))));

    assertThat(s.directories())
        .extracting(DirectoryStat::path, DirectoryStat::commits, DirectoryStat::filesTouched)
        .containsExactly(tuple("src", 2, 2), tuple("", 1, 1));
  }

  @Test
  void countsCommitsWhoseFileListWasTruncated() {
    FileActivityStatistics s =
        analyzer.analyze(
            List.of(
                new CommitFiles(
                    "c1",
                    "a",
                    Instant.parse("2026-01-01T00:00:00Z"),
                    List.of(modified("x", 1, 1)),
                    true)));

    assertThat(s.commitsWithTruncatedFiles()).isEqualTo(1);
  }

  @Test
  void emptySample() {
    FileActivityStatistics s = analyzer.analyze(List.of());

    assertThat(s.commitsAnalyzed()).isZero();
    assertThat(s.sampleFrom()).isNull();
    assertThat(s.mostFrequentlyChanged()).isEmpty();
  }

  @Test
  void parentOf() {
    assertThat(FileActivityAnalyzer.parentOf("a/b/c.txt")).isEqualTo("a/b");
    assertThat(FileActivityAnalyzer.parentOf("c.txt")).isEmpty();
  }
}
