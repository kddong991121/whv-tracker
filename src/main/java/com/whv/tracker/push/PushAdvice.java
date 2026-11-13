package com.whv.tracker.push;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 화면에 VAPID 공개키를 넘긴다. 키가 없으면 빈 값이라 알림 카드가 안 보인다. 공개키는 비밀이 아니다. */
@ControllerAdvice
public class PushAdvice {

    private final String publicKey;

    public PushAdvice(PushService push, @Value("${whv.push.vapid-public:}") String publicKey) {
        this.publicKey = push.enabled() ? publicKey.trim() : "";
    }

    @ModelAttribute("vapidPublic")
    public String vapidPublic() {
        return publicKey;
    }
}
