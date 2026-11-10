# PWA (홈 화면에 추가)

## 만든 것 (2026-11-10)
- `static/manifest.webmanifest`: 이름 "워홀 88", 파란 아이콘, 전체 화면(standalone), 시작 주소 `/`.
- `static/icons/`: 192·512 아이콘, 안드로이드용 maskable, 아이폰용 apple-touch-icon (파란 바탕 흰 "88").
- `static/sw.js` 서비스 워커: 인터넷이 끊기면 `offline.html` 안내를 보여준다.
  - **개인 근무 화면은 캐시하지 않는다.** 휴대폰을 다른 사람이 써도 기록이 기기에 남지 않게 하려는 것이다.
- 모든 화면 head에 `fragments/pwa :: tags` (manifest 링크, 아이폰 메타 태그, 서비스 워커 등록).
- `SecurityConfig`: PWA 정적 파일은 로그인 없이 열린다. `PwaConfig`: `.webmanifest` 파일 형식 지정.

## 사용 방법 (사용자 안내용)
- 안드로이드 Chrome: 메뉴 ⋮ → "홈 화면에 추가" (또는 "앱 설치").
- 아이폰 Safari: 공유 버튼 → "홈 화면에 추가".
- **HTTPS 주소에서만** 설치된다 (localhost 제외). 배포 후 확인한다.

## 확인한 것
- Chrome 설치 가능 검사에서 오류 없음 (시크릿 창 제외), 서비스 워커 활성, 오프라인 시 안내 화면.
- 테스트: PWA 파일 공개 접근, 홈에 manifest 링크.

## 한계
- 앱스토어 검색 노출 없음. 아이폰 푸시 알림 제한 (기획에 푸시 없음).
