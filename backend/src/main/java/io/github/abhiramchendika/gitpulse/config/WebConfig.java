package io.github.abhiramchendika.gitpulse.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the API: only the configured frontend origins may call it from a browser.
 *
 * <p>This applies in local development too: the Vite dev server proxies {@code /api} but forwards
 * the browser's {@code Origin} header, so the backend still sees a cross-origin request. POST is
 * allowed for the one action that costs money (explanations); everything else is GET.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

  private final List<String> allowedOrigins;

  public WebConfig(@Value("${gitpulse.cors.allowed-origins}") List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins(allowedOrigins.toArray(String[]::new))
        .allowedMethods("GET", "POST");
  }
}
