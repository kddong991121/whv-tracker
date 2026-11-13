package com.whv.tracker.push;

import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 알림 켜기·끄기 (PRD 4.10e). 로그인과 CSRF 검사를 받는다. */
@RestController
public class PushController {

    private final PushService push;
    private final AppUserService users;

    public PushController(PushService push, AppUserService users) {
        this.push = push;
        this.users = users;
    }

    @PostMapping("/push/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(Authentication authentication,
                                                         @RequestParam String endpoint,
                                                         @RequestParam String p256dh,
                                                         @RequestParam String auth) {
        AppUser owner = users.findByGoogleSubject(authentication.getName());
        if (owner == null || !push.enabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("ok", false, "error", "알림 기능을 쓸 수 없어요."));
        }
        try {
            if (!push.subscribe(owner, endpoint, p256dh, auth)) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("ok", false, "error", "확인 알림을 보내지 못했어요. 잠시 뒤 다시 켜 주세요."));
            }
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/push/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribe(Authentication authentication, @RequestParam String endpoint) {
        AppUser owner = users.findByGoogleSubject(authentication.getName());
        if (owner != null) {
            push.unsubscribe(owner, endpoint);
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
