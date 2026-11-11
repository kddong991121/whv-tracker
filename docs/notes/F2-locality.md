# F2 보강: 포스트코드 지역명 표시 (2026-11-11)

## 만든 것
- 포스트코드 4자리를 넣으면 바로 아래에 `📍 Toowoomba (QLD) · North Toowoomba, Toowoomba City, South Toowoomba 외 30곳`이 뜬다.
- 위치: 인정 지역 확인(입력칸과 결과), 근무 기록의 일터 추가, 전체 기록의 일터 추가 폼.
- 자료: ABS SAL 2021 – POA 2021 (data.gov.au, CC BY 2.5 AU). 포스트코드 2,639개.

```
docs/sources/mmm23_sal21_poa21.xlsx ──(build_localities.py)──▶ seed/postcode_localities.csv
                                                                     │ 기동 시 1번 읽기
                                                                     ▼
입력칸 ──fetch──▶ GET /api/postcode/4350 ──▶ PostcodeLocalities.find() ──▶ {"main":"Toowoomba",...}
/check 결과 ─────────────────────────────▶ 같은 find()로 모델에 locality 추가
```

## 핵심 코드 설명 (면접 대비)
1. **대표 지역명 뽑기.** ABS 자료에는 인구가 없어서 "어느 동네가 중심인지"를 바로 알 수 없다. 같은 포스트코드 안 동네 이름에 반복되는 단어(East/North 같은 방향어는 뺌)를 대표로 삼았다. 4350이면 Toowoomba, 4670이면 Bundaberg가 나온다. 반복이 없으면 면적이 가장 작은 동네를 쓴다. 보통 시내 중심이 면적이 작다.
2. **판정과 분리.** 지역명은 참고 표시일 뿐이다. 인정 판정은 내무부 목록(PostcodeRegistry)만 쓴다. 통계용 근사 경계로 법적 판단을 하지 않으려는 것이다.
3. **오프라인 변환.** 엑셀을 서버에서 읽지 않고, 스크립트로 작은 CSV(214KB)를 만들어 jar에 넣었다. 기동 시 HashMap으로 한 번 올려서 조회는 O(1)이다. 외부 API를 부르지 않아 비용이 0원이고 장애 지점도 없다.
4. **XSS 방지.** 힌트 글자는 `textContent`로만 넣는다. 응답이 HTML로 해석되지 않는다.
5. **응답 순서 꼬임 방지.** 빠르게 타이핑하면 이전 요청 응답이 늦게 올 수 있다. 응답을 받았을 때 입력값이 바뀌었으면 그 응답은 버린다.

## 테스트 (필수)
- `PostcodeLocalitiesTest`: 4350 → Toowoomba/QLD/3개 표시, 3500처럼 하나뿐이면 "외 N곳" 없음, 잘못된 형식·없는 번호는 비어 있음.
- `AuthIntegrationTest.postcodeApi_isPublic_andReturnsLocality`: 로그인 없이 200, 없는 번호 404, /check 결과에 "Toowoomba (QLD)".
- 전체 101개 통과.

## 스스로 검토
- 공개 API지만 읽기 전용이고 개인정보가 없다. 입력 형식은 서버에서 다시 검사한다(숫자 4자리).
- 라이선스: CC BY 2.5 AU는 출처 표시 조건이다. 화면과 이 문서, PRD에 출처를 적었다.

## 추가: ABS 자료에 동네 이름이 없는 번호 (2026-11-11)
- 예: 4433은 내무부 목록(QLD, 표 4)에는 있지만 ABS 자료에는 동네 이름이 없다. 처음에는 "숫자를 다시 확인해 주세요"가 떠서 맞는 번호를 틀린 것처럼 보여줬다.
- 고침: API가 주만 돌려준다(`main`이 빈 값, 주는 `PostcodeRegistry.statesFor`). 화면에는 "📍 QLD · 동네 이름 자료가 없는 번호예요 (인정 여부는 따로 확인돼요)"가 뜬다.
- 주도 알 수 없는 입력(숫자 4자리가 아님 등)만 404를 돌려주고 "지역명을 찾지 못했어요"를 보여준다.
