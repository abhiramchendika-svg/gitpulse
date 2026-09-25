package io.github.abhiramchendika.gitpulse.api;

import io.github.abhiramchendika.gitpulse.api.dto.ProfileResponse;
import io.github.abhiramchendika.gitpulse.service.ProfileService;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/users/{username}}: public profile analysis. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final ProfileService profileService;

  public UserController(ProfileService profileService) {
    this.profileService = profileService;
  }

  @GetMapping("/{username}")
  public ProfileResponse profile(
      @PathVariable @Pattern(regexp = GitHubNames.OWNER) String username) {
    return profileService.analyze(username);
  }
}
