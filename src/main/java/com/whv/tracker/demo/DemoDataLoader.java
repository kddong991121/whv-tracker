package com.whv.tracker.demo;

import com.whv.tracker.ledger.DisasterWorkType;
import com.whv.tracker.ledger.Industry;
import com.whv.tracker.ledger.LedgerService;
import com.whv.tracker.security.DemoAuthFilter;
import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 데모 프로필 기동 시 고정 가짜 사용자 계정을 만들고, 그 계정의 기록이 비어 있으면 샘플 3건을 넣는다.
 * 샘플 저장은 LedgerService를 거치므로 입력 규칙이 그대로 적용된다. 고용주 이름은 모두 가상이다.
 */
@Component
@Profile("demo")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final AppUserService users;
    private final LedgerService ledger;

    public DemoDataLoader(AppUserService users, LedgerService ledger) {
        this.users = users;
        this.ledger = ledger;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppUser owner = users.upsertGoogleUser(DemoAuthFilter.DEMO_SUB, "demo@example.invalid", "데모 사용자");
        if (!ledger.dashboard(owner).records().isEmpty()) {
            return;
        }

        // 1) 2차 풀타임 연속 고용 (모드 A): 2026-03-02 ~ 2026-05-28 = 88 달력일, 무급 휴무 0일.
        ledger.addEmployment(owner, "Green Valley Farm (데모)", "4670", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION,
                2, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 5, 28), 0, null);

        // 2) 2차 파트타임 포장 작업 (모드 B): 근무일 10일, 기준 주당 5일.
        var packing = ledger.addCasual(owner, "Sunrise Packing (데모)", "4670",
                Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31), 5, null, null, null);
        for (LocalDate d : List.of(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 3),
                LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 16), LocalDate.of(2026, 6, 17),
                LocalDate.of(2026, 6, 22))) {
            ledger.addWorkDay(owner, packing.getId(), d, new BigDecimal("8"));
        }

        // 3) 3차 재해 복구 봉사 (모드 B): 근무일 3일, 봉사는 행마다 1일로 계산.
        var recovery = ledger.addCasual(owner, "Riverside Recovery Crew (데모)", "2480",
                Industry.NATURAL_DISASTER_RECOVERY, 3,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 20), 5, null, null, DisasterWorkType.VOLUNTEER);
        for (LocalDate d : List.of(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 13))) {
            ledger.addWorkDay(owner, recovery.getId(), d, null);
        }

        log.info("데모 샘플 기록 {}건을 넣었습니다.", ledger.dashboard(owner).records().size());
    }
}
