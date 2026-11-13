package com.whv.tracker.push;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * VAPID (RFC 8292): 우리 서버가 보낸 알림임을 푸시 서비스에 증명하는 서명 키.
 * JDK 기본 암호 기능만 쓴다 (P-256 타원곡선, ES256 서명). 외부 라이브러리 없음.
 *
 * 키는 환경변수로만 받는다 (WHV_VAPID_PUBLIC = 공개키 65바이트, WHV_VAPID_PRIVATE = 비밀키 32바이트, 둘 다 base64url).
 */
public final class Vapid {

    static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final ECPublicKey publicKey;
    private final ECPrivateKey privateKey;
    private final String publicKeyB64;

    public Vapid(String publicB64, String privateB64) throws GeneralSecurityException {
        this.publicKey = publicKeyFromRaw(B64D.decode(publicB64.trim()));
        ECParameterSpec p256 = p256();
        this.privateKey = (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, B64D.decode(privateB64.trim())), p256));
        this.publicKeyB64 = publicB64.trim();
    }

    /** 브라우저 pushManager.subscribe의 applicationServerKey로 쓰는 공개키 (base64url). */
    public String publicKey() {
        return publicKeyB64;
    }

    /**
     * Authorization 헤더 값: "vapid t=JWT, k=공개키".
     *
     * @param audience 푸시 서비스 주소의 origin (예: https://fcm.googleapis.com)
     * @param subject  연락처 (mailto: 또는 https:)
     */
    public String authorization(String audience, String subject, long nowEpochSeconds) throws GeneralSecurityException {
        String header = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = B64.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + (nowEpochSeconds + 12 * 3600)
                + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;
        // ES256 서명은 r||s 64바이트(P1363 형식). JDK가 바로 그 형식으로 만들어 준다.
        Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
        sig.initSign(privateKey);
        sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return "vapid t=" + signingInput + "." + B64.encodeToString(sig.sign()) + ", k=" + publicKeyB64;
    }

    // ---- P-256 도우미 ----

    static ECParameterSpec p256() throws GeneralSecurityException {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        return params.getParameterSpec(ECParameterSpec.class);
    }

    /** 0x04 || X(32) || Y(32) → 공개키 */
    static ECPublicKey publicKeyFromRaw(byte[] raw) throws GeneralSecurityException {
        if (raw.length != 65 || raw[0] != 0x04) {
            throw new GeneralSecurityException("P-256 공개키 형식이 아닙니다.");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, p256()));
    }

    /** 공개키 → 0x04 || X(32) || Y(32) */
    static byte[] rawPublicKey(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 0x04;
        copyFixed(key.getW().getAffineX(), out, 1);
        copyFixed(key.getW().getAffineY(), out, 33);
        return out;
    }

    static void copyFixed(BigInteger v, byte[] dest, int offset) {
        byte[] b = v.toByteArray(); // 앞에 부호 0바이트가 붙거나 32바이트보다 짧을 수 있다
        int len = Math.min(b.length, 32);
        System.arraycopy(b, b.length - len, dest, offset + 32 - len, len);
    }

    static KeyPair newKeyPair() throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    /**
     * 서버에서 키를 한 번 만들 때 쓰는 명령. 화면에 찍지 말고 환경 파일에 바로 붙인다:
     * java -Dloader.main=com.whv.tracker.push.Vapid -cp app.jar org.springframework.boot.loader.launch.PropertiesLauncher
     */
    public static void main(String[] args) throws Exception {
        KeyPair kp = newKeyPair();
        byte[] d = new byte[32];
        copyFixed(((ECPrivateKey) kp.getPrivate()).getS(), d, 0);
        System.out.println("WHV_VAPID_PUBLIC=" + B64.encodeToString(rawPublicKey((ECPublicKey) kp.getPublic())));
        System.out.println("WHV_VAPID_PRIVATE=" + B64.encodeToString(d));
    }
}
