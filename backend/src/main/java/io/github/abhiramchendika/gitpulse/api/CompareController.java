package io.github.abhiramchendika.gitpulse.api;

import io.github.abhiramchendika.gitpulse.api.dto.ComparisonResponse;
import io.github.abhiramchendika.gitpulse.service.ComparisonService;
import io.github.abhiramchendika.gitpulse.service.InvalidRequestException;
import io.github.abhiramchendika.gitpulse.service.RepositoryRef;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/compare?repos=owner1/repo1,owner2/repo2}. */
@RestController
@RequestMapping("/api/v1/compare")
public class CompareController {

  /** Two for now; the API takes a list so it can grow without breaking clients. */
  static final int REPOSITORY_COUNT = 2;

  private static final Pattern OWNER = Pattern.compile(GitHubNames.OWNER);
  private static final Pattern REPO = Pattern.compile(GitHubNames.REPO);

  private final ComparisonService comparisonService;

  public CompareController(ComparisonService comparisonService) {
    this.comparisonService = comparisonService;
  }

  @GetMapping
  public ComparisonResponse compare(@RequestParam String repos) {
    return comparisonService.compare(parse(repos));
  }

  /** Validates with the same allow-list as the path-based endpoints. */
  static List<RepositoryRef> parse(String repos) {
    String[] parts = repos.split(",", -1);
    if (parts.length != REPOSITORY_COUNT) {
      throw new InvalidRequestException(
          "Provide exactly " + REPOSITORY_COUNT + " repositories: repos=owner/repo,owner/repo");
    }
    List<RepositoryRef> refs = new ArrayList<>();
    for (String part : parts) {
      String[] names = part.trim().split("/", -1);
      if (names.length != 2
          || !OWNER.matcher(names[0]).matches()
          || !REPO.matcher(names[1]).matches()) {
        throw new InvalidRequestException("Invalid repository name: use owner/repo.");
      }
      refs.add(new RepositoryRef(names[0], names[1]));
    }
    if (refs.get(0).equals(refs.get(1))) {
      throw new InvalidRequestException("Choose two different repositories.");
    }
    return refs;
  }
}
