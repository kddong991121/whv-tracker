# 방문 통계: GoatCounter (2026-11-11)

- 목적: 방문 수, 많이 본 화면, 유입 경로(카페·오픈채팅 등)를 본다. 홍보글 효과 확인용.
- 선택 이유: 무료, 쿠키 없음, 개인 식별 정보 없음 → 동의 팝업 필요 없음. 비용 0원 규칙 충족.
- 구현:
  - `AnalyticsAdvice`(@ControllerAdvice)가 모든 화면 모델에 `goatcounterUrl`을 넣는다. 공통 head 조각(`fragments/ui :: head`)이 값이 있을 때만 `<script data-goatcounter=... async src="https://gc.zgo.at/count.js">`를 넣는다.
  - 주소는 서버 환경변수 `WHV_GOATCOUNTER_URL`로 받는다. `https://<이름>.goatcounter.com/count` 형식만 받아서, 설정 실수로 다른 사이트의 스크립트가 들어가지 않게 했다. 로컬·테스트·데모는 비어 있어서 집계하지 않는다.
  - 개인정보처리방침의 "맡기는 곳"과 "쿠키"에 GoatCounter를 적었다.
- 테스트: `AnalyticsAdviceTest` (정상 주소 통과, 빈 값·다른 도메인·http 거부). 전체 100개 통과.
- 확인 방법: https://kddong99.goatcounter.com 에 로그인한다. 광고 차단 확장 프로그램을 쓰면 본인 방문은 안 잡힐 수 있다.
