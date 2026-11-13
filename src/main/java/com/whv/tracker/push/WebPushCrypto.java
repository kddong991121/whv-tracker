package com.whv.tracker.push;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 알림 내용 암호화 (RFC 8291, "aes128gcm"). 푸시 서비스(구글·애플·모질라)는 내용을 볼 수 없고,
 * 구독한 그 브라우저만 풀 수 있다. JDK 기본 기능만 쓴다.
 *
 * <pre>
 * ecdh   = ECDH(서버 임시 비밀키, 브라우저 공개키 p256dh)
 * ikm    = HKDF(salt=auth, ecdh, "WebPush: info\0" || 브라우저 공개키 || 서버 임시 공개키, 32)
 * cek    = HKDF(salt=랜덤16, ikm, "Content-Encoding: aes128gcm\0", 16)
 * nonce  = HKDF(salt=랜덤16, ikm, "Content-Encoding: nonce\0", 12)
 * 본문   = salt(16) || 레코드크기(4) || 키길이(1)=65 || 서버 임시 공개키(65) || AES-GCM(cek, nonce, 내용 || 0x02)
 * </pre>
 */
public final class WebPushCrypto {

    private static final int RECORD_SIZE = 4096;
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushCrypto() {
    }

    /** @param p256dhB64 브라우저 공개키 (base64url), authB64 브라우저 인증 비밀 16바이트 (base64url) */
    public static byte[] encrypt(byte[] plaintext, String p256dhB64, String authB64) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, p256dhB64, authB64, Vapid.newKeyPair(), salt);
    }

    /** 테스트용: 서버 임시 키와 salt를 정해서 넣는다. */
    static byte[] encrypt(byte[] plaintext, String p256dhB64, String authB64, KeyPair serverKeys, byte[] salt)
            throws GeneralSecurityException {
        if (plaintext.length > RECORD_SIZE - 17 - 86) {
            throw new IllegalArgumentException("알림 내용이 너무 깁니다.");
        }
        byte[] uaPublic = Vapid.B64D.decode(p256dhB64);
        byte[] auth = Vapid.B64D.decode(authB64);
        ECPublicKey uaKey = Vapid.publicKeyFromRaw(uaPublic);
        byte[] asPublic = Vapid.rawPublicKey((ECPublicKey) serverKeys.getPublic());

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(serverKeys.getPrivate());
        ka.doPhase(uaKey, true);
        byte[] ecdh = ka.generateSecret();

        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
        byte[] ikm = hkdf(auth, ecdh, keyInfo, 32);
        byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] cipher = gcm.doFinal(concat(plaintext, new byte[] {0x02})); // 0x02 = 마지막 레코드 표시

        return ByteBuffer.allocate(16 + 4 + 1 + asPublic.length + cipher.length)
                .put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic).put(cipher).array();
    }

    /** HKDF-SHA256, 길이 32 이하 (출력 1블록이면 충분). */
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] okm = mac.doFinal(concat(info, new byte[] {0x01}));
        byte[] out = new byte[length];
        System.arraycopy(okm, 0, out, 0, length);
        return out;
    }

    static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) {
            n += p.length;
        }
        ByteBuffer b = ByteBuffer.allocate(n);
        for (byte[] p : parts) {
            b.put(p);
        }
        return b.array();
    }
}
