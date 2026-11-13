package com.whv.tracker.push;

import java.net.URI;
import java.util.List;

/**
 * 구독 주소 검사. 서버가 이 주소로 직접 요청을 보내므로, 아무 주소나 받으면 서버를 이용해
 * 내부망을 찌르는 공격(SSRF)이 될 수 있다. 알려진 브라우저 푸시 서비스의 https 주소만 받는다.
 */
public final class PushEndpointPolicy {

    private static final List<String> HOSTS = List.of(
            "fcm.googleapis.com",                 // 크롬·안드로이드·엣지 일부
            "updates.push.services.mozilla.com",  // 파이어폭스
            "web.push.apple.com",                 // 사파리·아이폰 (홈 화면 앱)
            ".notify.windows.com");               // 엣지 (wns2-xxx.notify.windows.com)

    private PushEndpointPolicy() {
    }

    public static boolean allowed(String endpoint) {
        if (endpoint == null || endpoint.length() > 600) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || host == null || uri.getPort() != -1 || uri.getUserInfo() != null) {
                return false;
            }
            String h = host.toLowerCase();
            return HOSTS.stream().anyMatch(a -> a.startsWith(".") ? h.endsWith(a) : h.equals(a));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** VAPID aud 값: 주소의 origin (https://host). */
    static String origin(String endpoint) {
        URI uri = URI.create(endpoint);
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }
}
