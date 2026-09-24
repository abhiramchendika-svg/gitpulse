package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.github.exception.GitHubRateLimitException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

/**
 * Runs I/O-bound calls in parallel with a hard cap on how many run at once.
 *
 * <p>Each call gets its own virtual thread (Java 21): cheap to create, and a thread blocked on an
 * HTTP response costs almost nothing. The semaphore is what enforces politeness towards GitHub,
 * whose secondary rate limits punish bursts of concurrent requests.
 */
public final class BoundedParallel {

  private BoundedParallel() {}

  /**
   * Applies {@code call} to every item, at most {@code maxConcurrent} at a time, and returns the
   * results in input order. If any call fails, the failure is rethrown after all calls finish, a
   * rate-limit failure taking precedence (it is the most useful one to report).
   */
  public static <T, R> List<R> map(List<T> items, int maxConcurrent, Function<T, R> call) {
    Semaphore permits = new Semaphore(maxConcurrent);
    List<Future<R>> futures = new ArrayList<>(items.size());
    // try-with-resources: close() waits for every task to finish.
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (T item : items) {
        futures.add(
            executor.submit(
                () -> {
                  permits.acquire();
                  try {
                    return call.apply(item);
                  } finally {
                    permits.release();
                  }
                }));
      }
    }

    List<R> results = new ArrayList<>(items.size());
    RuntimeException failure = null;
    for (Future<R> future : futures) {
      try {
        results.add(future.get());
      } catch (ExecutionException e) {
        RuntimeException cause =
            e.getCause() instanceof RuntimeException re ? re : new IllegalStateException(e);
        if (failure == null || cause instanceof GitHubRateLimitException) {
          failure = cause;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for GitHub requests", e);
      }
    }
    if (failure != null) {
      throw failure;
    }
    return results;
  }
}
