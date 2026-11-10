# 무료 호스팅 후보 비교 (2026-11-10)

- 목적: 사용자 결정을 돕기 위한 비교. 이 문서는 어떤 후보도 결정하지 않는다.
- 기준 5개: (1) Spring Boot + MySQL 실행 가능 여부, (2) 월 0원 가능 여부, (3) 잠자기(sleep) 제한, (4) DB 용량, (5) HTTPS 제공.
- 표기: "공식 확인" = 공식 문서 URL을 직접 조회해 확인. "미확인 (제3자 자료)" = 공식 페이지를 열지 못했거나 조회하지 않아, 확인 근거가 없거나 제3자 정보에 의존한다는 뜻.
- 조회 방법: 2026-11-10에 WebFetch로 조회했다. 조회 결과가 막히거나 부족한 페이지는 아래 "조회 실패·부족 목록"에 적었다.

## 후보 A. Oracle Cloud Always Free (VM + MySQL HeatWave Free)

출처: `https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm` (공식 확인)

| 기준 | 결과 | 확인 |
|---|---|---|
| 1. Spring Boot + MySQL 실행 | VM 옵션 2종: AMD `VM.Standard.E2.1.Micro` 최대 2대 (메모리 1GB씩), 또는 Ampere A1 월 1,500 OCPU시간 + 9,000 GB시간 (2 OCPU, 12GB 상당). MySQL HeatWave: 단일 노드 DB 시스템 1개. Spring Boot는 VM에 직접 설치하고 HeatWave에 연결하는 구성이 가능해 보이나, 실제 실행과 연결은 검증하지 않았다. | 공식 확인 (자원 목록). 실행 검증은 미실시 |
| 2. 월 0원 | Always Free 한도 안의 자원은 무료로 명시. 결제 수단 등록 요구와 한도 초과 과금 조건은 이 페이지에 없음. | 자원 한도: 공식 확인. 가입 조건: 미확인 (제3자 자료). `https://www.oracle.com/cloud/free/`는 조회 실패 |
| 3. 잠자기 | 잠자기 기능은 명시되지 않는다. 대신 "유휴 Always Free 컴퓨트 인스턴스는 Oracle이 회수할 수 있다"고 명시. 7일 동안 CPU 95 백분위 20% 미만, 네트워크 20% 미만 (A1은 메모리 20% 미만 포함)이면 유휴로 본다. | 공식 확인 |
| 4. DB 용량 | HeatWave 저장 50GB (데이터·로그), 백업 추가 50GB. 블록 볼륨 전체 200GB (부트 볼륨 기본 50GB 포함). 아웃바운드 월 10TB. | 공식 확인 |
| 5. HTTPS | 이 페이지에는 TLS 인증서 제공 언급이 없다. VM에서 인증서를 직접 설정해야 한다. 도메인 연결 방법은 조사하지 않았다. | HTTPS 자동 제공: 미확인 (제3자 자료). 직접 설정 방법: 미조사 |

## 후보 B. Render 무료 웹 서비스 + 외부 MySQL

출처: `https://render.com/docs/free` (공식 확인). 가격 페이지 `https://render.com/pricing`은 무료 웹 서비스 세부 정보가 없어 참고하지 못했다.

| 기준 | 결과 | 확인 |
|---|---|---|
| 1. Spring Boot + MySQL 실행 | 웹 서비스 무료 인스턴스 존재. Spring Boot 실행 방식 (Docker 또는 네이티브 Java 빌드) 지원 여부는 이 페이지에 없음. 무료 DB는 Postgres만 언급되어 MySQL은 무료 제공이 확인되지 않았다. MySQL은 외부 서비스가 필요하며, 외부 무료 MySQL 제공처는 이번 비교에서 조사하지 않았다. | 웹 서비스 무료: 공식 확인. Java 실행 방식: 미확인 (제3자 자료). MySQL 외부 제공처: 미조사 |
| 2. 월 0원 | 워크스페이스당 월 750 무료 인스턴스 시간. 시간이 모두 쓰이면 다음 달까지 정지. 무료 Postgres는 생성 후 30일 뒤 만료 (14일 유예 후 삭제). | 공식 확인 |
| 3. 잠자기 | 무료 웹 서비스는 15분간 인바운드 트래픽이 없으면 spin down. 다시 켜질 때 약 1분 소요. | 공식 확인 |
| 4. DB 용량 | 무료 Postgres: 고정 1GB, 백업 미지원, 30일 후 만료. MySQL 용량: 외부 서비스에 따라 다르며 미조사. | Postgres 조건: 공식 확인. MySQL: 미조사 |
| 5. HTTPS | 무료 웹 서비스는 커스텀 도메인과 관리형 TLS 인증서를 지원. | 공식 확인 |

- 추가 확인 필요: 무료 인스턴스의 RAM과 CPU 사양은 이 페이지에 없다. 미확인 (제3자 자료).

## 후보 C. Google Cloud Compute Engine e2-micro + MySQL 직접 설치

출처: `https://docs.cloud.google.com/free/docs/free-cloud-features` (공식 확인, `cloud.google.com/free/docs/free-cloud-features`에서 리디렉션됨).

| 기준 | 결과 | 확인 |
|---|---|---|
| 1. Spring Boot + MySQL 실행 | 무료 대상은 e2-micro VM 1대 (월 기준, 시간 한도 방식). VM에 Spring Boot와 MySQL을 직접 설치하는 구성이 가능해 보이나, 실행은 검증하지 않았다. | 무료 대상: 공식 확인. 설치 실행: 미검증 |
| 2. 월 0원 | e2-micro 무료는 지정된 미국 리전 3곳 (us-west1 오리건, us-central1 아이오와, us-east1 사우스캐롤라이나)에서만. 월 시간 한도 안에서 무료. 초과분은 표준 요금. Cloud Billing 계정이 필요하다. 아웃바운드 1GB/월 (북미 → 전 리전, 중국·호주 제외). | 공식 확인 |
| 3. 잠자기 | 잠자기 기능이 없는 VM 구성이다 (VM을 켜 두는 동안 상시 가동). 이 문장은 조회한 페이지 원문에 없으므로 공식 문구는 확인하지 못했다. | 미확인 (제3자 자료) |
| 4. DB 용량 | 표준 영구 디스크 30 GB-month/월. OS, 앱, MySQL이 같은 디스크를 쓴다. 디스크 크기 해석 방식은 미확인. | 공식 확인 (무료 양). 실사용 가능 용량: 미확인 (제3자 자료) |
| 5. HTTPS | 무료 항목에 TLS 제공 언급 없음. 인증서 설정과 도메인은 직접 해야 한다. 방법은 조사하지 않았다. | HTTPS 자동 제공: 미확인 (제3자 자료). 방법: 미조사 |

- 주의: 아웃바운드 1GB/월은 페이슬립 PDF 다운로드 등 트래픽이 많아지면 빨리 찬다. 이것은 조사 사실이며 판단이 아니다.

## 조회 실패·부족 목록 (2026-11-10)

- `https://www.oracle.com/cloud/free/`: HTTP 403. 가입 조건과 결제 수단 요구 확인 불가.
- `https://render.com/pricing`: 무료 웹 서비스 세부 정보 없음. 참고 불가.
- `https://cloud.google.com/free/docs/free-cloud-features`: 301 리디렉션. 리디렉션 URL로 조회 성공.
- 세 후보 모두 MySQL 실행 검증은 하지 않았다. 실행 가능 여부는 공식 문서 기준의 자원 조건만 본 것이다.

## 사용자 결정 필요: 후보 선택

- 후보 선택: A (Oracle Cloud Always Free) / B (Render 무료 + 외부 MySQL) / C (Google Cloud e2-micro + MySQL 직접 설치) / 기타 (직접 지정)
