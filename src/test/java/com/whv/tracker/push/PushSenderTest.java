package com.whv.tracker.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 실제 HTTP 요청 모양 확인: 로컬 가짜 푸시 서버로 보내서 헤더와 본문 길이를 본다. */
class PushSenderTest {

    @Test
    void sendsEncryptedBodyWithVapidHeaders() throws Exception {
        Map<String, String> got = new HashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push/abc", ex -> {
            ex.getRequestHeaders().forEach((k, v) -> got.put(k.toLowerCase(), v.get(0)));
            got.put("bodyLength", String.valueOf(ex.getRequestBody().readAllBytes().length));
            ex.sendResponseHeaders(201, -1);
            ex.close();
        });
        server.start();
        try {
            KeyPair kp = Vapid.newKeyPair();
            byte[] d = new byte[32];
            Vapid.copyFixed(((ECPrivateKey) kp.getPrivate()).getS(), d, 0);
            Vapid vapid = new Vapid(Vapid.B64.encodeToString(Vapid.rawPublicKey((ECPublicKey) kp.getPublic())),
                    Vapid.B64.encodeToString(d));
            KeyPair ua = Vapid.newKeyPair();
            PushSubscription s = new PushSubscription(null,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/push/abc",
                    Vapid.B64.encodeToString(Vapid.rawPublicKey((ECPublicKey) ua.getPublic())),
                    Vapid.B64.encodeToString(new byte[16]), null);

            int code = new PushSender(vapid, "mailto:a@example.com", HttpClient.newHttpClient()).send(s, "{\"title\":\"t\"}");

            assertThat(code).isEqualTo(201);
            assertThat(got.get("content-encoding")).isEqualTo("aes128gcm");
            assertThat(got.get("ttl")).isEqualTo("86400");
            assertThat(got.get("authorization")).startsWith("vapid t=").contains(", k=");
            // 머리 86바이트 + (내용 13 + 표시 1 + 인증 태그 16)
            assertThat(Integer.parseInt(got.get("bodyLength"))).isEqualTo(86 + 13 + 1 + 16);
        } finally {
            server.stop(0);
        }
    }
}
