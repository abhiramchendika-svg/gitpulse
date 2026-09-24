package io.github.abhiramchendika.gitpulse.github;

import java.util.List;

/**
 * Items collected by following GitHub's pagination.
 *
 * @param items all items from the pages that were fetched
 * @param pagesFetched number of HTTP requests made
 * @param truncated true if more pages existed but the page cap stopped us
 */
public record PagedResult<T>(List<T> items, int pagesFetched, boolean truncated) {

  public PagedResult {
    items = List.copyOf(items);
  }
}
