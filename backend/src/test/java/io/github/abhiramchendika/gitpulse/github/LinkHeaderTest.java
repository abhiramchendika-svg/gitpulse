package io.github.abhiramchendika.gitpulse.github;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class LinkHeaderTest {

  @Test
  void parsesNextAndLast() {
    String header =
        "<https://api.github.com/repositories/1/commits?per_page=100&page=2>; rel=\"next\", "
            + "<https://api.github.com/repositories/1/commits?per_page=100&page=34>; rel=\"last\"";

    LinkHeader links = LinkHeader.parse(header);

    assertThat(links.next())
        .contains(URI.create("https://api.github.com/repositories/1/commits?per_page=100&page=2"));
    assertThat(links.lastPageNumber()).hasValue(34);
  }

  @Test
  void lastPage_hasNoNextLink() {
    String header =
        "<https://api.github.com/repositories/1/commits?page=1>; rel=\"first\", "
            + "<https://api.github.com/repositories/1/commits?page=33>; rel=\"prev\"";

    LinkHeader links = LinkHeader.parse(header);

    assertThat(links.next()).isEmpty();
    assertThat(links.lastPageNumber()).isEmpty();
  }

  @Test
  void missingOrBlankHeader_meansSinglePage() {
    assertThat(LinkHeader.parse(null).next()).isEmpty();
    assertThat(LinkHeader.parse("").next()).isEmpty();
  }

  @Test
  void malformedHeader_isIgnoredRatherThanThrowing() {
    LinkHeader links = LinkHeader.parse("garbage; rel=next, <not a uri>; rel=\"next\"");

    assertThat(links.next()).isEmpty();
  }

  @Test
  void lastLinkWithoutPageParam_hasNoPageNumber() {
    LinkHeader links = LinkHeader.parse("<https://api.github.com/x?per_page=1>; rel=\"last\"");

    assertThat(links.last()).isPresent();
    assertThat(links.lastPageNumber()).isEmpty();
  }
}
