package com.whv.tracker.calc;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 예상 완주일 (PRD 4.10a). 최근 28일 동안 인정된 일수로 속도를 잡고, 남은 일수를 그 속도로 나눈다.
 * 순수 계산, DB와 무관.
 *
 * @param finishDate  지금 속도로 목표를 채우는 날
 * @param weeklyDays  최근 4주 평균 주당 인정 일수 (소수 첫째 자리)
 * @param afterExpiry 예상일이 비자 만료일보다 늦음 (만료일을 모르면 false)
 */
public record Forecast(LocalDate finishDate, BigDecimal weeklyDays, boolean afterExpiry) {

    /** 속도를 재는 기간 (일). */
    public static final int WINDOW_DAYS = 28;

    /**
     * @param recentCredited 최근 28일(오늘 포함) 동안 인정된 일수
     * @param neededDays     목표까지 더 필요한 인정 일수
     * @param expiry         비자 만료일 (모르면 null)
     * @return 계산할 수 없으면 null (최근 기록이 없거나, 이미 다 채움)
     */
    public static Forecast of(BigDecimal recentCredited, long neededDays, LocalDate today, LocalDate expiry) {
        if (neededDays <= 0 || recentCredited == null || recentCredited.signum() <= 0) {
            return null;
        }
        // 남은 일수 ÷ (최근 인정 일수 ÷ 28일) = 걸리는 달력일. 올림해서 하루라도 모자라지 않게 한다.
        double perDay = recentCredited.doubleValue() / WINDOW_DAYS;
        long days = (long) Math.ceil(neededDays / perDay);
        LocalDate finish = today.plusDays(days);
        BigDecimal weekly = recentCredited.multiply(BigDecimal.valueOf(7))
                .divide(BigDecimal.valueOf(WINDOW_DAYS), 1, java.math.RoundingMode.HALF_UP);
        boolean late = expiry != null && finish.isAfter(expiry);
        return new Forecast(finish, weekly, late);
    }
}
