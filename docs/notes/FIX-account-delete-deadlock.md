# 버그 수정: 탈퇴 중 Deadlock (2026-11-12)

## 증상 (운영 로그, 2026-11-11 21:29 KST)
```
SQL Error: 1213, SQLState: 40001
Deadlock found when trying to get lock; try restarting transaction
... [delete wr1_0 from work_record wr1_0 where wr1_0.owner_user_id=?]
```
탈퇴 요청이 에러 페이지로 끝났다. 같은 계정의 탈퇴 요청이 거의 동시에 두 번 들어온 것(버튼 두 번 누름)으로 추정된다.

## 원인
```
요청 A: payslip 삭제 → work_day 삭제 → work_record 삭제(잠금 대기) ─┐
요청 B: payslip 삭제 → work_day 삭제(잠금 대기) ───────────────────┘  서로 상대 잠금을 기다림 → 교착
```
InnoDB는 지울 행과 그 주변 범위(gap)를 잠근다. 두 트랜잭션이 같은 행들을 다른 순간에 잠그면서 서로를 기다렸다. MySQL이 한쪽을 강제로 취소했고, 취소된 요청이 500 에러가 됐다.

## 고친 것
1. **계정 행 먼저 잠그기:** `em.find(AppUser.class, id, PESSIMISTIC_WRITE)` → `SELECT ... FOR UPDATE`. 두 번째 요청은 여기서 첫 번째가 끝날 때까지 기다린다. 끝난 뒤 계정이 없으면 `false`를 돌려주고 아무것도 하지 않는다. 모든 탈퇴가 같은 자원(계정 행)을 같은 순서로 먼저 잡으므로 교착이 생기지 않는다.
2. **버튼 두 번 누름 막기:** 탈퇴 폼을 제출하면 버튼을 비활성화하고 "지우는 중…"으로 바꾼다.
3. **탈퇴 기록:** `account deleted: userId=N`만 로그에 남긴다. 이메일·이름은 남기지 않는다. 다음에 id가 비면 탈퇴인지 로그로 확인할 수 있다.

## 테스트
- `AccountDeletionConcurrencyTest`: 두 스레드가 동시에 같은 계정을 탈퇴시키면 결과는 [true, false]이고 예외가 없으며, 계정이 지워진다.
- 한계: 테스트 DB는 H2라서 MySQL InnoDB의 gap lock 교착을 그대로 재현하지는 않는다. 테스트가 확인하는 건 "잠금으로 직렬화되고, 두 번째 요청은 조용히 끝난다"는 동작이다.
- 전체 101개 통과.

## 면접 포인트
- 비관적 잠금(PESSIMISTIC_WRITE)과 낙관적 잠금(@Version)의 차이, 그리고 여기서 비관적 잠금을 고른 이유: 충돌이 "같은 사람의 연타"처럼 짧고 확실해서, 기다렸다 처리하는 편이 재시도 로직보다 단순하다.
- 서버 방어(잠금)와 화면 방어(버튼 비활성화)를 둘 다 둔 이유: 화면 방어는 우회될 수 있다(새로고침, 느린 네트워크 재전송).
