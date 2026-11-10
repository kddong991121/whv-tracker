package com.whv.tracker.calc;

import java.time.LocalDate;

/**
 * 일한 날 근무 1행 (고용주 1곳의 그 날짜 근무).
 *
 * @param standardDays 이 행이 저장될 때의 기준 주당 근무일 W
 * @param full         근무 시간이 그 고용주의 평소 하루 시간 이상이면 true (PRD 4.2.3 하루 판정)
 */
public record WorkDayRow(LocalDate date, int standardDays, boolean full) {

    /** 평소 시간 이상 근무한 행. */
    public WorkDayRow(LocalDate date, int standardDays) {
        this(date, standardDays, true);
    }
}
