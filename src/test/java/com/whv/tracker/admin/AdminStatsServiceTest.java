package com.whv.tracker.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.whv.tracker.ledger.Industry;
import com.whv.tracker.ledger.LedgerService;
import com.whv.tracker.ledger.WorkRecord;
import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 재방문 지표(PRD 4.9b): 가입한 날 뒤에 또 온 사람, 최근 7일 근무를 적은 사람. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminStatsServiceTest {

    @Autowired private AdminStatsService stats;
    @Autowired private AppUserService users;
    @Autowired private LedgerService ledger;
    @Autowired private EntityManager em;

    @Test
    void countsReturnedUsersAndRecentRecorders() {
        LocalDateTime now = LocalDateTime.now();
        AppUser back = users.upsertGoogleUser("r1", "back@example.com", "A");    // 사흘 전 가입, 오늘 방문
        AppUser sameDay = users.upsertGoogleUser("r2", "same@example.com", "B"); // 가입한 날만 방문
        users.upsertGoogleUser("r3", "never@example.com", "C");                  // 방문 기록 없음
        em.flush();
        setDates(back, now.minusDays(3), now);
        setDates(sameDay, now, now);

        LocalDate start = LocalDate.of(2026, 10, 1);
        WorkRecord place = ledger.addCasual(back, "농장A", "4670", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                start, start.plusMonths(2), 5, null, null, null, new BigDecimal("8"), null, false);
        ledger.addWorkDay(back, place.getId(), start.plusDays(4), new BigDecimal("8"));
        em.flush();

        AdminStatsService.Stats s = stats.stats(now);
        assertThat(s.returnedUsers()).isEqualTo(1);
        assertThat(s.recordedLast7Days()).isEqualTo(1);
        assertThat(s.recordedLast7Percent()).isEqualTo(100);

        // 8일 전에 적은 근무는 최근 7일에 안 잡힌다
        em.createNativeQuery("update work_day set created_at = ?1").setParameter(1, now.minusDays(8)).executeUpdate();
        em.clear();
        assertThat(stats.stats(now).recordedLast7Days()).isZero();
    }

    private void setDates(AppUser u, LocalDateTime created, LocalDateTime lastSeen) {
        em.createNativeQuery("update users set created_at = ?1, last_seen_at = ?2 where id = ?3")
                .setParameter(1, created).setParameter(2, lastSeen).setParameter(3, u.getId()).executeUpdate();
    }
}
