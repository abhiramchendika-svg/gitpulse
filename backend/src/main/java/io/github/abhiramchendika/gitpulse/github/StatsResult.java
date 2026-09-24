package io.github.abhiramchendika.gitpulse.github;

import java.util.List;

/**
 * Result of a GitHub {@code /stats/*} endpoint. GitHub computes these lazily: the first request
 * returns {@code 202 Accepted} with no data while a background job runs, so "pending" is a normal
 * outcome, not an error.
 */
public record StatsResult<T>(boolean pending, List<T> items) {

  public static <T> StatsResult<T> computing() {
    return new StatsResult<>(true, List.of());
  }

  public static <T> StatsResult<T> ready(List<T> items) {
    return new StatsResult<>(false, List.copyOf(items));
  }
}
