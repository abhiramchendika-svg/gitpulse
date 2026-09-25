package io.github.abhiramchendika.gitpulse.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.abhiramchendika.gitpulse.service.ProfileService;
import io.github.abhiramchendika.gitpulse.service.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
class UserControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ProfileService profileService;

  @Test
  void validUsername_callsTheService() throws Exception {
    mockMvc.perform(get("/api/v1/users/octo-cat")).andExpect(status().isOk());
    verify(profileService).analyze("octo-cat");
  }

  @Test
  void invalidUsername_is400() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/-bad"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    mockMvc.perform(get("/api/v1/users/under_score")).andExpect(status().isBadRequest());
    verifyNoInteractions(profileService);
  }

  @Test
  void unknownUser_is404WithItsOwnCode() throws Exception {
    when(profileService.analyze("ghost123")).thenThrow(new UserNotFoundException("ghost123"));

    mockMvc
        .perform(get("/api/v1/users/ghost123"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ghost123")));
  }
}
