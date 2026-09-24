package io.github.abhiramchendika.gitpulse.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Small numeric helpers shared by analyzers. */
public final class Numbers {

  private Numbers() {}

  /**
   * Rounds half-up to the given number of decimal places (avoids binary floating-point surprises).
   */
  public static double round(double value, int places) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return 0;
    }
    return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
  }

  /** {@code part / whole * 100}, rounded to one decimal; 0 when {@code whole} is 0. */
  public static double percent(double part, double whole) {
    return whole == 0 ? 0 : round(part * 100.0 / whole, 1);
  }
}
