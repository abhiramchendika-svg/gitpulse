package io.github.abhiramchendika.gitpulse.config;

import io.github.abhiramchendika.gitpulse.github.GitHubClient;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires the {@link GitHubClient}. Timeouts live here (not inside the client) so tests can swap the
 * HTTP layer for {@code MockRestServiceServer} without touching client code.
 */
@Configuration(proxyBeanMethods = false)
public class GitHubClientConfig {

  @Bean
  GitHubClient gitHubClient(RestClient.Builder builder, GitHubProperties properties) {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            // GitHub answers 301 for renamed/transferred repositories.
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.readTimeout());

    return new GitHubClient(builder.requestFactory(requestFactory), properties);
  }
}
