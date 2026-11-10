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
}
