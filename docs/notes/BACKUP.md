# 서버 밖 백업 (2026-11-12)

## 왜
기존 백업(install-backup.sh)은 VM 디스크 안(/var/backups/whv88)에만 있었다. VM이나 디스크가 망가지면 DB와 백업이 함께 사라진다. 실제 가입자가 생겨서 서버 밖 사본이 필요해졌다.

## 구성
```
03:17  mysqldump → /var/backups/whv88/whv_tracker-YYYY-MM-DD.sql.gz (14일 보관)
03:27  gcloud storage cp → gs://<버킷>/whv_tracker-YYYY-MM-DD.sql.gz
       버킷 수명 주기: 14일 지나면 자동 삭제 (개인정보처리방침 보관 기간과 같음)
결과 로그: journalctl -t whv88-backup
```

## 결정과 이유
- **Google Cloud Storage, us-west1, Standard:** 같은 리전이라 전송비가 없다. 무료 한도(미국 리전 Standard 5GB)에 들어간다. DB 백업은 수 KB~MB라 14일치도 한도의 아주 작은 일부다.
- **서비스 계정 키 + "스토리지 객체 생성자" 권한만:** VM 기본 계정은 저장소 쓰기 권한이 없고, 권한을 바꾸려면 VM을 껐다 켜야 한다(외부 IP가 바뀔 수 있음). 그래서 버킷 하나에 "올리기만" 되는 전용 계정을 만들었다. 서버가 털려도 이 키로는 백업을 읽거나 지울 수 없다.
- **키 보관:** /etc/whv88/backup-sa.json (root만 읽기, 600). 설치 스크립트가 홈 폴더의 원본 키를 지운다. 코드·문서·채팅에 키를 넣지 않는다.
- **공개 액세스 방지:** 버킷은 비공개. 저장 시 Google이 기본 암호화한다.

## 복구 방법 (DB가 망가졌을 때)
1. GCP 콘솔 → Cloud Storage → 버킷 → 원하는 날짜 파일 다운로드 (사용자 본인 계정)
2. SSH 업로드 후: `gunzip -c whv_tracker-YYYY-MM-DD.sql.gz | sudo mysql whv_tracker`
3. `sudo systemctl restart whv88`

## 확인
- `sudo journalctl -t whv88-backup -n 5` → "offsite OK" 줄이 매일 하나씩 있어야 한다.
