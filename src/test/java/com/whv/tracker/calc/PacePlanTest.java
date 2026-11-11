package com.whv.tracker.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** F5 필수 계산. */
class PacePlanTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    /** start부터 days일 동안 정규 근무한 세컨 누적. */
    private static VisaSummary worked(LocalDate start, int days) {
        return WorkCalculator.summarize(2,
                List.of(new EmploymentPeriod(1, 2, start, start.plusDays(days - 1), 0, true)), List.of());
    }

    @Test
    void weeklyNeeded_isNeededDaysOverRemainingTimesSeven() {
        // 58일 인정, 30일 더 필요. 만료일 70일 뒤, 버퍼 0 → R = 70, 30 ÷ 70 × 7 = 3.0
        PacePlan p = PacePlan.of(TODAY.plusDays(70), 0, TODAY, worked(TODAY.minusDays(58), 58));

        assertThat(p.remainingDays()).isEqualTo(70);
        assertThat(p.neededDays()).isEqualTo(30);
        assertThat(p.weeklyNeeded()).isEqualByComparingTo("3.0");
        assertThat(p.banner()).isEqualTo(90);
    }

    @Test
    void bufferMovesTargetDate_andZeroRemainingCannotBeCalculated() {
        PacePlan p = PacePlan.of(TODAY.plusDays(14), 14, TODAY, worked(TODAY.minusDays(10), 10));

        assertThat(p.targetDate()).isEqualTo(TODAY);
        assertThat(p.weeklyNeeded()).isNull();
    }

    @Test
    void expiredAndGoalMet_replaceBanner() {
        assertThat(PacePlan.of(TODAY.minusDays(1), 0, TODAY, worked(TODAY.minusDays(30), 10)).expired()).isTrue();
        PacePlan met = PacePlan.of(TODAY.plusDays(20), 0, TODAY, worked(TODAY.minusDays(100), 88));
        assertThat(met.goalMet()).isTrue();
        assertThat(met.banner()).isNull();
    }

    @Test
    void spanShort_whenTargetIsWithin88DaysOfFirstWorkDay() {
        // 첫 인정일 10일 전, 완료 목표일 30일 뒤 → 41일 < 88
        assertThat(PacePlan.of(TODAY.plusDays(30), 0, TODAY, worked(TODAY.minusDays(10), 10)).spanShort()).isTrue();
    }

    @Test
    void negativeBuffer_isRejected() {
        assertThatThrownBy(() -> PacePlan.of(TODAY.plusDays(30), -1, TODAY, worked(TODAY, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thirdVisa_uses179DaysTargetAndPeriod() {
        // 서드: 100일 인정, 79일 더 필요. 만료일 140일 뒤 → 79 × 7 ÷ 140 = 3.95 → 4.0
        VisaSummary third = WorkCalculator.summarize(3,
                List.of(new EmploymentPeriod(1, 3, TODAY.minusDays(100), TODAY.minusDays(1), 0, true)), List.of());
        PacePlan p = PacePlan.of(TODAY.plusDays(140), 0, TODAY, third);

        assertThat(p.neededDays()).isEqualTo(79);
        assertThat(p.weeklyNeeded()).isEqualByComparingTo("4.0");
        assertThat(p.spanShort()).isFalse();
        assertThat(third.visaName()).isEqualTo("서드");

        // 첫 근무일부터 완료 목표일까지 179일이 안 되면 기간 경고
        VisaSummary late = WorkCalculator.summarize(3,
                List.of(new EmploymentPeriod(1, 3, TODAY.minusDays(10), TODAY.minusDays(1), 0, true)), List.of());
        assertThat(PacePlan.of(TODAY.plusDays(100), 0, TODAY, late).spanShort()).isTrue();
    }

    @Test
    void workDaysPerWeek_andNotAchievable() {
        // 30일 더 필요, 70일 남음 → 출근 30 × 5 ÷ 70 = 2.14 → 3일 (올림)
        PacePlan ok = PacePlan.of(TODAY.plusDays(70), 0, TODAY, worked(TODAY.minusDays(58), 58));
        assertThat(ok.achievable()).isTrue();
        assertThat(ok.workDaysPerWeek()).isEqualTo(3);

        // 87일 더 필요, 17일 남음 → 매일 일해도 불가능, 최소 13주
        PacePlan no = PacePlan.of(TODAY.plusDays(17), 0, TODAY, worked(TODAY.minusDays(1), 1));
        assertThat(no.achievable()).isFalse();
        assertThat(no.workDaysPerWeek()).isNull();
        assertThat(no.minWeeks()).isEqualTo(13);
    }
}
