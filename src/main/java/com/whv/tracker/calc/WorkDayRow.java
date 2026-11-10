package com.whv.tracker.calc;

import java.time.LocalDate;

/** 모드 B 근무일 1행. standardDays는 이 행이 저장될 때의 기준 주당 근무일 W. */
public record WorkDayRow(LocalDate date, int standardDays) {
}
