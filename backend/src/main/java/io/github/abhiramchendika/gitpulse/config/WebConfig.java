package io.github.abhiramchendika.gitpulse.config;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web settings: CORS for the API, and caching for the built frontend when the backend serves it (in
 * the Docker image, the frontend is packaged under {@code classpath:/static}).
 *
 * <p>CORS applies in local development too: the Vite dev server proxies {@code /api} but forwards
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

  /**
   * Vite puts a content hash in every file name under {@code assets/}, so a given URL never changes
   * content: browsers may keep it for a year. Everything else, notably {@code index.html}, is
   * revalidated on each visit (see {@code spring.web.resources.cache} in application.yml), so a new
   * deployment is picked up immediately.
   */
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/assets/**")
        .addResourceLocations("classpath:/static/assets/")
        .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
  }
}
