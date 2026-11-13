package com.whv.tracker.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 예상 완주일 (PRD 4.10a). */
class ForecastTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    @Test
    void finishDateFromRecentPace() {
        // 최근 28일에 14일 인정 = 하루 0.5일. 20일 더 필요 → 40일 뒤.
        Forecast f = Forecast.of(new BigDecimal("14"), 20, TODAY, LocalDate.of(2027, 6, 1));
        assertThat(f.finishDate()).isEqualTo(TODAY.plusDays(40));
        assertThat(f.weeklyDays()).isEqualByComparingTo("3.5");
        assertThat(f.afterExpiry()).isFalse();
    }

    @Test
    void roundsUpPartialDay() {
        // 하루 0.25일(7/28), 1일 더 필요 → 4일 뒤. 3일이면 0.75일뿐이라 모자란다.
        assertThat(Forecast.of(new BigDecimal("7"), 1, TODAY, null).finishDate()).isEqualTo(TODAY.plusDays(4));
    }

    @Test
    void warnsWhenLaterThanExpiry() {
        Forecast f = Forecast.of(new BigDecimal("7"), 60, TODAY, TODAY.plusDays(100));
        assertThat(f.finishDate()).isEqualTo(TODAY.plusDays(240));
        assertThat(f.afterExpiry()).isTrue();
    }

    @Test
    void hiddenWithoutRecentWorkOrWhenDone() {
        assertThat(Forecast.of(BigDecimal.ZERO, 30, TODAY, null)).isNull();
        assertThat(Forecast.of(new BigDecimal("10"), 0, TODAY, null)).isNull();
    }
}
