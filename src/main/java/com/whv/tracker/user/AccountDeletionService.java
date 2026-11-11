package com.whv.tracker.user;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴: 계정과 그 계정의 모든 기록을 바로 지운다 (개인정보처리방침 "파기").
 * 외래키 순서대로 자식 테이블부터 지운다. 하나라도 실패하면 전부 되돌린다.
 */
@Service
public class AccountDeletionService {

    private final EntityManager em;

    public AccountDeletionService(EntityManager em) {
        this.em = em;
    }

    @Transactional
    public void deleteAccount(long userId) {
        em.createQuery("delete from Payslip p where p.owner.id = :u").setParameter("u", userId).executeUpdate();
        em.createQuery("delete from WorkDay d where d.record.id in "
                + "(select r.id from WorkRecord r where r.owner.id = :u)").setParameter("u", userId).executeUpdate();
        em.createQuery("delete from WorkStandardChange c where c.record.id in "
                + "(select r.id from WorkRecord r where r.owner.id = :u)").setParameter("u", userId).executeUpdate();
        em.createQuery("delete from WorkRecord r where r.owner.id = :u").setParameter("u", userId).executeUpdate();
        em.createQuery("delete from AppUser a where a.id = :u").setParameter("u", userId).executeUpdate();
    }
}
