package io.github.abhiramchendika.gitpulse.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DurationsTest {

  private static List<Duration> hours(int... values) {
    return IntStream.of(values).mapToObj(Duration::ofHours).toList();
  }

  @Test
  void empty_isNullNotZero() {
    assertThat(Durations.summarise(List.of())).isNull();
  }

  @Test
  void oddCount_medianIsMiddleValue() {
    Durations.Summary s = Durations.summarise(hours(5, 1, 3));

    assertThat(s.count()).isEqualTo(3);
    assertThat(s.medianHours()).isEqualTo(3.0);
  }

  @Test
  void evenCount_medianIsMeanOfMiddlePair() {
    assertThat(Durations.summarise(hours(1, 2, 3, 10)).medianHours()).isEqualTo(2.5);
  }

  @Test
  void p90_usesNearestRank() {
    // 10 values 1..10: ceil(0.9 * 10) = 9th smallest = 9.
    assertThat(Durations.summarise(hours(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)).p90Hours()).isEqualTo(9.0);
    // 3 values: ceil(2.7) = 3rd = the maximum.
    assertThat(Durations.summarise(hours(1, 2, 100)).p90Hours()).isEqualTo(100.0);
  }

  @Test
  void medianIgnoresAnOutlierThatWouldWreckTheMean() {
    // Mean would be ~146 hours; the median stays at the typical value.
    Durations.Summary s = Durations.summarise(hours(2, 3, 4, 5, 720));

    assertThat(s.medianHours()).isEqualTo(4.0);
  }
}
