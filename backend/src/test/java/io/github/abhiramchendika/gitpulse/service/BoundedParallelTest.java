package io.github.abhiramchendika.gitpulse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abhiramchendika.gitpulse.github.exception.GitHubNotFoundException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubRateLimitException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BoundedParallelTest {

  @Test
  void keepsInputOrder() {
    List<Integer> results =
        BoundedParallel.map(
            IntStream.range(0, 20).boxed().toList(),
            4,
            i -> {
              sleep(20 - i); // later items finish first
              return i * 10;
            });

    assertThat(results).isEqualTo(IntStream.range(0, 20).map(i -> i * 10).boxed().toList());
  }

  @Test
  void neverRunsMoreThanTheLimitAtOnce() {
    AtomicInteger running = new AtomicInteger();
    AtomicInteger peak = new AtomicInteger();

    BoundedParallel.map(
        IntStream.range(0, 30).boxed().toList(),
        4,
        i -> {
          int now = running.incrementAndGet();
          peak.accumulateAndGet(now, Math::max);
          sleep(15);
          running.decrementAndGet();
          return i;
        });

    assertThat(peak.get()).isLessThanOrEqualTo(4).isGreaterThan(1);
  }

  @Test
  void actuallyRunsInParallel() {
    long start = System.nanoTime();
    BoundedParallel.map(
        IntStream.range(0, 8).boxed().toList(),
        4,
        i -> {
          sleep(100);
          return i;
        });
    long millis = (System.nanoTime() - start) / 1_000_000;

    // Sequential would take ~800 ms; 4 at a time takes ~200 ms.
    assertThat(millis).isLessThan(600);
  }

  @Test
  void rethrowsFailures_preferringRateLimits() {
    assertThatThrownBy(
            () ->
                BoundedParallel.map(
                    List.of(1, 2, 3),
                    2,
                    i -> {
                      if (i == 1) throw new GitHubNotFoundException();
                      if (i == 3) throw new GitHubRateLimitException(null, null);
                      return i;
                    }))
        .isInstanceOf(GitHubRateLimitException.class);
  }

  @Test
  void emptyInput() {
    assertThat(BoundedParallel.map(List.<Integer>of(), 4, i -> i)).isEmpty();
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
