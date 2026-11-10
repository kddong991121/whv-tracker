package com.whv.tracker.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.whv.tracker.eligibility.EligibilityResult.Status;
import com.whv.tracker.ledger.DisasterWorkType;
import com.whv.tracker.ledger.Industry;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** F2 필수 판정. 실제 시드 CSV를 읽는다 (Spring 없이). */
class EligibilityServiceTest {

    private static final EligibilityService SERVICE = new EligibilityService(new PostcodeRegistry());
    private static final Industry FARM = Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private static EligibilityResult check(String postcode, Industry industry) {
        return SERVICE.check(postcode, industry, null, null, TODAY);
    }

    @Test
    void listedPostcode_isEligible() {
        assertThat(check("4870", FARM).status()).isEqualTo(Status.ELIGIBLE);
    }

    @Test
    void unlistedPostcode_isNotListed() {
        // 2000 (시드니 도심)은 표 1~6 어디에도 없다
        assertThat(check("2000", FARM).status()).isEqualTo(Status.NOT_LISTED);
    }

    @Test
    void stateWideAllRow_matchesPostcodeMissingFromCsv() {
        // 5000 (애들레이드)은 CSV에 없지만 번호대로 SA → 표 4 SA 전체(ALL) 행에 일치
        EligibilityResult r = check("5000", FARM);
        assertThat(r.states()).containsExactly("SA");
        assertThat(r.status()).isEqualTo(Status.ELIGIBLE);
        assertThat(check("5000", Industry.TOURISM_HOSPITALITY).status()).isEqualTo(Status.NOT_LISTED);
    }

    @Test
    void manufacturingWork_isNotSpecifiedWork_evenInListedArea() {
        EligibilityResult r = SERVICE.check("4870", FARM, WorkType.MANUFACTURING, null, TODAY);
        assertThat(r.status()).isEqualTo(Status.NOT_SPECIFIED_WORK);
    }

    @Test
    void postcodeInSeveralStates_listsAllStates() {
        assertThat(check("0872", Industry.NATURAL_DISASTER_RECOVERY).multipleStates()).isTrue();
    }

    @Test
    void lodgeDateBeforeTableCondition_isConditionNotMet() {
        // 표 2는 2022-07-01 이후 신청만 인정
        EligibilityResult r = SERVICE.check("4406", Industry.TOURISM_HOSPITALITY, null, null, LocalDate.of(2022, 6, 30));
        assertThat(r.status()).isEqualTo(Status.CONDITION_NOT_MET);
    }

    @Test
    void disasterRecovery_withoutPaidOrVolunteer_isConditional() {
        assertThat(check("2011", Industry.NATURAL_DISASTER_RECOVERY).status()).isEqualTo(Status.CONDITIONAL);
        assertThat(SERVICE.check("2011", Industry.NATURAL_DISASTER_RECOVERY, null, DisasterWorkType.PAID, TODAY)
                .status()).isEqualTo(Status.ELIGIBLE);
    }

    @Test
    void invalidPostcode_isRejected() {
        assertThatThrownBy(() -> check("48a0", FARM)).isInstanceOf(IllegalArgumentException.class);
    }
}
