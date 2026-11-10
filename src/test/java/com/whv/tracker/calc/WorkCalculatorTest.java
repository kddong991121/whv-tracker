package com.whv.tracker.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PRD 9절 F1 경계값 표를 순수 계산 로직으로 검증한다. 날짜는 2027년 기준 가정. */
class WorkCalculatorTest {

    private static final LocalDate JAN1 = LocalDate.of(2027, 1, 1);

    private static EmploymentPeriod period(long id, int visa, LocalDate s, LocalDate e, int unpaid) {
        return new EmploymentPeriod(id, visa, s, e, unpaid, true);
    }

    /** start부터 n개의 연속 날짜 행을 만든다. */
    private static List<WorkDayRow> rows(LocalDate start, int n, int w) {
        List<WorkDayRow> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new WorkDayRow(start.plusDays(i), w));
        }
        return list;
    }

    private static CasualRecord casual(long id, int visa, LocalDate s, LocalDate e, List<WorkDayRow> rows) {
        return new CasualRecord(id, visa, s, e, true, false, rows);
    }

    private static String fmt(BigDecimal v) {
        return WorkCalculator.format1(v).toPlainString();
    }

    @Test
    void modeA_88CalendarDays_meetsSecondVisa() {
        // 2027-01-01 ~ 2027-03-29 = 31 + 28 + 29 = 88 (주말 포함 전부 산입)
        VisaSummary s = WorkCalculator.summarize(2,
                List.of(period(1, 2, JAN1, LocalDate.of(2027, 3, 29), 0)), List.of());

        assertThat(s.employmentDays()).isEqualTo(88);
        assertThat(s.span()).isEqualTo(88);
        assertThat(s.eligible()).isTrue();
    }

    @Test
    void modeA_87CalendarDays_notEligible() {
        VisaSummary s = WorkCalculator.summarize(2,
                List.of(period(1, 2, JAN1, LocalDate.of(2027, 3, 28), 0)), List.of());

        assertThat(s.employmentDays()).isEqualTo(87);
        assertThat(s.targetMet()).isFalse();
        assertThat(s.eligible()).isFalse();
    }

    @Test
    void modeA_unpaidWeatherDaysAreSubtracted() {
        VisaSummary s = WorkCalculator.summarize(2,
                List.of(period(1, 2, JAN1, JAN1.plusDays(9), 2)), List.of());

        assertThat(s.employmentDays()).isEqualTo(8);
    }

    @Test
    void modeA_sameStartAndEnd_isOneDay() {
        VisaSummary s = WorkCalculator.summarize(2, List.of(period(1, 2, JAN1, JAN1, 0)), List.of());

        assertThat(s.employmentDays()).isEqualTo(1);
    }

    @Test
    void modeA_gapBetweenRecordsIsNotCounted() {
        // 10일 고용, 공백 10일, 다시 10일 고용: 합계 20 (공백 제외)
        VisaSummary s = WorkCalculator.summarize(2, List.of(
                period(1, 2, JAN1, JAN1.plusDays(9), 0),
                period(2, 2, JAN1.plusDays(20), JAN1.plusDays(29), 0)), List.of());

        assertThat(s.employmentDays()).isEqualTo(20);
    }

    @Test
    void modeB_sevenRows_w5_is98() {
        VisaSummary s = WorkCalculator.summarize(2, List.of(),
                List.of(casual(1, 2, JAN1, JAN1.plusDays(30), rows(JAN1, 7, 5))));

        assertThat(fmt(s.casualConverted())).isEqualTo("9.8");
    }

    @Test
    void modeB_threeRows_w5_is42() {
        VisaSummary s = WorkCalculator.summarize(2, List.of(),
                List.of(casual(1, 2, JAN1, JAN1.plusDays(30), rows(JAN1, 3, 5))));

        assertThat(fmt(s.casualConverted())).isEqualTo("4.2");
    }

    @Test
    void modeB_25RowsOver88DayPeriod_is35_0() {
        VisaSummary s = WorkCalculator.summarize(2, List.of(),
                List.of(casual(1, 2, JAN1, JAN1.plusDays(87), rows(JAN1, 25, 5))));

        assertThat(fmt(s.casualConverted())).isEqualTo("35.0");
        assertThat(s.targetMet()).isFalse();
    }

    @Test
    void modeB_karlaExample_averageInput_is61_0() {
        // 122 × 5 ÷ 14 = 43.5714… 를 입력하면 61.0 (원문 예시와 일치). 43.57 입력은 60.998이 되어도 표시는 61.0.
        assertThat(fmt(WorkCalculator.convert(new BigDecimal("43.57"), 5))).isEqualTo("61.0");
    }

    @Test
    void modeB_karlaIntegerInputs_showApproximationError() {
        assertThat(fmt(WorkCalculator.convert(BigDecimal.valueOf(43), 5))).isEqualTo("60.2");
        assertThat(fmt(WorkCalculator.convert(BigDecimal.valueOf(44), 5))).isEqualTo("61.6");
    }

    @Test
    void modeB_capAtPeriodCalendarDays_withWarning() {
        // 10일 기간에 10행 (W=5): 원시 14 → 10으로 cap, 경고
        VisaSummary s = WorkCalculator.summarize(2, List.of(),
                List.of(casual(1, 2, JAN1, JAN1.plusDays(9), rows(JAN1, 10, 5))));

        assertThat(fmt(s.casualConverted())).isEqualTo("10.0");
        assertThat(s.warnings()).anyMatch(w -> w.contains("제한"));
    }

    @Test
    void modeB_belowCap_noCap() {
        // 14일 기간, 6행: 8.4 그대로
        VisaSummary s = WorkCalculator.summarize(2, List.of(),
                List.of(casual(1, 2, JAN1, JAN1.plusDays(13), rows(JAN1, 6, 5))));

        assertThat(fmt(s.casualConverted())).isEqualTo("8.4");
        assertThat(s.warnings()).noneMatch(w -> w.contains("제한"));
    }

    @Test
    void modeB_w4_oneRow_roundsHalfUpTo18() {
        // 1 × 7 ÷ 4 = 1.75 → 표시 1.8 (원값 1.75 보관)
        VisaSummary s = WorkCalculator.summarize(2, List.of(),
                List.of(casual(1, 2, JAN1, JAN1.plusDays(30), rows(JAN1, 1, 4))));

        assertThat(s.casualConverted().compareTo(new BigDecimal("1.75"))).isZero();
        assertThat(fmt(s.casualConverted())).isEqualTo("1.8");
    }

    @Test
    void sumIsRoundedOnlyAtEnd() {
        // 3행 각 1.4 → 합 4.2 (행별 반올림 후 합산이 아님)
        VisaSummary s = WorkCalculator.summarize(2, List.of(),
                List.of(casual(1, 2, JAN1, JAN1.plusDays(30), rows(JAN1, 3, 5))));

        assertThat(s.casualConverted().compareTo(new BigDecimal("4.2"))).isZero();
    }

    @Test
    void sameDayTwoEmployers_countsOnce() {
        CasualRecord a = casual(1, 2, JAN1, JAN1.plusDays(30), rows(JAN1, 1, 5));
        CasualRecord b = casual(2, 2, JAN1, JAN1.plusDays(30), rows(JAN1, 1, 5));

        VisaSummary s = WorkCalculator.summarize(2, List.of(), List.of(a, b));

        assertThat(fmt(s.casualConverted())).isEqualTo("1.4");
    }

    @Test
    void crossMode_sameDateCountedOnlyInModeA() {
        // 모드 A 10일 기간 안의 날짜가 모드 B에도 있으면 B 쪽은 제외
        VisaSummary s = WorkCalculator.summarize(2,
                List.of(period(1, 2, JAN1, JAN1.plusDays(9), 0)),
                List.of(casual(2, 2, JAN1.plusDays(4), JAN1.plusDays(4), rows(JAN1.plusDays(4), 1, 5))));

        assertThat(s.employmentDays()).isEqualTo(10);
        assertThat(s.casualConverted().signum()).isZero();
        assertThat(fmt(s.total())).isEqualTo("10.0");
    }

    @Test
    void thirdVisa_work2019_06_30_notRecognized() {
        LocalDate d = LocalDate.of(2019, 6, 30);
        VisaSummary s = WorkCalculator.summarize(3, List.of(),
                List.of(casual(1, 3, d, d.plusDays(10), rows(d, 1, 5))));

        assertThat(s.casualConverted().signum()).isZero();
        assertThat(s.warnings()).anyMatch(w -> w.contains("2019-07-01 이전"));
    }

    @Test
    void thirdVisa_work2019_07_01_recognized() {
        LocalDate d = LocalDate.of(2019, 7, 1);
        VisaSummary s = WorkCalculator.summarize(3, List.of(),
                List.of(casual(1, 3, d, d.plusDays(10), rows(d, 1, 5))));

        assertThat(fmt(s.casualConverted())).isEqualTo("1.4");
    }

    @Test
    void thirdVisa_modeA_clippedAtCutoff() {
        // 2019-06-20 ~ 2019-07-09 (20일) 중 2019-07-01 이후 9일만 인정
        VisaSummary s = WorkCalculator.summarize(3,
                List.of(period(1, 3, LocalDate.of(2019, 6, 20), LocalDate.of(2019, 7, 9), 0)), List.of());

        assertThat(s.employmentDays()).isEqualTo(9);
    }

    @Test
    void secondVisaRecord_notCountedInThirdVisa() {
        VisaSummary s = WorkCalculator.summarize(3,
                List.of(period(1, 2, JAN1, LocalDate.of(2027, 3, 29), 0)), List.of());

        assertThat(s.employmentDays()).isZero();
        assertThat(s.target()).isEqualTo(179);
    }

    @Test
    void disasterRecovery_excludedFromTotal() {
        EmploymentPeriod disaster = new EmploymentPeriod(1, 2, JAN1, JAN1.plusDays(87), 0, false);

        VisaSummary s = WorkCalculator.summarize(2, List.of(disaster), List.of());

        assertThat(s.employmentDays()).isZero();
        assertThat(s.warnings()).anyMatch(w -> w.contains("재해 복구"));
    }

    @Test
    void completion_totalMetButSpanShort_notEligible() {
        // W=1 행 13개 (13일 연속) → 환산 91 ≥ 88 이지만 span은 13 < 88 → 요건 충족 불가
        List<WorkDayRow> rows = rows(JAN1, 13, 1);
        VisaSummary s = WorkCalculator.summarize(2, List.of(),
                List.of(casual(1, 2, JAN1, JAN1.plusDays(200), rows)));

        assertThat(s.targetMet()).isTrue();
        assertThat(s.span()).isEqualTo(13);
        assertThat(s.spanMet()).isFalse();
        assertThat(s.eligible()).isFalse();
    }

    @Test
    void gauge_62of88_showsOne_decimal705() {
        // 62 ÷ 88 × 100 = 70.4545… → 표시 70.5%
        VisaSummary s = WorkCalculator.summarize(2, List.of(
                period(1, 2, JAN1, JAN1.plusDays(61), 0)), List.of());

        assertThat(s.employmentDays()).isEqualTo(62);
        assertThat(s.percentText()).isEqualTo("70.5");
        assertThat(s.targetMet()).isFalse();
    }

    @Test
    void volunteerDisasterRecovery_eachDayCountsAsOne() {
        // 봉사 재해 복구: 근무 1일 = 1일 인정 (7 ÷ W 환산 없음)
        CasualRecord volunteer = new CasualRecord(1, 2, JAN1, JAN1.plusDays(30), true, true, rows(JAN1, 3, 5));

        VisaSummary s = WorkCalculator.summarize(2, List.of(), List.of(volunteer));

        assertThat(fmt(s.casualConverted())).isEqualTo("3.0");
    }

    @Test
    void completion_bothMet_eligible() {
        VisaSummary s = WorkCalculator.summarize(2, List.of(
                period(1, 2, JAN1, JAN1.plusDays(43), 0),
                period(2, 2, JAN1.plusDays(44), JAN1.plusDays(87), 0)), List.of());

        assertThat(s.employmentDays()).isEqualTo(88);
        assertThat(s.span()).isEqualTo(88);
        assertThat(s.eligible()).isTrue();
    }

    @Test
    void multiShift_oneFullShiftMakesDaySure_andDayCountsOnce() {
        // 같은 날 A 농장 8시간(평소 이상) + B 포장 4시간(짧음) = 인정 1일, 짧게 일한 날 0
        CasualRecord a = casual(1, 2, JAN1, JAN1.plusDays(30), List.of(new WorkDayRow(JAN1, 5, true)));
        CasualRecord b = casual(2, 2, JAN1, JAN1.plusDays(30), List.of(new WorkDayRow(JAN1, 5, false)));

        VisaSummary s = WorkCalculator.summarize(2, List.of(), List.of(a, b));

        assertThat(fmt(s.casualConverted())).isEqualTo("1.4");
        assertThat(s.shortDays()).isZero();
    }

    @Test
    void onlyShortShifts_areShortDay_notCountedAndNotInSpan() {
        // 4시간 + 4시간 (둘 다 평소보다 짧음) = 짧게 일한 날. 합계와 기간 계산에서 뺀다
        CasualRecord a = casual(1, 2, JAN1, JAN1.plusDays(30), List.of(
                new WorkDayRow(JAN1, 5, false), new WorkDayRow(JAN1.plusDays(9), 5, true)));
        CasualRecord b = casual(2, 2, JAN1, JAN1.plusDays(30), List.of(new WorkDayRow(JAN1, 5, false)));

        VisaSummary s = WorkCalculator.summarize(2, List.of(), List.of(a, b));

        assertThat(s.shortDays()).isEqualTo(1);
        assertThat(fmt(s.total())).isEqualTo("1.4");
        assertThat(s.span()).isEqualTo(1);
    }

    @Test
    void displayDays_roundsDown() {
        // 42일 × 7 ÷ 5 = 58.8 → 홈 표시 58
        CasualRecord r = casual(1, 2, JAN1, JAN1.plusDays(120), rows(JAN1, 42, 5));

        VisaSummary s = WorkCalculator.summarize(2, List.of(), List.of(r));

        assertThat(fmt(s.total())).isEqualTo("58.8");
        assertThat(s.displayDays()).isEqualTo(58);
        assertThat(s.remainingDays()).isEqualTo(30);
    }
}
