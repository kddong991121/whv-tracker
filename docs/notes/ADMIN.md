# 운영 통계 화면 /admin (2026-11-12)

## 화면
전체 가입자 · 오늘 가입 · 최근 7일 방문 · 근무를 넣은 사람(가입만 한 사람) · 세컨/서드 준비 · 최근 30일 탈퇴 · 최근 14일 가입 막대.

## 구조
```
요청 ─▶ LastSeenInterceptor ── 세션에 오늘 날짜 없으면 ──▶ UPDATE users SET last_seen_at (하루 1번)
/admin ─▶ AdminController.isAdmin(이메일 == WHV_ADMIN_EMAIL, 대소문자 무시, email_verified)
              ├ 아니면 404
              └ 맞으면 AdminStatsService.stats() ─ JPQL count 쿼리 7개 ─▶ admin.html
탈퇴 ─▶ AccountDeletionService ─▶ deleted_account_log(deleted_at)만 저장
```

## 핵심 코드 설명 (면접 대비)
1. **권한 없으면 403이 아니라 404:** 관리자 화면이 있다는 사실 자체를 숨긴다. 주소를 추측해서 들어오는 시도에 정보를 주지 않는다.
2. **관리자 판단을 코드가 아닌 설정으로:** 이메일을 코드에 쓰지 않고 환경변수로 받는다. 비어 있으면 기본으로 닫힌다(fail-closed).
3. **쓰기 줄이기:** 방문 시각을 요청마다 갱신하면 화면 하나 열 때마다 DB 쓰기가 생긴다. 세션에 "오늘 이미 기록함"을 표시해서 사용자당 하루 1번만 쓴다.
4. **집계는 DB에서:** 사용자 목록을 가져와 자바에서 세지 않고 `count` 쿼리로 숫자만 받는다. 개인정보가 애플리케이션 메모리에 올라오지 않는다.

## 테스트
- `AdminControllerTest`: 로그인 안 함 → 로그인으로 이동, 일반 사용자 → 404, 관리자(대소문자 다름) → 200, 다른 사용자의 이메일·이름 없음. 전체 105개 통과.

## 설정 (서버)
`echo 'WHV_ADMIN_EMAIL=<사이트에 로그인하는 구글 이메일>' | sudo tee -a /etc/whv88/whv88.env` 후 재시작.
