package com.whv.tracker.admin;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * /admin 운영 통계. 개인정보(이메일·이름·근무 내용)는 꺼내지 않고 숫자만 센다.
 * 날짜 기준은 서버 시간대(Australia/Sydney)다.
 */
@Service
public class AdminStatsService {

    private final EntityManager em;

    public AdminStatsService(EntityManager em) {
        this.em = em;
    }

    /** @param signupsByDay 최근 14일 날짜별 가입 수 (오래된 날 먼저) */
    public record Stats(long totalUsers, long todaySignups, long activeLast7Days, long secondGoal, long thirdGoal,
                        long usersWithRecords, long deletedLast30Days, List<DayCount> signupsByDay) {

        /** 막대 높이(%) 계산용: 14일 중 가장 많은 날의 가입 수 (0이면 1). */
        public long maxDaily() {
            return Math.max(1, signupsByDay.stream().mapToLong(DayCount::count).max().orElse(0));
        }
    }

    public record DayCount(LocalDate day, long count) {
    }

    @Transactional(readOnly = true)
    public Stats stats(LocalDateTime now) {
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        long total = count("select count(u) from AppUser u");
        long today = count("select count(u) from AppUser u where u.createdAt >= ?1", todayStart);
        long active = count("select count(u) from AppUser u where u.lastSeenAt >= ?1", now.minusDays(7));
        long second = count("select count(u) from AppUser u where u.goalVisa = 2");
        long third = count("select count(u) from AppUser u where u.goalVisa = 3");
        long withRecords = count("select count(distinct r.owner.id) from WorkRecord r");
        long deleted = count("select count(d) from DeletedAccountLog d where d.deletedAt >= ?1", now.minusDays(30));

        LocalDate from = now.toLocalDate().minusDays(13);
        Map<LocalDate, Long> byDay = new TreeMap<>();
        for (int i = 0; i < 14; i++) {
            byDay.put(from.plusDays(i), 0L);
        }
        List<LocalDateTime> created = em.createQuery(
                        "select u.createdAt from AppUser u where u.createdAt >= ?1", LocalDateTime.class)
                .setParameter(1, from.atStartOfDay()).getResultList();
        for (LocalDateTime c : created) {
            byDay.merge(c.toLocalDate(), 1L, Long::sum);
        }
        List<DayCount> days = new ArrayList<>();
        byDay.forEach((d, c) -> days.add(new DayCount(d, c)));
        return new Stats(total, today, active, second, third, withRecords, deleted, days);
    }

    private long count(String jpql, Object... params) {
        var q = em.createQuery(jpql, Long.class);
        for (int i = 0; i < params.length; i++) {
            q.setParameter(i + 1, params[i]);
        }
        return q.getSingleResult();
    }
}
