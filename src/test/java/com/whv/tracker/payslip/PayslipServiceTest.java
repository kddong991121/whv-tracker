package com.whv.tracker.payslip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.whv.tracker.ledger.Industry;
import com.whv.tracker.ledger.LedgerService;
import com.whv.tracker.ledger.WorkDay;
import com.whv.tracker.ledger.WorkDayRepository;
import com.whv.tracker.ledger.WorkRecord;
import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** F3 필수: 읽은 값 저장, 시간 나누기, 동의·기간·ABN 검증. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PayslipServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 2);

    @Autowired
    private PayslipService payslips;
    @Autowired
    private LedgerService ledger;
    @Autowired
    private AppUserService users;
    @Autowired
    private WorkDayRepository days;

    private AppUser owner;
    private WorkRecord farm;

    @BeforeEach
    void setUp() {
        owner = users.upsertGoogleUser("slip-sub", "slip@example.com", "페이슬립");
        farm = ledger.addCasual(owner, "Green Valley Farm", "4870", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                MON.minusDays(30), MON.plusDays(30), 5, null, null, null, new BigDecimal("6"), null, false);
    }

    private PayslipService.Import slip(List<LocalDate> dates, String abn, boolean consent) {
        return new PayslipService.Import(farm.getId(), "Green Valley Farm", abn, MON, MON.plusDays(6),
                new BigDecimal("30.5"), "Casual", dates, List.of(0, 2, 3), false, consent);
    }

    @Test
    void import_createsWorkDays_withHoursSplitEvenly() {
        Payslip p = payslips.importPayslip(owner, slip(List.of(MON, MON.plusDays(2), MON.plusDays(3), MON.plusDays(6)),
                "51 824 753 556", true));

        assertThat(p.getWorkedDays()).isEqualTo(4);
        assertThat(p.score()).isEqualTo(3);
        assertThat(p.getAbn()).isEqualTo("51824753556");
        List<WorkDay> rows = days.findByRecordOwner(owner);
        assertThat(rows).hasSize(4).allMatch(d -> d.getWorkHours().compareTo(new BigDecimal("7.63")) == 0);
    }

    @Test
    void consentRequired_andDateOutsidePeriodRejected() {
        assertThatThrownBy(() -> payslips.importPayslip(owner, slip(List.of(MON), null, false)))
                .hasMessageContaining("동의");
        assertThatThrownBy(() -> payslips.importPayslip(owner, slip(List.of(MON.plusDays(7)), null, true)))
                .hasMessageContaining("급여 기간 밖");
    }

    @Test
    void wrongAbnChecksum_isRejected() {
        assertThatThrownBy(() -> payslips.importPayslip(owner, slip(List.of(MON), "51 824 753 557", true)))
                .hasMessageContaining("ABN");
    }

    @Test
    void duplicateDay_rollsBackWholeImport() {
        ledger.addWorkDay(owner, farm.getId(), MON.plusDays(1), new BigDecimal("8"));

        assertThatThrownBy(() -> payslips.importPayslip(owner, slip(List.of(MON, MON.plusDays(1)), null, true)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
