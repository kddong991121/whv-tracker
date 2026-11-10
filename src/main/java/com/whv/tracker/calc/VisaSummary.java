package com.whv.tracker.calc;

import java.math.BigDecimal;
import java.util.List;

/**
 * 차수 하나의 누적 결과.
 *
 * @param rawTotal        중복 제거·상한·2019 기준 적용 전 원시 합계
 * @param employmentDays  모드 A 달력일 (무급 휴무 차감 후)
 * @param casualConverted 모드 B 환산 일수 (상한·중복 제거 후)
 * @param total           모드 A + 모드 B 합계 원값
 * @param span            인정일 첫날~마지막날 달력일 수 (없으면 0)
 * @param shortDays       짧게 일한 날 수 (평소 시간 이상 근무가 없는 날짜, 합계에 넣지 않음)
 * @param warnings        경고 문구 목록
 */
public record VisaSummary(int visaYear, BigDecimal rawTotal, long employmentDays,
                          BigDecimal casualConverted, BigDecimal total, long span,
                          int shortDays, List<String> warnings) {

    public int target() {
        return WorkCalculator.targetFor(visaYear);
    }

    public boolean targetMet() {
        return total.compareTo(BigDecimal.valueOf(target())) >= 0;
    }

    public boolean spanMet() {
        return span >= target();
    }

    /** 요건 충족 가능 표시 조건: 합계와 span 두 조건 모두 충족 (PRD 4.6). */
    public boolean eligible() {
        return targetMet() && spanMet();
    }

    /** 게이지 퍼센트 원값 (합계 ÷ 목표 × 100). 표시할 때만 반올림한다. */
    public BigDecimal percent() {
        return total.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(target()), 10, java.math.RoundingMode.HALF_UP);
    }

    /** 홈 화면 표시용: 인정 일수 합계를 정수로 내림 (PRD 4.2.4). */
    public long displayDays() {
        return total.setScale(0, java.math.RoundingMode.FLOOR).longValue();
    }

    /** 목표까지 남은 일수 (정수, 0 미만이면 0). */
    public long remainingDays() {
        return Math.max(0, target() - displayDays());
    }

    /** 화면 표시용 문자열 (소수 첫째 자리 half-up). */
    public String totalText() {
        return WorkCalculator.format1(total).toPlainString();
    }

    public String percentText() {
        return WorkCalculator.format1(percent()).toPlainString();
    }

    /** 게이지 막대 너비 (0~100 정수, 표시용). */
    public int barPercent() {
        return Math.min(100, percent().setScale(0, java.math.RoundingMode.HALF_UP).intValue());
    }

    public String rawTotalText() {
        return WorkCalculator.format1(rawTotal).toPlainString();
    }

    public String casualConvertedText() {
        return WorkCalculator.format1(casualConverted).toPlainString();
    }
}
