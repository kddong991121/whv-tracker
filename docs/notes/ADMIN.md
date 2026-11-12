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

## 재방문 지표 추가 (2026-11-12, PRD 4.9b)

### 무엇을 더했나
- /admin "다시 쓰는지" 카드: **다시 온 사람**(가입한 날보다 뒤에 또 방문), **최근 7일 기록한 사람**.
- Flyway V11: `work_day.created_at` (근무를 적은 시각). 기존 행은 NULL이라 배포 뒤부터 쌓인다.

### 핵심 코드
```
WorkDay 저장 ──@PrePersist──▶ created_at = now()
AdminStatsService.stats(now)
  returned  = users 중 cast(last_seen_at as date) > cast(created_at as date)
  recorded7 = work_day.created_at >= now-7일 인 근무의 주인(distinct owner)
  비율      = Stats.returnedPercent(), recordedLast7Percent()  (분모 0이면 0)
```
- `@PrePersist`: JPA가 INSERT 직전에 부르는 콜백. 서비스 코드(addWorkDay, 되돌리기, 지난주 복사, 원탭 기록) 어디서 저장하든 한 곳에서 시각이 찍힌다.
- `cast(... as LocalDate)`: Hibernate 6 HQL 문법. MySQL·H2 모두 `cast(x as date)`로 바뀐다. 시각을 날짜로 잘라서 "가입한 날과 다른 날"을 비교한다.
- 개인정보는 세지 않고 숫자만 꺼낸다(기존 원칙 유지).

### 한계
- `last_seen_at`은 마지막 방문일 하나뿐이라 "매주 오는지"(코호트)는 못 본다.
- 날짜 기준은 서버 시간대(시드니).

### 테스트
- `AdminStatsServiceTest`: 사흘 전 가입·오늘 방문 1명만 "다시 온 사람", 방금 적은 근무는 최근 7일에 잡히고 8일 전으로 바꾸면 빠진다.
- `AdminControllerTest`: 화면에 "다시 온 사람" 표시.

### 셀프 리뷰
- 버그: 분모 0 나누기 → `percent()`에서 0 처리.
- 보안: /admin 접근 규칙은 그대로(관리자 이메일 아니면 404).
- 누락: 화면 스크린샷은 관리자 로그인이 필요해 MockMvc 렌더링 확인으로 대신함.
