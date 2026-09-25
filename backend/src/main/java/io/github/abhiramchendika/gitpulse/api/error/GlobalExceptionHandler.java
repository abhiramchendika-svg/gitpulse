package io.github.abhiramchendika.gitpulse.api.error;

import io.github.abhiramchendika.gitpulse.explanation.ExplanationLimitException;
import io.github.abhiramchendika.gitpulse.explanation.ExplanationUnavailableException;
import io.github.abhiramchendika.gitpulse.explanation.ExplanationsDisabledException;
import io.github.abhiramchendika.gitpulse.explanation.UnreliableExplanationException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubApiException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubAuthenticationException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubNotFoundException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubRateLimitException;
import io.github.abhiramchendika.gitpulse.github.exception.GitHubUnavailableException;
import io.github.abhiramchendika.gitpulse.service.InvalidRequestException;
import io.github.abhiramchendika.gitpulse.service.RepositoryNotFoundException;
import io.github.abhiramchendika.gitpulse.service.UserNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converts exceptions into RFC 9457 "problem detail" JSON responses.
 *
 * <p>Every response has {@code status}, {@code title}, {@code detail} and a stable {@code code}.
 * Stack traces and internal messages are never included. Extending {@link
 * ResponseEntityExceptionHandler} gives Spring MVC's own errors (unknown route, bad parameter type)
 * the same shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(RepositoryNotFoundException.class)
  ResponseEntity<ProblemDetail> handleRepositoryNotFound(RepositoryNotFoundException e) {
    // The name was validated against a strict allow-list, so echoing it back is safe.
    return problem(
        HttpStatus.NOT_FOUND,
        ErrorCode.REPOSITORY_NOT_FOUND,
        "Repository '"
            + e.getFullName()
            + "' was not found. It may not exist, or it may be private (GitHub reports both the"
            + " same way).");
  }

  @ExceptionHandler(UserNotFoundException.class)
  ResponseEntity<ProblemDetail> handleUserNotFound(UserNotFoundException e) {
    // The name was validated against a strict allow-list, so echoing it back is safe.
    return problem(
        HttpStatus.NOT_FOUND,
        ErrorCode.USER_NOT_FOUND,
        "No GitHub user or organization named '" + e.getUsername() + "' was found.");
  }

  @ExceptionHandler(InvalidRequestException.class)
  ResponseEntity<ProblemDetail> handleInvalidRequest(InvalidRequestException e) {
    return problem(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, e.getMessage());
  }

  @ExceptionHandler(GitHubNotFoundException.class)
  ResponseEntity<ProblemDetail> handleNotFound(GitHubNotFoundException e) {
    return problem(
        HttpStatus.NOT_FOUND,
        ErrorCode.NOT_FOUND,
        "The requested GitHub resource does not exist or is not publicly accessible.");
  }

  @ExceptionHandler(GitHubRateLimitException.class)
  ResponseEntity<ProblemDetail> handleRateLimit(GitHubRateLimitException e) {
    ProblemDetail body =
        body(
            HttpStatus.TOO_MANY_REQUESTS,
            ErrorCode.RATE_LIMITED,
            "GitHub API rate limit reached. Try again later, or configure GITHUB_TOKEN for a"
                + " higher limit.");
    HttpHeaders headers = new HttpHeaders();
    Duration wait = retryAfter(e);
    if (e.getResetAt() != null) {
      body.setProperty("resetAt", e.getResetAt());
    }
    if (wait != null) {
      headers.set(HttpHeaders.RETRY_AFTER, Long.toString(wait.toSeconds()));
    }
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(headers).body(body);
  }

  @ExceptionHandler(GitHubAuthenticationException.class)
  ResponseEntity<ProblemDetail> handleAuth(GitHubAuthenticationException e) {
    log.error("GitHub rejected the configured GITHUB_TOKEN (401). Check that it is valid.");
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        ErrorCode.GITHUB_AUTH_FAILED,
        "The server's GitHub credentials were rejected. This is a server configuration problem.");
  }

  @ExceptionHandler(GitHubUnavailableException.class)
  ResponseEntity<ProblemDetail> handleUnavailable(GitHubUnavailableException e) {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        ErrorCode.GITHUB_UNAVAILABLE,
        "GitHub is currently unreachable. Please try again shortly.");
  }

  @ExceptionHandler(GitHubApiException.class)
  ResponseEntity<ProblemDetail> handleGitHubApi(GitHubApiException e) {
    log.warn("Unexpected GitHub response: HTTP {}", e.getStatus());
    return problem(
        HttpStatus.BAD_GATEWAY, ErrorCode.GITHUB_ERROR, "GitHub returned an unexpected response.");
  }

  @ExceptionHandler(ExplanationsDisabledException.class)
  ResponseEntity<ProblemDetail> handleExplanationsDisabled(ExplanationsDisabledException e) {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        ErrorCode.AI_NOT_CONFIGURED,
        "Explanations are not enabled on this server (no Anthropic API key is configured).");
  }

  @ExceptionHandler(ExplanationLimitException.class)
  ResponseEntity<ProblemDetail> handleExplanationLimit(ExplanationLimitException e) {
    ProblemDetail body =
        body(
            HttpStatus.TOO_MANY_REQUESTS,
            ErrorCode.AI_LIMIT_REACHED,
            "This server's hourly limit for explanations has been reached. Try again later.");
    body.setProperty("resetAt", e.getResetAt());
    Duration wait = Duration.between(Instant.now(), e.getResetAt());
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(0, wait.toSeconds())));
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(headers).body(body);
  }

  @ExceptionHandler(ExplanationUnavailableException.class)
  ResponseEntity<ProblemDetail> handleExplanationUnavailable(ExplanationUnavailableException e) {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        ErrorCode.AI_UNAVAILABLE,
        "The AI service is unavailable right now. The rest of the dashboard is unaffected.");
  }

  @ExceptionHandler(UnreliableExplanationException.class)
  ResponseEntity<ProblemDetail> handleUnreliableExplanation(UnreliableExplanationException e) {
    log.info("Explanation rejected: {}", e.getMessage());
    return problem(
        HttpStatus.BAD_GATEWAY,
        ErrorCode.AI_UNRELIABLE,
        "GitPulse could not produce an explanation that matches the numbers, so none is shown.");
  }

  /** Last resort: log the details server-side, return nothing internal to the client. */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
    log.error("Unhandled exception", e);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCode.INTERNAL_ERROR,
        "An unexpected error occurred.");
  }

  /** Path/query parameters that failed {@code @Pattern} etc.: name the offending parameters. */
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String parameters =
        ex.getParameterValidationResults().stream()
            .map(result -> result.getMethodParameter().getParameterName())
            .distinct()
            .collect(Collectors.joining(", "));
    ProblemDetail body =
        body(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, "Invalid value for: " + parameters);
    return handleExceptionInternal(ex, body, headers, status, request);
  }

  /** Adds our {@code code} field to Spring MVC's own errors (unknown route, bad parameter...). */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    // Spring often passes body == null here and builds the ProblemDetail inside super,
    // so add the code to the finished response rather than to the incoming body.
    ResponseEntity<Object> response =
        super.handleExceptionInternal(ex, body, headers, statusCode, request);
    if (response != null
        && response.getBody() instanceof ProblemDetail problem
        && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
      problem.setProperty("code", codeFor(statusCode).name());
    }
    return response;
  }

  private static ErrorCode codeFor(HttpStatusCode statusCode) {
    if (statusCode.value() == 404) {
      return ErrorCode.NOT_FOUND;
    }
    return statusCode.is4xxClientError() ? ErrorCode.INVALID_INPUT : ErrorCode.INTERNAL_ERROR;
  }

  private static ResponseEntity<ProblemDetail> problem(
      HttpStatus status, ErrorCode code, String detail) {
    return ResponseEntity.status(status).body(body(status, code, detail));
  }

  private static ProblemDetail body(HttpStatus status, ErrorCode code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("code", code.name());
    return problem;
  }

  /** Prefer GitHub's explicit Retry-After; otherwise derive it from the reset time. */
  private static Duration retryAfter(GitHubRateLimitException e) {
    if (e.getRetryAfter() != null) {
      return e.getRetryAfter();
    }
    if (e.getResetAt() != null) {
      Duration untilReset = Duration.between(Instant.now(), e.getResetAt());
      return untilReset.isNegative() ? Duration.ZERO : untilReset;
    }
    return null;
  }
}
