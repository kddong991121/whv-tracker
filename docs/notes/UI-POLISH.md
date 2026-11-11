# UI 다듬기 1차 (2026-11-11)

Impeccable 평가 결과 중 사용자가 고른 P1 세 개를 고치고, "호주 느낌 조금 추가"를 반영했다.

## 바뀐 것

| 문제 (평가) | 고친 방법 | 파일 |
|---|---|---|
| 첫 화면: 모바일에서 Google 버튼이 첫 화면 밖(850px) | 버튼 두 개를 소개 문장 바로 아래로 올리고 로고를 72px에서 56px로 줄임. 이제 약 290px 위치 | index.html, fragments/auth.html |
| 인정 지역 화면만 따로 놂 (하단 탭 없음, 다른 머리말) | 공통 머리말·카드·입력 모양으로 바꿈. 로그인한 사람에게는 하단 탭, 로그인 전에는 "처음으로" 링크 | check.html, CheckController |
| 전체 기록 화면이 예전 모양 + 내부 용어(모드 A/B, 환산, span, W) | 공통 모양과 하단 탭 적용, 화면 문장을 쉬운 말로 바꿈. 업종·재해 유형은 영문 상수 대신 한국어 이름 | ledger.html, Industry, DisasterWorkType |
| 근무 기록: 기록할 곳이 화면 아래, 빈 날 7줄 반복 | 순서를 "이번 주 합계 → 날짜 → 내 일터(접힘)"로 바꿈. 빈 날은 한 줄, 날짜 줄을 누르면 입력 칸이 열림 | work.html |
| 흔한 회색+파랑 | 배경을 모래색 #F6F2EA로, 보조색 유칼립투스 녹색 #2E6B52(메뉴 선택 표시, 화면 위 작은 제목), 첫 화면 위쪽에 해 질 녘 띠 | fragments/ui.html, fragments/auth.html |

일수와 버튼의 파란색 #2F6FEB는 그대로 둔다. 숫자 색이 바뀌면 사용자가 헷갈린다.

## 근무 기록 화면 순서

```
바꾸기 전                     바꾼 뒤
┌──────────────┐            ┌──────────────┐
│ 내 일터 (3곳)  │            │ 이번 주 합계    │  ← 바로 보임
│  ...설명...    │            ├──────────────┤
├──────────────┤            │ 7.6 (월) 인정 [+근무]│
│ 7.6 쉬는 날    │            │   일터 A  8h  ✕ │
│  [+ 근무]     │            │ 7.7 (화)  [+근무]│  ← 빈 날은 한 줄
│ 7.7 쉬는 날    │            │ ...           │
│  [+ 근무]     │            ├──────────────┤
│ ... 7번 반복   │            │ 내 일터 3곳 ▸   │  ← 접혀 있음
├──────────────┤            └──────────────┘
│ 이번 주 합계    │  ← 맨 아래
└──────────────┘
```

일터가 하나도 없으면 "내 일터"가 처음부터 열려 있고, 날짜 목록 위에 "먼저 아래 '내 일터'에서 일한 곳을 추가하세요" 안내가 뜬다.

## 핵심 코드 설명 (면접 대비)

### 1. HTML `<details>`로 펼치기 (자바스크립트 없음)

```html
<details>
  <summary>7.8 (수) <span class="add-pill">+ 근무</span></summary>
  <form ...>일터 / 시간 / 추가</form>
</details>
```

- `<summary>`를 누르면 브라우저가 알아서 안쪽 폼을 열고 닫는다.
- 자바스크립트가 필요 없어서 서버 렌더링(Thymeleaf)과 잘 맞고, 키보드(Enter/Space)로도 열린다.
- 지우기(✕) 버튼은 `<details>` 밖에 두었다. 안에 두면 날짜 줄을 누를 때 함께 반응할 수 있다.

### 2. Thymeleaf 속성 순서 함정

```html
<!-- 틀림: th:replace가 th:if보다 먼저 실행돼서 조건이 무시된다 -->
<nav th:if="${loggedIn}" th:replace="~{fragments/ui :: nav('check')}"></nav>

<!-- 맞음: 바깥 th:block에서 먼저 조건을 본다 -->
<th:block th:if="${loggedIn}"><nav th:replace="~{fragments/ui :: nav('check')}"></nav></th:block>
```

Thymeleaf는 한 태그 안의 속성을 정해진 순서로 처리한다. 조각 넣기(`th:insert`/`th:replace`)가 1순위, 조건(`th:if`)은 그 뒤다.

### 3. 로그인 여부 판단을 한 곳에

`HomeController.isLoggedIn(Authentication)`을 패키지 안에서 함께 쓰도록 열었다. 로그인 전 사용자는 Spring Security에서 `AnonymousAuthenticationToken`으로 들어오므로 `isAuthenticated()`만 보면 true가 나온다. 그래서 익명 토큰을 따로 걸러낸다.

### 4. enum에 화면용 이름 붙이기

```java
public enum Industry {
    TOURISM_HOSPITALITY("관광·요식업"), ...;
    public String label() { return label; }
}
```

DB와 폼 값은 영문 상수(`name()`)를 그대로 쓰고, 화면에는 `label()`만 쓴다. 저장 형식은 바뀌지 않아서 기존 데이터와 마이그레이션에 영향이 없다.

## 테스트

- 전체 95개 통과.
- 추가: `AuthIntegrationTest.checkPage_isPublic_andShowsResult`. 로그인 전 인정 지역 화면에 하단 탭이 없고 "처음으로"가 있는지 확인한다.
- 수정: `LedgerControllerTest`. 화면 문장 변경에 맞춰 기대 문장을 "매일 출근한 정규 근무"로 바꿨다.
- 수정: `WorkCalculatorTest.disasterRecovery_excludedFromTotal`. 이번 변경과 무관하게 이미 실패하던 테스트다. F2에서 경고 문장이 바뀌었는데 테스트는 옛 문장 "재해 복구"를 찾고 있었다. 지금 문장 "합계에서 뺐습니다"로 맞췄다.

## 스스로 검토

- 보안: 새 주소나 새 입력은 없다. 인정 지역 화면은 여전히 로그인 없이 열리고, 입력값을 저장하지 않는다.
- 접근성: 유칼립투스 녹색 #2E6B52와 모래색 배경의 대비는 약 5.6:1로, 기준 4.5:1을 넘는다.
- 남은 것 (이번 범위 밖): 전체 기록 화면은 길이가 5,800px로 여전히 길다. 정규 근무 기간 입력을 새 화면으로 옮기는 일은 따로 한다. 페이슬립 파일 버튼(영어 "Choose File")과 아이콘·Google 로고(P2)도 남아 있다.
