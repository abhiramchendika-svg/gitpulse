package io.github.abhiramchendika.gitpulse.analysis;

import io.github.abhiramchendika.gitpulse.analysis.FileActivityStatistics.DirectoryStat;
import io.github.abhiramchendika.gitpulse.analysis.FileActivityStatistics.FileStat;
import io.github.abhiramchendika.gitpulse.analysis.model.CommitFiles;
import io.github.abhiramchendika.gitpulse.analysis.model.CommitFiles.FileChange;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which files and directories change most often, and with the most churn, within a sample of
 * commits. Pure; no I/O.
 *
 * <p>Renames are followed: commits are processed newest first, and when a commit renames {@code old
 * -> new}, {@code old} is recorded as an alias so that older commits touching {@code old} are
 * credited to the file's current name. Complexity O(total file changes).
 */
public class FileActivityAnalyzer {

  private final int listLimit;

  public FileActivityAnalyzer(int listLimit) {
    this.listLimit = listLimit;
  }

  public FileActivityStatistics analyze(List<CommitFiles> sample) {
    List<CommitFiles> newestFirst =
        sample.stream().sorted(Comparator.comparing(CommitFiles::authoredAt).reversed()).toList();

    Map<String, String> renamedTo = new HashMap<>();
    Map<String, FileAccumulator> files = new HashMap<>();
    Map<String, DirectoryAccumulator> directories = new HashMap<>();

    for (CommitFiles commit : newestFirst) {
      for (FileChange change : commit.files()) {
        String current = resolve(change.path(), renamedTo);
        if (change.previousPath() != null && !change.previousPath().equals(change.path())) {
          renamedTo.put(change.previousPath(), current);
        }

        FileAccumulator file = files.computeIfAbsent(current, FileAccumulator::new);
        // Newest first: the first change seen is the most recent one.
        if (file.commits.isEmpty()) {
          file.lastChangedAt = commit.authoredAt();
          file.deleted = "removed".equals(change.status());
        }
        file.commits.add(commit.sha());
        file.authors.add(commit.authorKey());
        file.additions += change.additions();
        file.deletions += change.deletions();

        DirectoryAccumulator directory =
            directories.computeIfAbsent(parentOf(current), DirectoryAccumulator::new);
        directory.commits.add(commit.sha());
        directory.files.add(current);
        directory.churn += change.additions() + change.deletions();
      }
    }

    List<FileStat> stats = files.values().stream().map(FileAccumulator::toStat).toList();
    return new FileActivityStatistics(
        sample.size(),
        newestFirst.isEmpty() ? null : newestFirst.getLast().authoredAt(),
        newestFirst.isEmpty() ? null : newestFirst.getFirst().authoredAt(),
        stats.size(),
        (int) sample.stream().filter(CommitFiles::filesTruncated).count(),
        top(
            stats,
            Comparator.comparingInt(FileStat::commits)
                .reversed()
                .thenComparing(Comparator.comparingLong(FileStat::churn).reversed())),
        top(stats, Comparator.comparingLong(FileStat::churn).reversed()),
        directories.values().stream()
            .map(DirectoryAccumulator::toStat)
            .sorted(
                Comparator.comparingInt(DirectoryStat::commits)
                    .reversed()
                    .thenComparing(Comparator.comparingLong(DirectoryStat::churn).reversed())
                    .thenComparing(DirectoryStat::path))
            .limit(listLimit)
            .toList(),
        top(stats, Comparator.comparing(FileStat::lastChangedAt).reversed()));
  }

  private List<FileStat> top(List<FileStat> stats, Comparator<FileStat> order) {
    return stats.stream().sorted(order.thenComparing(FileStat::path)).limit(listLimit).toList();
  }

  /** Follows a chain of renames (a -> b -> c) to the newest name, guarding against cycles. */
  static String resolve(String path, Map<String, String> renamedTo) {
    String current = path;
    Set<String> seen = new HashSet<>();
    while (renamedTo.containsKey(current) && seen.add(current)) {
      current = renamedTo.get(current);
    }
    return current;
  }

  /** "src/main/App.java" -> "src/main"; "README.md" -> "" (the repository root). */
  static String parentOf(String path) {
    int slash = path.lastIndexOf('/');
    return slash < 0 ? "" : path.substring(0, slash);
  }

  private static final class FileAccumulator {
    final String path;
    final Set<String> commits = new HashSet<>();
    final Set<String> authors = new HashSet<>();
    long additions;
    long deletions;
    Instant lastChangedAt;
    boolean deleted;

    FileAccumulator(String path) {
      this.path = path;
    }

    FileStat toStat() {
      return new FileStat(
          path,
          commits.size(),
          additions,
          deletions,
          additions + deletions,
          authors.size(),
          lastChangedAt,
          deleted);
    }
  }

  private static final class DirectoryAccumulator {
    final String path;
    final Set<String> commits = new HashSet<>();
    final Set<String> files = new HashSet<>();
    long churn;

    DirectoryAccumulator(String path) {
      this.path = path;
    }

    DirectoryStat toStat() {
      return new DirectoryStat(path, commits.size(), files.size(), churn);
    }
  }
}
