package com.whv.tracker.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * 알림 암호화 (RFC 8291)와 VAPID 서명 (RFC 8292).
 * 브라우저 쪽 복호화를 규격대로 따로 짜서, 우리가 만든 본문이 풀리는지 확인한다.
 * (개발 중 파이썬 http_ece 라이브러리로도 따로 풀어 확인했다: docs/notes/RETENTION-C.md)
 */
class WebPushCryptoTest {

    @Test
    void browserCanDecrypt() throws Exception {
        KeyPair ua = Vapid.newKeyPair();                    // 브라우저 키
        byte[] auth = new byte[16];
        Arrays.fill(auth, (byte) 7);
        String p256dh = Vapid.B64.encodeToString(Vapid.rawPublicKey((ECPublicKey) ua.getPublic()));
        String msg = "{\"title\":\"워홀 88\",\"body\":\"이번 주 3일\"}";

        byte[] body = WebPushCrypto.encrypt(msg.getBytes(StandardCharsets.UTF_8), p256dh, Vapid.B64.encodeToString(auth));

        // 머리: salt(16) | rs(4)=4096 | idlen(1)=65 | 서버 임시 공개키(65)
        ByteBuffer b = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        b.get(salt);
        assertThat(b.getInt()).isEqualTo(4096);
        assertThat(b.get()).isEqualTo((byte) 65);
        byte[] asPublic = new byte[65];
        b.get(asPublic);
        byte[] cipher = new byte[b.remaining()];
        b.get(cipher);

        // 브라우저 쪽 계산 (같은 규격, 반대 방향 ECDH)
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ua.getPrivate());
        ka.doPhase(Vapid.publicKeyFromRaw(asPublic), true);
        byte[] ecdh = ka.generateSecret();
        byte[] uaPublic = Vapid.B64D.decode(p256dh);
        byte[] ikm = WebPushCrypto.hkdf(auth, ecdh,
                WebPushCrypto.concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic), 32);
        byte[] cek = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = gcm.doFinal(cipher);

        assertThat(plain[plain.length - 1]).isEqualTo((byte) 2); // 마지막 레코드 표시
        assertThat(new String(plain, 0, plain.length - 1, StandardCharsets.UTF_8)).isEqualTo(msg);
    }

    @Test
    void rejectsTooLongMessage() throws Exception {
        KeyPair ua = Vapid.newKeyPair();
        String p256dh = Vapid.B64.encodeToString(Vapid.rawPublicKey((ECPublicKey) ua.getPublic()));
        assertThatThrownBy(() -> WebPushCrypto.encrypt(new byte[4000], p256dh, Vapid.B64.encodeToString(new byte[16])))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vapidTokenIsSignedWithOurKey() throws Exception {
        KeyPair kp = Vapid.newKeyPair();
        byte[] d = new byte[32];
        Vapid.copyFixed(((ECPrivateKey) kp.getPrivate()).getS(), d, 0);
        String pub = Vapid.B64.encodeToString(Vapid.rawPublicKey((ECPublicKey) kp.getPublic()));
        Vapid vapid = new Vapid(pub, Vapid.B64.encodeToString(d));

        String h = vapid.authorization("https://fcm.googleapis.com", "mailto:a@example.com", 1_800_000_000L);
        assertThat(h).startsWith("vapid t=").endsWith(", k=" + pub);
        String[] jwt = h.substring("vapid t=".length(), h.indexOf(',')).split("\\.");
        String claims = new String(Vapid.B64D.decode(jwt[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"").contains("\"exp\":1800043200")
                .contains("\"sub\":\"mailto:a@example.com\"");

        Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
        verify.initVerify(kp.getPublic());
        verify.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verify.verify(Vapid.B64D.decode(jwt[2]))).isTrue();
    }

    @Test
    void onlyKnownPushServicesAreAllowed() {
        assertThat(PushEndpointPolicy.allowed("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
        assertThat(PushEndpointPolicy.allowed("https://web.push.apple.com/QK1")).isTrue();
        assertThat(PushEndpointPolicy.allowed("https://updates.push.services.mozilla.com/wpush/v2/x")).isTrue();
        assertThat(PushEndpointPolicy.allowed("https://wns2-par02p.notify.windows.com/w/?token=1")).isTrue();
        // 내부망·다른 주소·http·포트 지정·사용자 정보는 막는다 (SSRF 방지)
        assertThat(PushEndpointPolicy.allowed("http://fcm.googleapis.com/x")).isFalse();
        assertThat(PushEndpointPolicy.allowed("https://127.0.0.1/x")).isFalse();
        assertThat(PushEndpointPolicy.allowed("https://fcm.googleapis.com.evil.example/x")).isFalse();
        assertThat(PushEndpointPolicy.allowed("https://fcm.googleapis.com:8443/x")).isFalse();
        assertThat(PushEndpointPolicy.allowed("https://u@fcm.googleapis.com/x")).isFalse();
        assertThat(PushEndpointPolicy.allowed("https://notify.windows.com.evil.example/x")).isFalse();
        assertThat(PushEndpointPolicy.allowed(null)).isFalse();
    }
}
