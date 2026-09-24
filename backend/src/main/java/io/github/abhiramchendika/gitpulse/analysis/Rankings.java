package io.github.abhiramchendika.gitpulse.analysis;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Top-N counting shared by the pull request and issue analyzers. */
public final class Rankings {

  private Rankings() {}

  /** Someone who did something (opened a pull request, filed an issue). */
  public record Actor(String login, boolean bot) {}

  public record ActorCount(String login, boolean bot, int count) {}

  /** Counts per actor, most first, ties broken by login; actors with no login are skipped. */
  public static List<ActorCount> top(List<Actor> actors, int limit) {
    Map<Actor, Integer> counts = new HashMap<>();
    for (Actor actor : actors) {
      if (actor.login() != null) {
        counts.merge(actor, 1, Integer::sum);
      }
    }
    return counts.entrySet().stream()
        .map(e -> new ActorCount(e.getKey().login(), e.getKey().bot(), e.getValue()))
        .sorted(
            Comparator.comparingInt(ActorCount::count).reversed().thenComparing(ActorCount::login))
        .limit(limit)
        .toList();
  }
}
