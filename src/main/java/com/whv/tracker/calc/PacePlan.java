package com.whv.tracker.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * F5: 1년차 만료일 기준 남은 기간과 페이스 (PRD F5). 순수 계산, DB와 무관.
 *
 * @param targetDate    완료 목표일 = 만료일 − 버퍼 일수 (사용자 목표, 규정상 기한 아님)
 * @param remainingDays 남은 달력일 R = 완료 목표일 − 오늘
 * @param neededDays    세컨 목표까지 더 필요한 인정 일수 N (정수, 최소 0)
 * @param weeklyNeeded  주당 필요량 = N ÷ R × 7 (소수 첫째 자리 half-up). R ≤ 0이면 null
 * @param banner        D-120/90/60/30 배너 단계 (해당 없으면 null)
 * @param expired       오늘이 만료일 이후
 * @param goalMet       인정 일수 ≥ 88
 * @param spanShort     완료 목표일까지 가도 첫 인정일부터 88일이 안 됨
 */
public record PacePlan(LocalDate targetDate, long remainingDays, long neededDays, BigDecimal weeklyNeeded,
                       Integer banner, boolean expired, boolean goalMet, boolean spanShort) {

    private static final int[] BANNERS = {30, 60, 90, 120};

    public static PacePlan of(LocalDate expiry, int bufferDays, LocalDate today, VisaSummary second) {
        if (bufferDays < 0) {
            throw new IllegalArgumentException("버퍼 일수는 0 이상이어야 합니다.");
        }
        LocalDate target = expiry.minusDays(bufferDays);
        long r = ChronoUnit.DAYS.between(today, target);
        long n = second.remainingDays();
        BigDecimal weekly = r <= 0 ? null : BigDecimal.valueOf(n).multiply(BigDecimal.valueOf(7))
                .divide(BigDecimal.valueOf(r), 1, RoundingMode.HALF_UP);
        Integer banner = null;
        for (int b : BANNERS) {
            if (r >= 0 && r <= b) {
                banner = b;
                break;
            }
        }
        boolean goalMet = second.targetMet();
        boolean expired = today.isAfter(expiry);
        if (goalMet || expired) {
            banner = null; // 목표 달성·만료일 경과는 배너 대신 다른 문구를 보여준다
        }
        LocalDate first = second.firstDate();
        boolean spanShort = first != null
                && WorkCalculator.calendarDays(first, target) < second.target();
        return new PacePlan(target, r, n, weekly, banner, expired, goalMet, spanShort);
    }

    public boolean targetPassed() {
        return remainingDays < 0;
    }
}
