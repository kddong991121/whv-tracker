package com.whv.tracker.calc;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * specified work 누적 계산 (순수 Java, DB와 무관).
 *
 * <p>규칙 근거: PRD 4.3~4.6, 4.9. 모드 A는 기간 달력일, 모드 B는 근무일 × 7 ÷ W 환산.
 * 내부 계산은 반올림하지 않고, 표시할 때만 소수 첫째 자리 half-up으로 반올림한다.
 */
public final class WorkCalculator {

    /** 2차 비자 목표 달력일 (PRD 6절 Minimum period). */
    public static final int TARGET_SECOND_VISA = 88;
    /** 3차 비자 목표 달력일 (PRD 6절 Minimum period). */
    public static final int TARGET_THIRD_VISA = 179;
    /** 3차 인정 시작일. 이 날짜 이전 근무는 3차에서 불인정 (원문 Circumstances 절). */
    public static final LocalDate THIRD_VISA_CUTOFF = LocalDate.of(2019, 7, 1);
    /** 기준 주당 정상 근무일 수 기본값 (PRD 4.4). */
    public static final int DEFAULT_STANDARD_DAYS = 5;

    // 나눗셈 자릿수만 제한한다. 7 ÷ 3 같은 무한소수를 위한 것이고, 표시 반올림과는 무관하다.
    private static final MathContext DIVISION = new MathContext(20, RoundingMode.HALF_UP);

    private WorkCalculator() {
    }

    /** 양 끝 포함 달력일 수. 시작일 = 종료일이면 1일. */
    public static long calendarDays(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    /** 근무일 수 × 7 ÷ W. 근무일은 정수 또는 평균 소수 (Karla 검산용). */
    public static BigDecimal convert(BigDecimal workDays, int standardDays) {
        return workDays.multiply(BigDecimal.valueOf(7)).divide(BigDecimal.valueOf(standardDays), DIVISION);
    }

    /** 표시용 소수 첫째 자리 half-up. 저장과 누적 계산에는 쓰지 않는다. */
    public static BigDecimal format1(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    public static int targetFor(int visaYear) {
        return visaYear == 3 ? TARGET_THIRD_VISA : TARGET_SECOND_VISA;
    }

    /**
     * 차수 하나의 누적을 계산한다.
     * 교차 중복: 모드 A 기간에 든 날짜의 모드 B 행은 환산에서 뺀다.
     * 날짜 중복: 같은 날짜는 고용주가 몇 곳이든 1일을 넘지 않는다. 평소 시간 이상인 근무가 없으면 짧게 일한 날.
     * 상한: 모드 B 기록 하나의 환산 합계는 그 기록 기간의 달력일을 넘지 않는다.
     */
    public static VisaSummary summarize(int visaYear, List<EmploymentPeriod> employment, List<CasualRecord> casual) {
        List<String> warnings = new ArrayList<>();
        LocalDate cutoff = visaYear == 3 ? THIRD_VISA_CUTOFF : LocalDate.MIN;

        // 1) 모드 A: 기간 날짜를 모아 같은 날짜는 1일로 센다. 무급 휴무는 차감한다.
        Set<LocalDate> employmentDates = new HashSet<>();
        long rawEmployment = 0;
        long unpaid = 0;
        int excludedCountable = 0;
        for (EmploymentPeriod p : employment) {
            if (p.visaYear() != visaYear) {
                continue;
            }
            rawEmployment += calendarDays(p.start(), p.end());
            if (!p.countable()) {
                excludedCountable++;
                continue;
            }
            unpaid += p.unpaidDays();
            LocalDate from = max(p.start(), cutoff);
            for (LocalDate d = from; !d.isAfter(p.end()); d = d.plusDays(1)) {
                employmentDates.add(d);
            }
        }
        long employmentDays = Math.max(0, employmentDates.size() - unpaid);

        // 2) 일한 날: 같은 날짜의 근무 행을 고용주와 상관없이 모아 하루 단위로 판정한다 (PRD 4.2.3).
        //    근무 하나라도 평소 시간 이상이면 인정(1일 × 7 ÷ W), 모두 짧으면 짧게 일한 날로 따로 센다.
        //    인정된 날짜는 평소 시간 이상인 첫 행의 기록에 귀속한다 (기록별 상한 계산용).
        Map<LocalDate, WorkDayRow> sureRow = new HashMap<>();
        Map<LocalDate, CasualRecord> sureRecord = new HashMap<>();
        Set<LocalDate> touched = new HashSet<>();
        BigDecimal rawCasual = BigDecimal.ZERO;
        int beforeCutoff = 0;
        int overlapWithEmployment = 0;
        int excludedCasual = 0;
        for (CasualRecord r : casual) {
            if (r.visaYear() != visaYear) {
                continue;
            }
            if (!r.countable()) {
                excludedCasual++;
                continue;
            }
            for (WorkDayRow row : r.rows()) {
                rawCasual = rawCasual.add(value(r, row));
                LocalDate d = row.date();
                if (d.isBefore(cutoff)) {
                    beforeCutoff++;
                    continue;
                }
                if (employmentDates.contains(d)) {
                    overlapWithEmployment++;
                    continue;
                }
                touched.add(d);
                if (row.full() && !sureRow.containsKey(d)) {
                    sureRow.put(d, row);
                    sureRecord.put(d, r);
                }
            }
        }

        LocalDate first = employmentDates.isEmpty() ? null : Collections.min(employmentDates);
        LocalDate last = employmentDates.isEmpty() ? null : Collections.max(employmentDates);
        Map<CasualRecord, BigDecimal> perRecord = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, WorkDayRow> e : sureRow.entrySet()) {
            CasualRecord r = sureRecord.get(e.getKey());
            perRecord.merge(r, value(r, e.getValue()), BigDecimal::add);
            LocalDate d = e.getKey();
            first = (first == null || d.isBefore(first)) ? d : first;
            last = (last == null || d.isAfter(last)) ? d : last;
        }
        int shortDays = touched.size() - sureRow.size();

        BigDecimal casualSum = BigDecimal.ZERO;
        for (Map.Entry<CasualRecord, BigDecimal> e : perRecord.entrySet()) {
            CasualRecord r = e.getKey();
            BigDecimal recordSum = e.getValue();
            BigDecimal periodDays = BigDecimal.valueOf(calendarDays(r.start(), r.end()));
            if (recordSum.compareTo(periodDays) > 0) {
                warnings.add("기록 #" + r.id() + ": 환산 " + format1(recordSum).toPlainString()
                        + "일이 고용 기간 " + periodDays.toPlainString() + "달력일을 넘어 " + periodDays.toPlainString()
                        + "일로 제한했습니다.");
                recordSum = periodDays;
            }
            casualSum = casualSum.add(recordSum);
        }

        if (beforeCutoff > 0) {
            warnings.add("2019-07-01 이전 근무 " + beforeCutoff + "건은 3차에서 불인정으로 제외했습니다.");
        }
        if (overlapWithEmployment > 0) {
            warnings.add("모드 A 기간과 겹친 모드 B 근무일 " + overlapWithEmployment + "일은 모드 A 달력일로만 계산했습니다.");
        }
        if (excludedCountable + excludedCasual > 0) {
            warnings.add("세컨 인정 지역·업무로 확인되지 않은 기록 " + (excludedCountable + excludedCasual)
                    + "건은 합계에서 뺐습니다. 아래 기록 목록에서 이유를 확인하세요.");
        }

        BigDecimal total = BigDecimal.valueOf(employmentDays).add(casualSum);
        long span = first == null ? 0 : calendarDays(first, last);
        BigDecimal rawTotal = BigDecimal.valueOf(rawEmployment).add(rawCasual);
        return new VisaSummary(visaYear, rawTotal, employmentDays, casualSum, total, span, first, shortDays, warnings);
    }

    /** 행 하나의 값: 봉사는 1일, 그 외는 1 × 7 ÷ W. */
    private static BigDecimal value(CasualRecord r, WorkDayRow row) {
        return r.volunteer() ? BigDecimal.ONE : convert(BigDecimal.ONE, row.standardDays());
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
