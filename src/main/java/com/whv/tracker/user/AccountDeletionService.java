package com.whv.tracker.user;

import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴: 계정과 그 계정의 모든 기록을 바로 지운다 (개인정보처리방침 "파기").
 * 외래키 순서대로 자식 테이블부터 지운다. 하나라도 실패하면 전부 되돌린다.
 *
 * 2026-11-11 운영 로그에서 탈퇴 중 Deadlock 발견 (같은 계정 탈퇴 요청이 동시에 두 번 들어온 경우로 추정).
 * 고침: 지우기 전에 users 행을 먼저 쓰기 잠금(SELECT ... FOR UPDATE)한다. 두 번째 요청은 첫 번째가 끝날 때까지
 * 기다렸다가, 계정이 이미 없으면 아무것도 하지 않는다. 잠그는 순서가 항상 같아져서 교착이 생기지 않는다.
 */
@Service
public class AccountDeletionService {

    private final EntityManager em;

    public AccountDeletionService(EntityManager em) {
        this.em = em;
    }

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    /** @return 실제로 지웠으면 true, 이미 없는 계정이면 false. */
    @Transactional
    public boolean deleteAccount(long userId) {
        AppUser locked = em.find(AppUser.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (locked == null) {
            return false; // 다른 요청이 먼저 지웠다
        }
        em.createQuery("delete from Payslip p where p.owner.id = :u").setParameter("u", userId).executeUpdate();
        em.createQuery("delete from WorkDay d where d.record.id in "
                + "(select r.id from WorkRecord r where r.owner.id = :u)").setParameter("u", userId).executeUpdate();
        em.createQuery("delete from WorkStandardChange c where c.record.id in "
                + "(select r.id from WorkRecord r where r.owner.id = :u)").setParameter("u", userId).executeUpdate();
        em.createQuery("delete from WorkRecord r where r.owner.id = :u").setParameter("u", userId).executeUpdate();
        em.flush();
        em.detach(locked);
        em.createQuery("delete from AppUser a where a.id = :u").setParameter("u", userId).executeUpdate();
        // 운영 통계용: 탈퇴 시각만 남긴다 (누구인지는 남기지 않는다)
        em.persist(new com.whv.tracker.admin.DeletedAccountLog(java.time.LocalDateTime.now()));
        // 몇 번 계정이 탈퇴했는지만 남긴다 (이메일·이름 같은 개인정보는 남기지 않는다)
        log.info("account deleted: userId={}", userId);
        return true;
    }
}
