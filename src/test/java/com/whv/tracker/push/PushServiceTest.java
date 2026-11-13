package com.whv.tracker.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.whv.tracker.ledger.Industry;
import com.whv.tracker.ledger.LedgerService;
import com.whv.tracker.ledger.WorkRecord;
import com.whv.tracker.user.AccountDeletionService;
import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 주 1회 알림 (PRD 4.10e). 실제로 보내지 않고, 보내는 쪽을 가짜로 바꿔 끼운다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PushServiceTest {

    @Autowired private PushSubscriptionRepository subs;
    @Autowired private LedgerService ledger;
    @Autowired private AppUserService users;
    @Autowired private AccountDeletionService deletion;

    private final List<String> sent = new ArrayList<>();
    private final Map<String, Integer> answer = new HashMap<>();
    private PushService push;
    private AppUser owner;

    private static final String P256DH = "B" + "A".repeat(86);   // 길이·글자만 검사하는 자리
    private static final String AUTH = "QUFBQUFBQUFBQUFBQUFBQQ";

    @BeforeEach
    void setUp() {
        PushSender fake = new PushSender(null, null, null) {
            @Override
            public int send(PushSubscription s, String json) {
                sent.add(s.getEndpoint() + " " + json);
                return answer.getOrDefault(s.getEndpoint(), 201);
            }
        };
        push = new PushService(subs, ledger, fake);
        owner = users.upsertGoogleUser("push-sub", "push@example.com", "알림 테스트");
    }

    private String ep(int n) {
        return "https://fcm.googleapis.com/fcm/send/device" + n;
    }

    @Test
    void subscribe_sendsWelcome_andKeepsAtMostFivePerUser() {
        for (int i = 1; i <= 6; i++) {
            assertThat(push.subscribe(owner, ep(i), P256DH, AUTH)).isTrue();
        }
        assertThat(sent.get(0)).contains("알림을 켰어요");
        List<PushSubscription> mine = subs.findByOwnerOrderByCreatedAtAsc(owner);
        assertThat(mine).hasSize(5);
        assertThat(mine).extracting(PushSubscription::getEndpoint).doesNotContain(ep(1));
    }

    @Test
    void subscribe_removedWhenWelcomeFails() {
        answer.put(ep(1), 400);
        assertThat(push.subscribe(owner, ep(1), P256DH, AUTH)).isFalse();
        assertThat(subs.findByEndpoint(ep(1))).isEmpty();
    }

    @Test
    void weekly_messageCountsThisWeek_dropsGoneSubscriptions() {
        LocalDate today = LocalDate.now(PushService.SYDNEY);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        WorkRecord r = ledger.addCasual(owner, "알림 농장", "4670", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                monday.minusDays(30), today, 5, null, null, null, new BigDecimal("8"), null, false);

        assertThat(push.weeklyMessage(owner, today)).contains("이번 주 근무를 아직 안 적었어요").contains("세컨 88일 중 0일");

        ledger.addWorkDay(owner, r.getId(), monday, new BigDecimal("8"));
        assertThat(push.weeklyMessage(owner, today)).contains("이번 주 1.4일 인정됐어요");

        push.subscribe(owner, ep(1), P256DH, AUTH);
        push.subscribe(owner, ep(2), P256DH, AUTH);
        sent.clear();
        answer.put(ep(2), 410);  // 사용자가 브라우저에서 알림을 지움
        push.weekly();
        assertThat(sent).hasSize(2);
        assertThat(subs.findByEndpoint(ep(1))).isPresent();
        assertThat(subs.findByEndpoint(ep(2))).isEmpty();
    }

    @Test
    void accountDeletion_removesSubscriptions() {
        push.subscribe(owner, ep(1), P256DH, AUTH);
        deletion.deleteAccount(owner.getId());
        assertThat(subs.findByEndpoint(ep(1))).isEmpty();
    }
}
