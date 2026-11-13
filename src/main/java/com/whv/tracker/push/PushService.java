package com.whv.tracker.push;

import com.whv.tracker.calc.VisaSummary;
import com.whv.tracker.ledger.LedgerService;
import com.whv.tracker.user.AppUser;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주 1회 알림 (PRD 4.10e). 사용자가 켠 브라우저에만 보낸다.
 * 서버 환경변수에 VAPID 키가 없으면 기능 전체가 꺼진다 (화면에도 안 보임).
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");
    /** 한 사용자당 기기 수 상한. 넘으면 가장 오래된 것을 지운다. */
    static final int MAX_PER_USER = 5;

    private final PushSubscriptionRepository subs;
    private final LedgerService ledger;
    private final PushSender sender; // 키가 없으면 null

    @org.springframework.beans.factory.annotation.Autowired
    public PushService(PushSubscriptionRepository subs, LedgerService ledger,
                       @Value("${whv.push.vapid-public:}") String vapidPublic,
                       @Value("${whv.push.vapid-private:}") String vapidPrivate,
                       @Value("${whv.contact-email:}") String contactEmail,
                       @Value("${whv.site-url:https://whv88.duckdns.org}") String siteUrl) {
        this.subs = subs;
        this.ledger = ledger;
        PushSender s = null;
        if (!vapidPublic.isBlank() && !vapidPrivate.isBlank()) {
            try {
                String subject = contactEmail.isBlank() ? siteUrl : "mailto:" + contactEmail.trim();
                s = new PushSender(new Vapid(vapidPublic, vapidPrivate), subject, PushSender.defaultClient());
            } catch (Exception e) {
                log.warn("push disabled: VAPID key is invalid");
            }
        }
        this.sender = s;
    }

    /** 테스트용: 보내는 쪽을 바꿔 끼운다. */
    PushService(PushSubscriptionRepository subs, LedgerService ledger, PushSender sender) {
        this.subs = subs;
        this.ledger = ledger;
        this.sender = sender;
    }

    public boolean enabled() {
        return sender != null;
    }

    /**
     * 구독 저장 후 확인 알림을 바로 한 번 보낸다. 실패하면 구독을 지우고 false.
     * 같은 주소가 이미 있으면 (다른 계정이었어도) 지금 사용자로 바꾼다 — 한 브라우저는 한 계정만 받는다.
     */
    @Transactional
    public boolean subscribe(AppUser owner, String endpoint, String p256dh, String auth) {
        if (!enabled()) {
            throw new IllegalStateException("알림 기능이 꺼져 있어요.");
        }
        if (!PushEndpointPolicy.allowed(endpoint) || !b64(p256dh, 80, 100) || !b64(auth, 16, 30)) {
            throw new IllegalArgumentException("이 브라우저의 알림 정보를 확인할 수 없어요.");
        }
        PushSubscription s = subs.findByEndpoint(endpoint).orElse(null);
        if (s == null) {
            s = subs.save(new PushSubscription(owner, endpoint, p256dh, auth, LocalDateTime.now()));
        } else {
            s.update(owner, p256dh, auth);
        }
        List<PushSubscription> mine = subs.findByOwnerOrderByCreatedAtAsc(owner);
        for (int i = 0; i < mine.size() - MAX_PER_USER; i++) {
            subs.delete(mine.get(i));
        }
        int code = deliver(s, message("알림을 켰어요", "매주 일요일 저녁 7시(시드니 시간)에 이번 주 기록을 알려 드릴게요.", "/"));
        if (code < 200 || code >= 300) {
            subs.delete(s);
            return false;
        }
        return true;
    }

    @Transactional
    public void unsubscribe(AppUser owner, String endpoint) {
        subs.deleteByOwnerAndEndpoint(owner, endpoint);
    }

    /** 매주 일요일 19:00 시드니 시간. */
    @Scheduled(cron = "${whv.push.cron:0 0 19 * * SUN}", zone = "Australia/Sydney")
    public void weekly() {
        if (!enabled()) {
            return;
        }
        LocalDate today = LocalDate.now(SYDNEY);
        int sent = 0, gone = 0, failed = 0, skipped = 0;
        Long lastOwner = null;
        String lastJson = null;
        for (PushSubscription s : subs.findAllWithOwner()) {
            if (!s.getOwner().getId().equals(lastOwner)) {
                lastOwner = s.getOwner().getId();
                lastJson = weeklyMessage(s.getOwner(), today);
            }
            if (lastJson == null) {
                skipped++;
                continue;
            }
            int code = deliver(s, lastJson);
            if (code == 404 || code == 410) {
                subs.delete(s); // 사용자가 브라우저에서 알림을 끄거나 앱을 지웠다
                gone++;
            } else if (code >= 200 && code < 300) {
                sent++;
            } else {
                failed++;
            }
        }
        log.info("weekly push: sent={} gone={} failed={} skipped={}", sent, gone, failed, skipped);
    }

    /** 목표를 이미 채웠으면 null (보내지 않음). */
    String weeklyMessage(AppUser owner, LocalDate today) {
        int idx = owner.getGoalVisa() == 3 ? 1 : 0;
        VisaSummary all = ledger.dashboard(owner).summaries().get(idx);
        if (all.targetMet()) {
            return null;
        }
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        BigDecimal week = ledger.dashboard(owner, null, monday, today).summaries().get(idx).total();
        String progress = all.visaName() + " " + all.target() + "일 중 " + all.displayDays() + "일";
        String body = week.signum() > 0
                ? "이번 주 " + week.stripTrailingZeros().toPlainString() + "일 인정됐어요 · " + progress + ". 빠진 날 없나요?"
                : "이번 주 근무를 아직 안 적었어요 · " + progress;
        return message("이번 주 근무 확인", body, "/work");
    }

    private int deliver(PushSubscription s, String json) {
        try {
            return sender.send(s, json);
        } catch (Exception e) {
            return -1;
        }
    }

    static String message(String title, String body, String url) {
        return "{\"title\":" + quote(title) + ",\"body\":" + quote(body) + ",\"url\":" + quote(url) + "}";
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean b64(String v, int min, int max) {
        return v != null && v.length() >= min && v.length() <= max && v.matches("[A-Za-z0-9_-]+=*");
    }

    public long countUsers() {
        return subs.countUsers();
    }
}
