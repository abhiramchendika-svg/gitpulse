package io.github.abhiramchendika.gitpulse.explanation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Allows at most {@code max} calls in any rolling hour (a sliding window, so there is no burst at
 * the top of each hour). Every attempt counts, successful or not, because failed model calls can
 * cost money too.
 */
public final class HourlyLimiter {

  static final Duration WINDOW = Duration.ofHours(1);

  private final int max;
  private final Clock clock;
  private final Deque<Instant> calls = new ArrayDeque<>();

  public HourlyLimiter(int max, Clock clock) {
    this.max = max;
    this.clock = clock;
  }

  /**
   * Records a call, or throws if the window is full.
   *
   * @throws ExplanationLimitException with the time a slot frees up
   */
  public synchronized void acquire() {
    Instant now = clock.instant();
    while (!calls.isEmpty() && !calls.peekFirst().plus(WINDOW).isAfter(now)) {
      calls.removeFirst();
    }
    if (calls.size() >= max) {
      throw new ExplanationLimitException(calls.peekFirst().plus(WINDOW));
    }
    calls.addLast(now);
  }
}
