#!/usr/bin/env bash
# 서버 밖 백업 설치: sudo bash install-offsite-backup.sh <버킷 이름>
#
# 매일 만드는 DB 백업(/var/backups/whv88, install-backup.sh)을 Google Cloud Storage 버킷에도 복사한다.
# VM 디스크가 망가져도 백업이 남는다. 버킷의 수명 주기 규칙(14일 뒤 삭제)으로 보관 기간을 지킨다.
#
# 준비물 (GCP 콘솔에서 사용자가 만든다. docs/notes/BACKUP.md 참고):
#   - 버킷 (us-west1, Standard, 공개 액세스 방지)
#   - 서비스 계정 키 JSON: 홈 폴더에 whv88-backup-key.json 으로 업로드. 버킷에 "스토리지 객체 생성자" 권한만 있다
#     (올리기만 가능, 읽기·지우기 불가 → 서버가 털려도 백업을 지울 수 없다).
set -euo pipefail
[ "$(id -u)" -eq 0 ] || { echo "sudo bash install-offsite-backup.sh <버킷 이름> 으로 실행하세요."; exit 1; }
BUCKET="${1:-}"
[[ "$BUCKET" =~ ^[a-z0-9][a-z0-9._-]{1,61}[a-z0-9]$ ]] || { echo "버킷 이름을 확인하세요. 예: sudo bash install-offsite-backup.sh whv88-backup-1234"; exit 1; }
[ -x /usr/local/sbin/whv88-backup ] || { echo "먼저 install-backup.sh 를 설치하세요."; exit 1; }

USER_HOME=$(getent passwd "${SUDO_USER:-root}" | cut -d: -f6)
KEY_SRC="$USER_HOME/whv88-backup-key.json"
KEY=/etc/whv88/backup-sa.json
if [ -f "$KEY_SRC" ]; then
    install -o root -g root -m 600 "$KEY_SRC" "$KEY"
    shred -u "$KEY_SRC" 2>/dev/null || rm -f "$KEY_SRC"   # 홈 폴더에 키를 남기지 않는다
fi
[ -f "$KEY" ] || { echo "키 파일이 없어요. whv88-backup-key.json 을 홈 폴더에 올린 뒤 다시 실행하세요."; exit 1; }

GCLOUD=$(command -v gcloud || ls /snap/bin/gcloud /usr/bin/gcloud 2>/dev/null | head -1 || true)
if [ -z "$GCLOUD" ]; then
    echo "gcloud 설치 중..."; snap install google-cloud-cli --classic; GCLOUD=/snap/bin/gcloud
fi

# 이 백업 전용 gcloud 설정 폴더 (사용자 계정 설정과 섞이지 않게)
CFG=/root/.config/gcloud-whv88-backup
mkdir -p "$CFG" && chmod 700 "$CFG"
CLOUDSDK_CONFIG="$CFG" "$GCLOUD" auth activate-service-account --key-file="$KEY" --quiet

cat > /usr/local/sbin/whv88-offsite <<SH
#!/usr/bin/env bash
# 오늘 백업 파일을 버킷에 올린다. 결과는 journal에 남는다 (journalctl -t whv88-backup).
# gcloud storage cp는 올리기 전에 "읽기"(objects.get) 권한으로 기존 파일을 확인해서, 올리기 전용 계정에서는 403이 난다.
# 그래서 Storage JSON API에 직접 올린다 (objects.create만 필요). 같은 이름 덮어쓰기는 권한이 없으므로 시각을 붙인다.
set -uo pipefail
F=/var/backups/whv88/whv_tracker-\$(date +%F).sql.gz
if [ ! -f "\$F" ]; then logger -t whv88-backup "offsite FAIL: no local backup \$F"; exit 1; fi
NAME=whv_tracker-\$(date +%F-%H%M).sql.gz
TOKEN=\$(CLOUDSDK_CONFIG=$CFG $GCLOUD auth print-access-token 2>/tmp/whv88-offsite.err) || { logger -t whv88-backup "offsite FAIL: token"; exit 1; }
CODE=\$(curl -s -o /tmp/whv88-offsite.err -w '%{http_code}' -X POST --data-binary @"\$F" \\
    -H "Authorization: Bearer \$TOKEN" -H "Content-Type: application/gzip" \\
    "https://storage.googleapis.com/upload/storage/v1/b/$BUCKET/o?uploadType=media&name=\$NAME")
if [ "\$CODE" = "200" ]; then
    logger -t whv88-backup "offsite OK: \$NAME -> gs://$BUCKET"
else
    logger -t whv88-backup "offsite FAIL: HTTP \$CODE \$(tr '\\n' ' ' < /tmp/whv88-offsite.err | cut -c1-300)"; exit 1
fi
SH
chmod 700 /usr/local/sbin/whv88-offsite

# 매일 03:17 로컬 백업 → 03:27 버킷 복사 (서버 시간대 기준)
echo "27 3 * * * root /usr/local/sbin/whv88-offsite" > /etc/cron.d/whv88-offsite

# 지금 한 번 실행해서 확인
/usr/local/sbin/whv88-backup
if /usr/local/sbin/whv88-offsite; then
    echo "서버 밖 백업 설치 완료. 오늘 백업을 gs://$BUCKET 에 올렸어요."
else
    echo "올리기 실패. 이유:"; cat /tmp/whv88-offsite.err; echo; exit 1
fi
journalctl -t whv88-backup -n 3 --no-pager
