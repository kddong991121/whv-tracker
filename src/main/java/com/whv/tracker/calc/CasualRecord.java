package com.whv.tracker.calc;

import java.time.LocalDate;
import java.util.List;

/**
 * 모드 B 입력: 고용 기간 1건과 그 안의 근무일 행들.
 *
 * @param rows      날짜 1개당 1행 (행마다 저장 시점의 W 스냅샷을 가진다)
 * @param countable false면 인정 합계에서 제외 (재해 복구 유형 미선택 등)
 * @param volunteer true면 근무 1일을 1일로 센다 (재해 복구 봉사, PRD 4.9 규칙 8)
 */
public record CasualRecord(long id, int visaYear, LocalDate start, LocalDate end,
                           boolean countable, boolean volunteer, List<WorkDayRow> rows) {
}
