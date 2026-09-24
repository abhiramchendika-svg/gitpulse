package io.github.abhiramchendika.gitpulse.analysis;

import java.time.Duration;
import java.util.List;

/**
 * Summaries of durations such as "time to merge".
 *
 * <p>Median and 90th percentile rather than the mean: durations are heavily skewed (one pull
 * request left open for two years would drag a mean far away from what is typical), and the median
 * is not affected by such outliers.
 */
public final class Durations {

  private Durations() {}

  /**
   * @param count how many durations were summarised
   * @param medianHours 50th percentile; for an even count, the mean of the two middle values
   * @param p90Hours 90th percentile, nearest-rank method: the smallest value with at least 90% of
   *     values at or below it
   */
  public record Summary(int count, double medianHours, double p90Hours) {}

  /** Returns null when there is nothing to summarise, so clients show "—" rather than 0. */
  public static Summary summarise(List<Duration> durations) {
    if (durations.isEmpty()) {
      return null;
    }
    List<Double> hours = durations.stream().map(d -> d.toSeconds() / 3600.0).sorted().toList();
    int n = hours.size();
    double median = n % 2 == 1 ? hours.get(n / 2) : (hours.get(n / 2 - 1) + hours.get(n / 2)) / 2.0;
    double p90 = hours.get((int) Math.ceil(0.9 * n) - 1);
    return new Summary(n, Numbers.round(median, 1), Numbers.round(p90, 1));
  }
}
