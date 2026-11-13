package com.whv.tracker.push;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/** 알림 한 건 보내기 (RFC 8030 + 8291 + 8292). 결과 코드만 돌려주고, 판단은 PushService가 한다. */
public class PushSender {

    private final Vapid vapid;
    private final String subject;
    private final HttpClient http;

    public PushSender(Vapid vapid, String subject, HttpClient http) {
        this.vapid = vapid;
        this.subject = subject;
        this.http = http;
    }

    public static HttpClient defaultClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .proxy(java.net.ProxySelector.getDefault()).build(); // 프록시 설정이 없으면 바로 연결
    }

    /** @return HTTP 상태 코드 (201 성공, 404·410은 구독이 사라짐) */
    public int send(PushSubscription s, String json) throws Exception {
        byte[] body = WebPushCrypto.encrypt(json.getBytes(StandardCharsets.UTF_8), s.getP256dh(), s.getAuth());
        HttpRequest req = HttpRequest.newBuilder(URI.create(s.getEndpoint()))
                .timeout(Duration.ofSeconds(15))
                .header("TTL", "86400")                  // 휴대폰이 꺼져 있으면 하루까지 기다린다
                .header("Urgency", "normal")
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("Authorization", vapid.authorization(PushEndpointPolicy.origin(s.getEndpoint()), subject,
                        Instant.now().getEpochSecond()))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
