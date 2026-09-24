package io.github.abhiramchendika.gitpulse.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * File activity costs one GitHub request per sampled commit, so its limits are configured
 * separately, bound from {@code gitpulse.file-activity.*}.
 *
 * @param anonymousMaxSample largest sample without a token (60 requests/hour in total)
 * @param defaultSample sample size with a token when the client does not ask for one
 * @param maxSample largest sample with a token
 * @param parallelRequests commit details fetched at the same time; kept low to stay clear of
 *     GitHub's secondary (burst) rate limits
 * @param listLimit entries per ranked list in the response
 */
@Validated
@ConfigurationProperties(prefix = "gitpulse.file-activity")
public record FileActivityProperties(
    @Min(1) @Max(60) int anonymousMaxSample,
    @Min(1) int defaultSample,
    @Min(1) @Max(1000) int maxSample,
    @Min(1) @Max(10) int parallelRequests,
    @Min(1) int listLimit) {}
