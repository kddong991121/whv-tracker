package com.whv.tracker.admin;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 로그인한 사용자의 마지막 방문 시각(users.last_seen_at)을 하루에 한 번만 갱신한다.
 * 세션에 오늘 날짜를 적어 두고, 날짜가 바뀐 첫 요청에서만 DB를 쓴다 (요청마다 쓰지 않는다).
 * 로그인을 30일 유지하므로 "로그인 시각"으로는 다시 온 사람을 셀 수 없어서 방문 시각을 쓴다.
 */
@Component
public class LastSeenInterceptor implements HandlerInterceptor {

    static final String SESSION_KEY = "whv.lastSeenDay";

    private final EntityManager em;
    private final TransactionTemplate tx;

    public LastSeenInterceptor(EntityManager em, TransactionTemplate tx) {
        this.em = em;
        this.tx = tx;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof OAuth2AuthenticationToken)) {
            return true;
        }
        HttpSession session = request.getSession(false);
        String today = LocalDate.now().toString();
        if (session == null || today.equals(session.getAttribute(SESSION_KEY))) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        tx.executeWithoutResult(s -> em.createQuery("update AppUser u set u.lastSeenAt = :now where u.googleSubject = :sub")
                .setParameter("now", now).setParameter("sub", auth.getName()).executeUpdate());
        session.setAttribute(SESSION_KEY, today);
        return true;
    }
}
