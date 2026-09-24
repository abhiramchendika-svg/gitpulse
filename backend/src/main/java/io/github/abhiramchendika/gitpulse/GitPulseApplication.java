package io.github.abhiramchendika.gitpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GitPulseApplication {

  public static void main(String[] args) {
    SpringApplication.run(GitPulseApplication.class, args);
  }
}
