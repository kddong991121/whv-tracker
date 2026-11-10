package com.whv.tracker.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.whv.tracker.calc.VisaSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * H2(MySQL 모드, test 프로필)에 Flyway V1·V2를 적용하고 저장 계층의 검증 규칙을 확인한다.
 * 실제 MySQL 검증은 사용자 PC에서 별도로 해야 한다.
 * 테스트 메서드마다 트랜잭션을 롤백한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LedgerServiceIntegrationTest {

    private static final BigDecimal H8 = new BigDecimal("8");

    private static final LocalDate START = LocalDate.of(2027, 1, 1);

    @Autowired
    private AppUserService users;

    private AppUser owner;

    @BeforeEach
    void setUpOwner() {
        owner = users.upsertGoogleUser("f1-test-sub", "f1@example.com", "F1 테스트");
    }

    @Autowired
    private LedgerService ledger;

    @Autowired
    private WorkDayRepository days;

    @Autowired
    private WorkStandardChangeRepository changes;

    private WorkRecord casual(String employer, LocalDate start, LocalDate end) {
        return ledger.addCasual(owner, employer, "4670", Industry.TOURISM_HOSPITALITY, 2, start, end, 5, null, null, null, H8);
    }

    @Test
    void flywayMigrationsApplied_andModeAFullTimeCountsToEligible() {
        WorkRecord saved = ledger.addEmployment(owner, "농장A", "4670", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION,
                2, START, LocalDate.of(2027, 3, 29), 0, null);

        assertThat(saved.getId()).isNotNull();
        VisaSummary second = ledger.dashboard(owner).summaries().get(0);
        assertThat(second.visaYear()).isEqualTo(2);
        assertThat(second.employmentDays()).isEqualTo(88);
        assertThat(second.eligible()).isTrue();
    }

    @Test
    void sameDate_sameEmployerBlocked_otherEmployerAllowed_dayTotalOver24Blocked() {
        WorkRecord a = casual("지은 카페", START, START.plusDays(10));
        WorkRecord b = casual("다른 카페", START, START.plusDays(10));
        WorkRecord c = casual("세번째 카페", START, START.plusDays(10));
        ledger.addWorkDay(owner, a.getId(), START, new BigDecimal("16"));

        assertThatThrownBy(() -> ledger.addWorkDay(owner, a.getId(), START, new BigDecimal("1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 있습니다");
        // 하루 여러 근무: 다른 고용주의 같은 날짜 행은 허용
        ledger.addWorkDay(owner, b.getId(), START, new BigDecimal("8"));
        // 같은 날짜 합계 24시간 초과는 거부
        assertThatThrownBy(() -> ledger.addWorkDay(owner, c.getId(), START, new BigDecimal("0.5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("24시간");
    }

    @Test
    void multiShiftDay_combinedOnlyIsShortDay_andNotCounted() {
        WorkRecord a = casual("오전 농장", START, START.plusDays(10));
        WorkRecord b = casual("오후 포장", START, START.plusDays(10));
        ledger.addWorkDay(owner, a.getId(), START, new BigDecimal("4"));
        ledger.addWorkDay(owner, b.getId(), START, new BigDecimal("4"));
        ledger.addWorkDay(owner, a.getId(), START.plusDays(1), new BigDecimal("8"));

        VisaSummary second = ledger.dashboard(owner).summaries().get(0);
        assertThat(second.shortDays()).isEqualTo(1);
        assertThat(second.casualConvertedText()).isEqualTo("1.4");
        assertThat(second.displayDays()).isEqualTo(1);
    }

    @Test
    void workHours_requiredExceptVolunteer() {
        WorkRecord a = casual("시간 필수", START, START.plusDays(10));
        assertThatThrownBy(() -> ledger.addWorkDay(owner, a.getId(), START, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hoursBoundary_onOneRow_24Allowed_25Rejected() {
        WorkRecord a = casual("시간 테스트", START, START.plusDays(10));

        ledger.addWorkDay(owner, a.getId(), START, new BigDecimal("24"));
        assertThatThrownBy(() -> ledger.addWorkDay(owner, a.getId(), START.plusDays(1), new BigDecimal("25")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("24");
        assertThatThrownBy(() -> ledger.addWorkDay(owner, a.getId(), START.plusDays(1), new BigDecimal("24.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hoursInvalid_zeroNegativeOver24AndScale_areRejected() {
        WorkRecord a = casual("시간 검증", START, START.plusDays(10));

        assertThatThrownBy(() -> ledger.addWorkDay(owner, a.getId(), START, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger.addWorkDay(owner, a.getId(), START, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger.addWorkDay(owner, a.getId(), START, new BigDecimal("24.5")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger.addWorkDay(owner, a.getId(), START, new BigDecimal("8.505")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("소수 둘째");
    }

    @Test
    void workDayOutsidePeriod_isRejected() {
        WorkRecord a = casual("기간 테스트", START, START.plusDays(5));

        assertThatThrownBy(() -> ledger.addWorkDay(owner, a.getId(), START.plusDays(6), H8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reversedDates_areRejected() {
        assertThatThrownBy(() -> ledger.addEmployment(owner, "역전", "4670", Industry.TOURISM_HOSPITALITY, 2,
                START, START.minusDays(1), 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("종료일");
    }

    @Test
    void invalidRequiredFields_areRejected() {
        assertThatThrownBy(() -> ledger.addEmployment(owner, "   ", "4670", Industry.TOURISM_HOSPITALITY, 2,
                START, START, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger.addEmployment(owner, "결측 포스트", null, Industry.TOURISM_HOSPITALITY, 2,
                START, START, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger.addEmployment(owner, "결측 날짜", "4670", Industry.TOURISM_HOSPITALITY, 2,
                null, START, 0, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void standardDaysOutOfRange_areRejected() {
        assertThatThrownBy(() -> ledger.addCasual(owner, "W0", "4670", Industry.TOURISM_HOSPITALITY, 2,
                START, START.plusDays(30), 0, "사유", null, null, H8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger.addCasual(owner, "W8", "4670", Industry.TOURISM_HOSPITALITY, 2,
                START, START.plusDays(30), 8, "사유", null, null, H8)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void standardChangeWithoutReason_isRejected_andWithReasonIsLoggedOnce() {
        assertThatThrownBy(() -> ledger.addCasual(owner, "W 테스트", "4670", Industry.TOURISM_HOSPITALITY, 2,
                START, START.plusDays(30), 4, "  ", null, null, H8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사유");

        WorkRecord ok = ledger.addCasual(owner, "W 사유 테스트", "4670", Industry.TOURISM_HOSPITALITY, 2,
                START, START.plusDays(30), 4, "업종 표준 근거", "근거 문서 2026-11-10", null, H8);

        assertThat(ok.getStandardDays()).isEqualTo(4);
        assertThat(changes.count()).isEqualTo(1);
        assertThat(changes.findAll().get(0).getSourceNote()).isEqualTo("근거 문서 2026-11-10");
    }

    @Test
    void reasonOrSourceLongerThan255_areRejected() {
        String long256 = "가".repeat(256);

        assertThatThrownBy(() -> ledger.addCasual(owner, "긴 사유", "4670", Industry.TOURISM_HOSPITALITY, 2,
                START, START.plusDays(30), 4, long256, null, null, H8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger.addCasual(owner, "긴 출처", "4670", Industry.TOURISM_HOSPITALITY, 2,
                START, START.plusDays(30), 4, "사유", long256, null, H8)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameEmployerModeAOverlap_isRejected() {
        ledger.addEmployment(owner, "겹침 농장", "4670", Industry.TOURISM_HOSPITALITY, 2, START, START.plusDays(30), 0, null);

        assertThatThrownBy(() -> ledger.addEmployment(owner, "겹침 농장", "4670", Industry.TOURISM_HOSPITALITY, 2,
                START.plusDays(30), START.plusDays(40), 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("겹칩니다");
    }

    @Test
    void modeAPeriodAndModeBRow_ofSameEmployer_conflictInBothDirections() {
        // 방향 1: 모드 B 근무일이 있는 기간에 같은 고용주의 모드 A 기간을 추가하면 거부
        WorkRecord b = casual("교차 농장", START, START.plusDays(30));
        ledger.addWorkDay(owner, b.getId(), START.plusDays(5), H8);
        assertThatThrownBy(() -> ledger.addEmployment(owner, "교차 농장", "4670", Industry.TOURISM_HOSPITALITY, 2,
                START, START.plusDays(10), 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("모드 B");

        // 방향 2: 모드 A 기간 안의 날짜에 같은 고용주의 모드 B 행을 추가하면 거부
        ledger.addEmployment(owner, "교차 농장2", "4670", Industry.TOURISM_HOSPITALITY, 2,
                START.plusDays(40), START.plusDays(50), 0, null);
        WorkRecord b2 = casual("교차 농장2", START, START.plusDays(60));
        assertThatThrownBy(() -> ledger.addWorkDay(owner, b2.getId(), START.plusDays(45), H8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("모드 A");
    }

    @Test
    void employerKey_trimsAndCollapsesSpaces_butSpaceDifferenceIsAnotherEmployer() {
        ledger.addEmployment(owner, "지은 카페", "4670", Industry.TOURISM_HOSPITALITY, 2, START, START.plusDays(10), 0, null);

        // 앞뒤 공백, 연속 공백, 대소문자는 같은 키
        assertThatThrownBy(() -> ledger.addEmployment(owner, "  지은   카페 ", "4670", Industry.TOURISM_HOSPITALITY, 2,
                START, START.plusDays(10), 0, null)).isInstanceOf(IllegalArgumentException.class);
        // 공백 차이 ("지은카페")는 다른 고용주로 보고 겹쳐도 저장된다
        ledger.addEmployment(owner, "지은카페", "4670", Industry.TOURISM_HOSPITALITY, 2, START, START.plusDays(10), 0, null);
    }

    @Test
    void disasterRecovery_withoutType_isStoredButExcluded() {
        ledger.addEmployment(owner, "재해 복구", "7000", Industry.NATURAL_DISASTER_RECOVERY, 2,
                START, START.plusDays(87), 0, null);

        VisaSummary second = ledger.dashboard(owner).summaries().get(0);
        assertThat(second.employmentDays()).isZero();
        assertThat(second.warnings()).anyMatch(w -> w.contains("조건부"));
    }

    @Test
    void disasterRecovery_paidType_isCounted() {
        ledger.addEmployment(owner, "재해 유급", "7000", Industry.NATURAL_DISASTER_RECOVERY, 2,
                START, START.plusDays(87), 0, com.whv.tracker.ledger.DisasterWorkType.PAID);

        assertThat(ledger.dashboard(owner).summaries().get(0).employmentDays()).isEqualTo(88);
    }

    @Test
    void disasterRecovery_volunteerType_countsOneDayPerRow() {
        WorkRecord v = ledger.addCasual(owner, "재해 봉사", "7000", Industry.NATURAL_DISASTER_RECOVERY, 2,
                START, START.plusDays(30), 5, null, null, com.whv.tracker.ledger.DisasterWorkType.VOLUNTEER, H8);
        ledger.addWorkDay(owner, v.getId(), START, null);

        assertThat(ledger.dashboard(owner).summaries().get(0).casualConvertedText()).isEqualTo("1.0");
    }

    @Test
    void disasterType_onOtherIndustry_isRejected() {
        assertThatThrownBy(() -> ledger.addEmployment(owner, "유형 잘못", "4670", Industry.TOURISM_HOSPITALITY, 2,
                START, START.plusDays(10), 0, com.whv.tracker.ledger.DisasterWorkType.PAID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void secondVisaRecordsDoNotCountInThirdVisa() {
        ledger.addEmployment(owner, "차수 분리", "4670", Industry.TOURISM_HOSPITALITY, 2,
                START, START.plusDays(87), 0, null);

        VisaSummary third = ledger.dashboard(owner).summaries().get(1);
        assertThat(third.visaYear()).isEqualTo(3);
        assertThat(third.employmentDays()).isZero();
    }

    @Test
    void workDaysArePersisted() {
        WorkRecord a = casual("저장 확인", START, START.plusDays(10));
        ledger.addWorkDay(owner, a.getId(), START, new BigDecimal("8.5"));

        assertThat(days.findByRecordOwnerAndWorkDate(owner, START)).hasSize(1);
    }

    @Test
    void recordsOfOtherAccount_areNotVisibleOrWritable() {
        AppUser other = users.upsertGoogleUser("other-sub", "other@example.com", "다른 사용자");
        WorkRecord mine = casual("내 카페", START, START.plusDays(10));

        assertThat(ledger.dashboard(other).records()).isEmpty();
        assertThatThrownBy(() -> ledger.addWorkDay(other, mine.getId(), START, H8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }
}
