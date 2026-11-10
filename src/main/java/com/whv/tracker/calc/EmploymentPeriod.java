package com.whv.tracker.calc;

import java.time.LocalDate;

/**
 * 모드 A 입력: 풀타임 연속 고용 기간 1건.
 *
 * @param unpaidDays 무급 날씨 휴무 일수 (기간에서 차감)
 * @param countable  false면 재해 복구 업종 등 인정 합계에서 제외
 */
public record EmploymentPeriod(long id, int visaYear, LocalDate start, LocalDate end,
                               int unpaidDays, boolean countable) {
}
