package io.github.abhiramchendika.gitpulse.api.dto;

/**
 * Optional features this server has enabled, so the frontend can hide what it cannot use.
 *
 * @param explanations the "explain these numbers" feature (needs an Anthropic API key)
 */
public record FeaturesResponse(boolean explanations) {}
