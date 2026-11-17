# 앱 안 의견 받기 (PRD 4.12)

## 바뀐 것
- 새 테이블 `feedback` (Flyway V13): 회원, 점수(1=👍 / -1=👎 / 없음), 글(최대 500자), 시각.
- `feedback/` 패키지: `Feedback`(엔티티), `FeedbackRepository`, `FeedbackService`(저장·하루 5건 한도·홈 카드 조건), `FeedbackController`(`/feedback`, `/feedback/rate`).
- 홈: 근무를 5일 이상 넣고 아직 👍/👎를 안 눌렀으면 "쓸 만한가요?" 카드. 👎를 누르면 이유 칸이 열린다. [나중에]는 7일 숨김(브라우저 저장).
- /admin: 👍·👎 개수와 최근 의견 20개(이메일 포함).
- 탈퇴: `AccountDeletionService`가 의견도 같이 지운다.
- 구글 폼 제거: `FeedbackLinkAdvice`·`WHV_FEEDBACK_URL`을 지우고 링크를 `/feedback`으로 바꿨다.
- 개인정보처리방침에 "의견" 항목 추가.

## 핵심 코드 (면접 대비)
```
홈 열기 → FeedbackService.shouldAskRating(owner)
            ├─ 점수(👍/👎)를 이미 보냈나?  → 예: 카드 없음
            └─ 근무 기록(WorkDay) 5개 이상? → 예: 카드 표시
카드의 👍/👎 → POST /feedback/rate → submit() → 저장 → 홈으로
submit(): 글 500자 초과 거절 → 점수·글 둘 다 없으면 거절 → 오늘 5건이면 거절 → 저장
```
- 하루 한도는 서버 날짜(시드니) 0시부터 센다.
- 글은 화면에 `th:text`로만 찍어서 HTML이 실행되지 않는다. 쓰기 요청은 CSRF 검사를 받는다.

## 검수
- 테스트 125개 통과. 새 테스트 `FeedbackTest`: 저장과 한도, 카드 조건, /admin 값과 탈퇴 삭제, 비로그인 차단과 구글 폼 링크 없음.
- 홈 카드 화면과 👎 입력칸 열기는 브라우저 스크립트라 배포 뒤 폰에서 직접 확인한다.
- 배포 순서 주의: 새 테이블이 필요해서 앱이 켜질 때 Flyway V13이 자동으로 실행된다. 롤백이 필요하면 이전 jar로 돌리기 전에 `DROP TABLE feedback;`은 필요 없다(이전 버전은 이 테이블을 쓰지 않는다).
