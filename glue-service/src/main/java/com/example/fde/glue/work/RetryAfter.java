package com.example.fde.glue.work;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class RetryAfter {
  public static long millis(String value, Clock clock) {
    if (value == null) {
      return 0;
    }
    try {
      return Math.min(60000, Math.max(0, Math.multiplyExact(Long.parseLong(value.trim()), 1000)));
    } catch (RuntimeException ignored) {
      try {
        return Math.min(
            60000,
            Math.max(
                0,
                Duration.between(
                        clock.instant(),
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant())
                    .toMillis()));
      } catch (RuntimeException invalid) {
        return 0;
      }
    }
  }

  private RetryAfter() {}
}
