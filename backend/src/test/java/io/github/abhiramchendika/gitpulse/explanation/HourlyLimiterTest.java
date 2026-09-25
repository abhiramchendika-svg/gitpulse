package io.github.abhiramchendika.gitpulse.explanation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class HourlyLimiterTest {

  /** A clock tests can move forward. */
  static final class MutableClock extends Clock {
    Instant now;

    MutableClock(Instant start) {
      now = start;
    }

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private final MutableClock clock = new MutableClock(Instant.parse("2026-09-25T10:00:00Z"));

  @Test
  void allowsMaxCallsPerRollingHour_thenReportsWhenASlotFrees() {
    HourlyLimiter limiter = new HourlyLimiter(3, clock);
    limiter.acquire(); // 10:00
    clock.advance(Duration.ofMinutes(20));
    limiter.acquire(); // 10:20
    limiter.acquire(); // 10:20

    assertThatThrownBy(limiter::acquire)
        .isInstanceOfSatisfying(
            ExplanationLimitException.class,
            e -> assertThat(e.getResetAt()).isEqualTo(Instant.parse("2026-09-25T11:00:00Z")));

    // Exactly one hour after the first call, its slot is free again.
    clock.advance(Duration.ofMinutes(40));
    assertThatCode(limiter::acquire).doesNotThrowAnyException();
    assertThatThrownBy(limiter::acquire).isInstanceOf(ExplanationLimitException.class);
  }

  @Test
  void rejectedAttemptsDoNotUseUpSlots() {
    HourlyLimiter limiter = new HourlyLimiter(1, clock);
    limiter.acquire();
    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(limiter::acquire).isInstanceOf(ExplanationLimitException.class);
    }
    clock.advance(Duration.ofHours(1));
    assertThatCode(limiter::acquire).doesNotThrowAnyException();
  }
}
