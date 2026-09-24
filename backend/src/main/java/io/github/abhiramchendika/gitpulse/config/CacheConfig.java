package io.github.abhiramchendika.gitpulse.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.boot.cache.autoconfigure.CacheManagerCustomizer;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache lifetimes that differ from the default ({@code spring.cache.caffeine.spec}: 10 minutes).
 *
 * <p>A commit's content is identified by its SHA and can never change, so commit details can be
 * kept much longer than activity data, which goes stale as new commits arrive. The size bound keeps
 * memory predictable: a few thousand small records.
 */
@Configuration(proxyBeanMethods = false)
public class CacheConfig {

  static final String COMMIT_DETAILS = "commitDetails";

  @Bean
  CacheManagerCustomizer<CaffeineCacheManager> longLivedCaches() {
    return manager ->
        manager.registerCustomCache(
            COMMIT_DETAILS,
            Caffeine.newBuilder()
                .maximumSize(5_000)
                .expireAfterWrite(Duration.ofHours(24))
                .build());
  }
}
