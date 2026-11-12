# 편의 기능 묶음 (2026-11-12)

## 만든 것
| 기능 | 위치 | 동작 |
|---|---|---|
| 원탭 기록 | 홈 | `오늘 Sunny Farm 8시간 일했어요 ＋` → `POST /work/today` → 저장 후 "기록 완료 ✓" |
| 지난주 그대로 | 근무 기록 | `↻ 지난주와 똑같이 넣기` → `POST /work/copy-week` → "N일 넣었어요 · M일 건너뜀" |
| 로그인 유지 | 전체 | 세션을 MySQL에 저장, 30일 |
| 되돌리기 | 근무 기록 | 삭제 → 아래 검은 토스트 "근무를 지웠어요 · 되돌리기" (5초) |
| 시간 칩 | 근무 추가 폼 | 4·6·8시간 버튼, 평소보다 짧으면 주황 안내 |

```
[홈 원탭] ─▶ LedgerService.quickShift(owner, 비자, 오늘)
              └ 마지막 WorkDay의 일터 → 기간·평소시간 확인 → addWorkDay(오늘, 평소시간)
[지난주]  ─▶ copyPreviousWeek(owner, 이번주 월)
              └ 지난주 WorkDay마다 addWorkDay(날짜+7) 시도 → 규칙 위반은 건너뜀(카운트)
[삭제]    ─▶ deleteWorkDay → DeletedShift(일터, 날짜, 시간) → 플래시 → 토스트 폼 → 같은 값으로 POST /work/shift
[세션]    브라우저 쿠키(30일) ─▶ SessionRepositoryFilter ─▶ SPRING_SESSION 표 (배포해도 유지)
```

## 핵심 코드 설명 (면접 대비)
1. **규칙 재사용:** 원탭·복사·되돌리기 모두 새 저장 로직을 만들지 않고 기존 `addWorkDay`를 부른다. 중복, 고용 기간, 하루 24시간 검사가 한 곳에만 있어서 기능이 늘어도 규칙이 어긋나지 않는다.
2. **되돌리기를 소프트 삭제 없이:** `deleted_at` 칸을 추가하는 대신, 지운 값을 플래시에 담아 같은 값으로 다시 넣는다. DB 구조를 바꾸지 않았다. 대신 id는 새로 바뀐다(문제없음).
3. **세션을 DB에:** 메모리 세션은 배포(재시작) 때 모두 사라져서 사용자 전원이 다시 로그인해야 했다. Spring Session JDBC로 MySQL에 두면 재시작해도 유지된다. 서버를 여러 대로 늘려도 세션을 공유할 수 있다(수평 확장 준비).
4. **함께 생긴 문제와 해결:** 세션을 DB에 두니 "Cannot create a session after the response has been committed" 오류가 났다. CSRF 토큰을 화면 출력 도중에 늦게 만들면서 세션을 새로 만들려 했기 때문이다. `CsrfEagerAdvice`가 컨트롤러 단계에서 토큰을 미리 읽게 해서 해결했다.
5. **세션에 들어가는 객체는 Serializable:** 플래시 속성(`DeletedShift`)도 세션(DB)에 직렬화되므로 `Serializable`을 붙였다.

## 테스트 (필수)
- `HomeWorkPagesTest.quickShift_today_thenShowsDone`: 근무 이력 없으면 버튼 없음 → 어제 근무 후 버튼(7.6시간) → 누르면 저장·"기록 완료" → 다시 누르면 거부.
- `HomeWorkPagesTest.copyPreviousWeek_copiesAndSkipsExisting`: 지난주 2일 중 이미 있는 월요일은 건너뛰고 1일만 넣는다.
- `HomeWorkPagesTest.deleteShift_offersUndo_withSameValues`: 삭제 시 되돌리기 값이 같고, 그 값으로 다시 넣을 수 있다.
- 기존 테스트 전부가 세션 DB 저장 상태로 통과 (전체 104개).

## 스스로 검토
- 보안: 새 POST 둘 다 로그인과 CSRF가 필요하다. `/work/today`는 넘어온 recordId가 서버가 계산한 원탭 후보와 같을 때만 저장한다(다른 사람 일터 id를 보내도 거부됨, `addWorkDay`도 소유자 검사).
- 세션 30일: 공용 PC에서는 로그아웃을 해야 한다. 내 계정 화면에 로그아웃 버튼이 있다.
- 배포 영향: V9가 처음 켜질 때 자동 적용된다. 배포 직후 한 번은 모두 다시 로그인해야 한다(메모리 세션 → DB 세션 전환). 그 다음부터는 배포해도 유지된다.
