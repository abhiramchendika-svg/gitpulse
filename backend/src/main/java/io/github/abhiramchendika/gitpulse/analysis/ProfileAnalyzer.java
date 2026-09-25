package io.github.abhiramchendika.gitpulse.analysis;

import io.github.abhiramchendika.gitpulse.analysis.ProfileStatistics.Events;
import io.github.abhiramchendika.gitpulse.analysis.ProfileStatistics.LanguageCount;
import io.github.abhiramchendika.gitpulse.analysis.ProfileStatistics.RepositoryCount;
import io.github.abhiramchendika.gitpulse.analysis.ProfileStatistics.RepositoryItem;
import io.github.abhiramchendika.gitpulse.analysis.ProfileStatistics.TypeCount;
import io.github.abhiramchendika.gitpulse.analysis.ProfileStatistics.YearCount;
import io.github.abhiramchendika.gitpulse.analysis.model.OwnedRepository;
import io.github.abhiramchendika.gitpulse.analysis.model.PublicEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/** Facts about an account's public repositories and recent public events. Pure; no I/O. */
public class ProfileAnalyzer {

  /** Readable names for common event types; anything else keeps GitHub's name minus "Event". */
  private static final Map<String, String> EVENT_LABELS =
      Map.ofEntries(
          Map.entry("PushEvent", "Pushes"),
          Map.entry("PullRequestEvent", "Pull request activity"),
          Map.entry("PullRequestReviewEvent", "Pull request reviews"),
          Map.entry("PullRequestReviewCommentEvent", "Review comments"),
          Map.entry("IssuesEvent", "Issue activity"),
          Map.entry("IssueCommentEvent", "Issue comments"),
          Map.entry("CreateEvent", "Branches/tags/repositories created"),
          Map.entry("DeleteEvent", "Branches/tags deleted"),
          Map.entry("ForkEvent", "Forks"),
          Map.entry("WatchEvent", "Stars given"),
          Map.entry("ReleaseEvent", "Releases"),
          Map.entry("PublicEvent", "Repositories made public"));

  private final int listLimit;

  public ProfileAnalyzer(int listLimit) {
    this.listLimit = listLimit;
  }

  /**
   * @param events null when events are not analysed (organizations)
   */
  public ProfileStatistics analyze(
      List<OwnedRepository> repositories, List<PublicEvent> events, Instant now) {
    List<OwnedRepository> originals = repositories.stream().filter(r -> !r.fork()).toList();

    Map<String, Integer> byLanguage = new HashMap<>();
    for (OwnedRepository repo : originals) {
      if (repo.language() != null) {
        byLanguage.merge(repo.language(), 1, Integer::sum);
      }
    }
    int withLanguage = byLanguage.values().stream().mapToInt(Integer::intValue).sum();

    return new ProfileStatistics(
        repositories.size(),
        originals.size(),
        repositories.size() - originals.size(),
        (int) repositories.stream().filter(OwnedRepository::archived).count(),
        originals.stream().mapToLong(OwnedRepository::stars).sum(),
        originals.stream().mapToLong(OwnedRepository::forks).sum(),
        byLanguage.entrySet().stream()
            .map(
                e ->
                    new LanguageCount(
                        e.getKey(), e.getValue(), Numbers.percent(e.getValue(), withLanguage)))
            .sorted(
                Comparator.comparingInt(LanguageCount::repositories)
                    .reversed()
                    .thenComparing(LanguageCount::name))
            .limit(listLimit)
            .toList(),
        originals.size() - withLanguage,
        pushedWithin(repositories, now, Duration.ofDays(30)),
        pushedWithin(repositories, now, Duration.ofDays(90)),
        pushedWithin(repositories, now, Duration.ofDays(365)),
        originals.stream()
            .sorted(
                Comparator.comparingInt(OwnedRepository::stars)
                    .reversed()
                    .thenComparing(OwnedRepository::fullName))
            .limit(listLimit)
            .map(ProfileAnalyzer::item)
            .toList(),
        repositories.stream()
            .filter(r -> r.pushedAt() != null)
            .sorted(Comparator.comparing(OwnedRepository::pushedAt).reversed())
            .limit(listLimit)
            .map(ProfileAnalyzer::item)
            .toList(),
        createdPerYear(repositories, now),
        events == null ? null : events(events));
  }

  private static int pushedWithin(List<OwnedRepository> repos, Instant now, Duration period) {
    Instant from = now.minus(period);
    return (int)
        repos.stream()
            .map(OwnedRepository::pushedAt)
            .filter(Objects::nonNull)
            .filter(t -> t.isAfter(from) && !t.isAfter(now))
            .count();
  }

  /** Zero-filled from the first year a repository was created up to the current year. */
  static List<YearCount> createdPerYear(List<OwnedRepository> repos, Instant now) {
    Map<Integer, Integer> counts = new TreeMap<>();
    for (OwnedRepository repo : repos) {
      if (repo.createdAt() != null) {
        counts.merge(repo.createdAt().atZone(ZoneOffset.UTC).getYear(), 1, Integer::sum);
      }
    }
    if (counts.isEmpty()) {
      return List.of();
    }
    int first = counts.keySet().iterator().next();
    int last = Math.max(now.atZone(ZoneOffset.UTC).getYear(), first);
    List<YearCount> result = new ArrayList<>();
    for (int year = first; year <= last; year++) {
      result.add(new YearCount(year, counts.getOrDefault(year, 0)));
    }
    return result;
  }

  private Events events(List<PublicEvent> events) {
    if (events.isEmpty()) {
      return new Events(0, null, null, 0, 0, List.of(), List.of());
    }
    Map<String, Long> byType =
        events.stream().collect(Collectors.groupingBy(PublicEvent::type, Collectors.counting()));
    Map<String, Long> byRepository =
        events.stream()
            .filter(e -> e.repository() != null)
            .collect(Collectors.groupingBy(PublicEvent::repository, Collectors.counting()));
    return new Events(
        events.size(),
        events.stream().map(PublicEvent::createdAt).min(Comparator.naturalOrder()).orElse(null),
        events.stream().map(PublicEvent::createdAt).max(Comparator.naturalOrder()).orElse(null),
        (int)
            events.stream()
                .map(e -> e.createdAt().atZone(ZoneOffset.UTC).toLocalDate())
                .distinct()
                .count(),
        byRepository.size(),
        byType.entrySet().stream()
            .map(e -> new TypeCount(e.getKey(), label(e.getKey()), e.getValue().intValue()))
            .sorted(
                Comparator.comparingInt(TypeCount::count).reversed().thenComparing(TypeCount::type))
            .toList(),
        top(byRepository, RepositoryCount::new));
  }

  private <T> List<T> top(Map<String, Long> counts, BiFunction<String, Integer, T> make) {
    return counts.entrySet().stream()
        .sorted(
            Map.Entry.<String, Long>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .limit(listLimit)
        .map(e -> make.apply(e.getKey(), e.getValue().intValue()))
        .toList();
  }

  static String label(String type) {
    return EVENT_LABELS.getOrDefault(
        type, type.endsWith("Event") ? type.substring(0, type.length() - 5) : type);
  }

  private static RepositoryItem item(OwnedRepository r) {
    return new RepositoryItem(
        r.fullName(),
        r.htmlUrl(),
        r.description(),
        r.language(),
        r.stars(),
        r.forks(),
        r.fork(),
        r.archived(),
        r.pushedAt());
  }
}
