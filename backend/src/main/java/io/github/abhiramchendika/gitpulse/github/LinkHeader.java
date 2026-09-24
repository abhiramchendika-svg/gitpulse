package io.github.abhiramchendika.gitpulse.github;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Parses GitHub's pagination {@code Link} header.
 *
 * <p>Example: {@code <https://api.github.com/repositories/1/commits?page=2>; rel="next",
 * <https://api.github.com/repositories/1/commits?page=34>; rel="last"}
 *
 * <p>Following {@code rel="next"} (instead of incrementing a page counter ourselves) is what GitHub
 * recommends, and the {@code rel="last"} page number lets us count items cheaply: request {@code
 * per_page=1} and the last page number equals the total number of items.
 */
public final class LinkHeader {

  private static final Pattern LINK = Pattern.compile("<([^>]+)>\\s*;\\s*rel=\"([^\"]+)\"");

  private final Map<String, URI> links;

  private LinkHeader(Map<String, URI> links) {
    this.links = links;
  }

  public static LinkHeader parse(String header) {
    Map<String, URI> links = new HashMap<>();
    if (header != null) {
      Matcher matcher = LINK.matcher(header);
      while (matcher.find()) {
        try {
          links.put(matcher.group(2), URI.create(matcher.group(1)));
        } catch (IllegalArgumentException ignored) {
          // Malformed URL in one relation: skip it rather than failing the whole response.
        }
      }
    }
    return new LinkHeader(Map.copyOf(links));
  }

  public Optional<URI> next() {
    return Optional.ofNullable(links.get("next"));
  }

  public Optional<URI> last() {
    return Optional.ofNullable(links.get("last"));
  }

  /** The {@code page} query parameter of the {@code rel="last"} link, if present. */
  public OptionalInt lastPageNumber() {
    return last().map(LinkHeader::pageParam).orElse(OptionalInt.empty());
  }

  private static OptionalInt pageParam(URI uri) {
    String page = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("page");
    try {
      return page == null ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(page));
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
    }
  }
}
