# 묶음 B: 페이슬립 여러 장 한 번에 (2026-11-13, PRD 4.10f)

## 바뀐 것
- PDF를 한 번에 최대 30장 고를 수 있다 (`<input type="file" multiple>`).
- 고른 파일을 한 장씩 차례로 이 기기에서 읽고(pdf.js), 목록에 "대기 / 저장됨 ✓ / 건너뜀 / 직접 입력 필요"를 보여준다.
- 한 장 확인 → 일한 날 고르기(평일은 미리 선택) → 저장 → 다음 장으로 자동 이동. "2 / 4장째" 진행 막대.
- "이 장 건너뛰기": 이미 넣은 기간이거나 읽지 못한 파일은 저장하지 않고 넘어간다.
- 한 장만 고르거나 직접 입력할 때는 예전처럼 폼을 그대로 보낸다.

## 흐름
```
[브라우저]                                   [서버]
PDF 여러 장 고르기 (최대 30)
  └ 한 장씩 pdf.js로 글자 읽기 → parse()      (파일은 서버로 안 감)
queue = [{name, 읽은 값, 상태}, ...]
show(k): 폼 채우기 → 사용자가 확인·날짜 선택
저장 ── fetch POST /payslip/one (폼 값 + CSRF) ──▶ PayslipBatchController
                                                  └ PayslipService.importPayslip (기존 규칙 그대로)
     ◀── {"ok":true,"message":"근무 5일을 넣었어요."} / {"ok":false,"error":"..."}
ok → 상태 "저장됨", show(k+1) / 실패 → 이유를 보여주고 그 장에 머문다
```

## 왜 JSON 엔드포인트를 따로 만들었나
- 기존 `POST /payslip`은 저장 후 페이지로 이동(redirect)한다. 여러 장을 이어 하려면 화면이 새로 고쳐지면 안 된다 (고른 파일 목록이 사라짐).
- 그래서 같은 서비스를 부르는 `POST /payslip/one`을 `@RestController`로 따로 두었다.
- 입력값이 빠졌을 때도 JSON으로 답하도록 컨트롤러 전용 `@ExceptionHandler`를 붙였다. (기존 컨트롤러의 예외 처리는 페이지로 이동시키므로 섞지 않았다.)
- 저장 규칙(ABN 검사, 고용 기간 밖 날짜, 같은 날짜 중복, 동의 체크)은 기존 `PayslipService`를 그대로 써서 한 곳에서만 관리한다.

## 테스트
- `HomeWorkPagesTest.payslipBatch_savesOneAtATime_andAnswersJson`: 정상 저장 JSON, 같은 날짜 재저장 거부 JSON(400), 빠진 값 JSON(400), 화면에 `multiple`과 "최대 30장".
- 브라우저(Playwright)로 가짜 페이슬립 3장 + 글자 없는 PDF 1장: 3장 저장, 1장 건너뜀, 저장 목록에 3장 표시. ABN이 틀리거나 기간 밖 날짜면 그 장에 머물며 이유를 보여주는 것도 확인.

## 셀프 리뷰
- 보안: 새 주소는 로그인 필요(`anyRequest().authenticated()`), CSRF 토큰은 폼 값에 들어 있어 그대로 검사된다. 파일 파라미터 없음.
- 버그: 버튼 두 번 누름 → 저장 중 버튼 비활성화. 로그인 만료로 HTML이 오면 JSON이 아니라서 "다시 로그인" 안내.
- 메모리: PDF를 다 읽은 뒤 `doc.destroy()`로 바로 정리하고, 파일 내용은 들고 있지 않는다(읽은 값만 보관).
- 고친 것: 끝난 뒤에도 "건너뛰기" 버튼이 보이던 문제(`.btn`의 display가 `hidden`을 이김), 빈 폼에 하루 평균 문구가 남던 문제.
