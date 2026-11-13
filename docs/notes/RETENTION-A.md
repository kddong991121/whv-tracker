# 묶음 A: 다시 오게 하기·퍼지게 하기 (2026-11-13, PRD 4.10a·b·c·d·g)

## 바뀐 것
| 기능 | 화면 | 핵심 파일 |
|---|---|---|
| 예상 완주일 (4.10a) | 홈 | `calc/Forecast.java`, `HomeController` |
| 홈 화면에 추가 안내 (4.10b) | 홈 | `fragments/ui.html` (install 조각) |
| 공유 미리보기 (4.10c) | 첫 화면·/check·로그인 | `fragments/ui.html` (og 태그), `SiteAdvice`, `static/icons/og.png` |
| 다크 모드 (4.10d) | 전체 | `fragments/ui.html` (@media prefers-color-scheme), `pwa.html` (theme-color) |
| 친구에게 알려주기 (4.10g) | 홈·계정 | `fragments/ui.html` (share 조각) |

## 예상 완주일 계산
```
최근 28일(오늘 포함) 장부 ──dashboard(owner, null, 오늘-27, 오늘)──▶ 인정 일수 r
남은 일수 n (목표 - 지금까지 인정)

하루 속도 = r ÷ 28
걸리는 날 = ⌈ n ÷ 하루 속도 ⌉        (올림: 하루라도 모자라지 않게)
예상 완주일 = 오늘 + 걸리는 날
만료일보다 늦으면 주황색 경고
```
- 기존 `dashboard(owner, recordIds, from, to)`(F4 PDF 기간 자르기용)를 그대로 재사용했다. 기간 밖 근무는 잘리고, 매일 출근 기간도 28일 안쪽만 센다.
- 계산은 `Forecast.of(...)` 순수 함수라 DB 없이 단위 테스트한다. r이 0이거나 이미 다 채웠으면 null → 화면에서 카드 숨김.

## 공유 미리보기와 개인 화면
- og 태그는 공통 head 조각 안에 있지만 `${og == true}`일 때만 나온다. 컨트롤러가 첫 화면, /check, 로그인에서만 `og=true`를 넣는다.
- 홈·근무·장부 같은 개인 화면에는 안 나온다 (카톡 미리보기가 개인 정보를 긁어 갈 일이 없게).
- 사이트 주소는 `whv.site-url` 설정 (기본 https://whv88.duckdns.org).

## 다크 모드
- 휴대폰 설정만 따른다(스위치 없음). 공통 style 끝에 `@media (prefers-color-scheme: dark)` 한 덩어리로 모았다.
- 화면별 `<style>`에 박힌 밝은 색(장부 `section`, 계정 라디오, 페이슬립 PDF 상자 등)은 같은 덩어리에서 덮었다.
- 첫 화면 `.sunset`은 화면 쪽 style이 뒤에 나와서 이겼다 → `body.sunset`으로 우선순위를 올렸다.

## 홈 화면 추가 · 공유 버튼 (브라우저 JS)
- 안드로이드: `beforeinstallprompt`를 잡아 두었다가 "추가" 버튼에서 `prompt()`.
- 아이폰: 그 이벤트가 없어서 "공유 → 홈 화면에 추가" 문구만 보여준다.
- 이미 앱으로 열었으면(`display-mode: standalone`) 숨김. "닫기"는 localStorage에만 기억(try/catch, 서버 저장 없음).
- 공유: `navigator.share` → 안 되면 클립보드 복사 → 그것도 안 되면 prompt. 링크는 `/?s=share`(GoatCounter 유입 구분용, 서버 저장 없음).

## 테스트
- `ForecastTest`: 속도 계산, 하루 올림, 만료일 경고, 숨김 조건.
- `HomeWorkPagesTest.home_showsForecastFromRecentPace_andShareAndInstall`: 40일 전 근무만 있으면 숨김, 최근 5일(=7일 인정) 넣으면 "주 1.8일"과 예상일 표시, 공유·설치 카드 있음, 개인 화면에 og 없음.
- `AuthIntegrationTest.publicHome...`: 첫 화면에 og:image 절대 주소.
- 다크·밝은 모드 스크린샷으로 홈, 근무, 장부, 페이슬립, 계정, 첫 화면, /check 확인.

## 셀프 리뷰
- 버그: 속도 0 나누기 → r ≤ 0이면 null로 막음. 미래 날짜 근무는 기간 끝을 오늘로 잘라 속도에 안 들어감.
- 보안: 새 서버 주소(엔드포인트) 없음. og에는 고정 문구만, 사용자 데이터 없음. localStorage에는 닫기 여부만.
- 성능: 홈에서 기간 자른 장부 계산을 한 번 더 한다(쿼리 2개). 사용자당 기록 수가 작아 문제없음.
- 남은 것: 화면 곳곳의 인라인 글자색 몇 개는 다크에서도 원래 색이지만 대비는 확인했다.
