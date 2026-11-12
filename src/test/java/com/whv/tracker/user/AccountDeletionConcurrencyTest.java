package com.whv.tracker.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.whv.tracker.ledger.Industry;
import com.whv.tracker.ledger.LedgerService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 탈퇴 버튼을 두 번 눌러 같은 계정 탈퇴가 동시에 두 번 들어와도 오류 없이 한 번만 지워진다.
 * (2026-11-11 운영에서 본 Deadlock 재발 방지). 트랜잭션을 실제로 나눠야 해서 @Transactional을 붙이지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountDeletionConcurrencyTest {

    @Autowired private AppUserService users;
    @Autowired private LedgerService ledger;
    @Autowired private AccountDeletionService deletion;

    @Test
    void doubleDelete_atSameTime_deletesOnceWithoutError() {
        AppUser owner = users.upsertGoogleUser("double-del", "double@example.com", "두번");
        LocalDate mon = LocalDate.of(2026, 11, 2);
        ledger.addCasual(owner, "농장", "4870", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                mon, mon.plusDays(10), 5, null, null, null, new BigDecimal("8"), null, false);

        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> runs = List.of(
                CompletableFuture.supplyAsync(() -> awaitThenDelete(start, owner.getId())),
                CompletableFuture.supplyAsync(() -> awaitThenDelete(start, owner.getId())));
        start.countDown();

        List<Boolean> results = runs.stream().map(CompletableFuture::join).toList(); // 예외가 나면 여기서 실패
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(users.findByGoogleSubject("double-del")).isNull();
    }

    private boolean awaitThenDelete(CountDownLatch start, long userId) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return deletion.deleteAccount(userId);
    }
}
