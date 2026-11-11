# 배포 (2026-11-11)

## 구성
- 주소: https://whv88.duckdns.org (DuckDNS 무료 주소, 서버가 5분마다 IP 갱신)
- 서버: Google Cloud Compute Engine e2-micro, us-west1-b, Ubuntu 26.04, 표준 디스크 30GB, 스왑 2GB
- 구조: Caddy(HTTPS 인증서 자동, 443) → Spring Boot 앱(127.0.0.1:8080, prod 프로필) → MySQL 8.4(127.0.0.1)
- 비밀값: 서버 /etc/whv88/whv88.env (권한 600). 코드와 저장소에는 없다.
- 백업: 매일 03:17(호주 시드니 시간) mysqldump → /var/backups/whv88, 14일 보관. 같은 서버 안이라 디스크 고장에는 대비 못 함.
- 비용: 무료 등급 + 체험 크레딧(2027-02-09까지). 예산 알림 1,500원(알림만, 자동 중지 없음).

## 설치·업데이트
- 처음: jar + deploy/setup.sh 업로드 → `sudo bash setup.sh`
- 새 버전: jar + deploy/update.sh 업로드 → `sudo bash update.sh`
- 백업 설치: `sudo bash deploy/install-backup.sh`

## 겪은 문제
- 오라클 클라우드 무료 가입이 "이미 계정이 있다"로 막혀서 Google Cloud로 바꿨다.
- Ubuntu 26.04의 sudo(sudo-rs)가 구글 클라우드 기본 관리자 설정을 인정하지 않아 sudo가 막혔다.
  VM 시작 스크립트로 /etc/sudoers.d/90-kddong99 를 만들어 해결했다.
- 비밀값(DuckDNS token, OAuth secret)이 캡처에 노출돼 바로 재발급했다. 앞으로 캡처 시 가린다.
