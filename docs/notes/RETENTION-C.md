# 묶음 C: 주 1회 알림 (웹 푸시) (2026-11-13, PRD 4.10e)

## 바뀐 것
- 계정 화면 "주 1회 근무 알림" 카드: 알림 받기 / 끄기.
- 홈: 근무를 한 번이라도 넣은 사람에게 한 번 권하는 카드 (닫기 가능, 이미 켰으면 안 보임).
- 매주 일요일 19:00 (시드니 시간) "이번 주 근무 확인" 알림. 예: "이번 주 2.8일 인정됐어요 · 세컨 88일 중 41일. 빠진 날 없나요?"
  - 이번 주 기록이 없으면 "이번 주 근무를 아직 안 적었어요 · …". 목표를 다 채운 사람에게는 보내지 않는다.
- 켜는 순간 확인 알림을 한 번 보낸다 → 바로 동작을 확인할 수 있고, 실패하면 구독을 저장하지 않는다.
- /admin에 "주 1회 알림 켠 사람" 수. 개인정보 처리방침에 알림 정보와 전달 업체 추가.
- 서버에 키가 없으면 기능 전체가 꺼지고 화면에도 안 보인다.

## 전체 흐름
```
[브라우저]                          [우리 서버]                         [푸시 서비스: 구글/애플/모질라/MS]
알림 받기 클릭
 └ 허용 창 → 허용
 └ pushManager.subscribe(공개키) ──────────────────────────────▶ 구독 주소(endpoint) 발급
 └ POST /push/subscribe ──────▶ 주소 검사(알려진 푸시 서비스만)
   (endpoint, p256dh, auth)     DB 저장 (V12 push_subscription)
                                확인 알림 ── 암호화 + VAPID 서명 ──▶ 전달 ──▶ 휴대폰 알림
매주 일요일 19:00 ─────────────▶ @Scheduled weekly()
                                사용자별 이번 주 인정 일수 계산
                                각 기기로 전송 ───────────────────▶ 전달
                                404/410 답이면 그 구독 삭제 (알림 끄거나 앱 지움)
sw.js 'push' 이벤트 ◀──────────────────────────────────────────── 암호문
 └ 브라우저가 풀어서 알림 표시, 누르면 /work 열기
```

## 왜 라이브러리 없이 직접 구현했나
- 자바 웹 푸시 라이브러리는 BouncyCastle·비동기 HTTP 클라이언트를 함께 끌고 와 jar와 메모리가 커진다. 서버는 1GB 무료 VM이다.
- 필요한 암호 기능(P-256 ECDH, ES256 서명, HKDF, AES-GCM)은 JDK 17에 다 있다.
- `Vapid.java`(서명 키·JWT, 80줄) + `WebPushCrypto.java`(본문 암호화, 60줄) + `PushSender.java`(java.net.http 전송).

## 암호화 핵심 (RFC 8291 aes128gcm)
```
ecdh  = ECDH(서버 임시 비밀키, 브라우저 공개키 p256dh)
ikm   = HKDF(salt=auth, ecdh, "WebPush: info\0" || 브라우저 공개키 || 서버 임시 공개키) 32바이트
cek   = HKDF(salt=랜덤16, ikm, "Content-Encoding: aes128gcm\0") 16바이트
nonce = HKDF(salt=랜덤16, ikm, "Content-Encoding: nonce\0") 12바이트
본문  = salt | 4096 | 65 | 서버 임시 공개키 | AES-GCM(cek, nonce, 내용 || 0x02)
```
- 서명(VAPID, RFC 8292): JWT {aud: 푸시 서비스 origin, exp: 12시간, sub: mailto:연락처}를 ES256으로 서명.
  JDK의 `SHA256withECDSAinP1363Format`이 JWT가 요구하는 r||s 64바이트 형식을 바로 만들어 준다.

## 검증
- 개발 중 파이썬 `http_ece`(웹 푸시 표준 구현) 라이브러리로 우리 암호문을 따로 풀어 원문과 같음을 확인했고,
  파이썬 `cryptography`로 VAPID 서명도 검증했다 (서로 다른 구현끼리 맞물리는지 확인).
- `WebPushCryptoTest`: 브라우저 쪽 복호화를 규격대로 따로 짜서 왕복 확인, 머리 형식, 서명 검증, 너무 긴 내용 거부, 주소 검사.
- `PushSenderTest`: 로컬 가짜 푸시 서버로 보내 헤더(TTL, Content-Encoding, Authorization)와 본문 길이 확인.
- `PushServiceTest`: 확인 알림, 기기 5개 상한, 확인 알림 실패 시 삭제, 주간 문구(0일/1.4일), 410 답이면 삭제, 탈퇴 시 삭제.
- 화면: 계정·홈 카드 밝은/다크 모드 스크린샷.
- 한계: 이 개발 환경의 헤드리스 크롬은 구글 푸시 등록 서버에 닿지 못해 "브라우저 등록 → 실제 휴대폰 수신"은
  배포 후 실제 휴대폰으로 확인한다. (이때 등록이 끝없이 기다리는 경우를 발견해 20초 시간 제한을 넣었다.)

## 보안
- SSRF 방지: 서버가 구독 주소로 직접 요청을 보내므로, https + 알려진 푸시 서비스 호스트만 받는다
  (fcm.googleapis.com, updates.push.services.mozilla.com, web.push.apple.com, *.notify.windows.com).
  포트 지정·사용자 정보·비슷한 가짜 도메인(fcm.googleapis.com.evil.example)은 거부.
- 키: VAPID 비밀키는 환경변수(WHV_VAPID_PRIVATE)로만. 공개키는 비밀이 아니라 화면에 넣는다.
- 알림 내용은 암호화되어 푸시 서비스가 볼 수 없다. 내용에는 이름·이메일을 넣지 않는다.
- 로그인·CSRF 검사 그대로. 한 사용자 기기 5개 상한. 탈퇴 시 구독 삭제.

## 셀프 리뷰에서 고친 것
- 생성자가 둘이라 스프링이 못 고르던 문제 → 주입용 생성자에 @Autowired.
- 등록이 끝없이 기다리면 버튼이 멈춤 → 20초 제한.
- `hidden` 속성이 `.btn`의 display에 져서 닫기 버튼이 보이던 문제 → 공통 `[hidden]{display:none!important}`.
