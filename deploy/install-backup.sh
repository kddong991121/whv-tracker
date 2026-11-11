#!/usr/bin/env bash
# 매일 새벽 DB 백업 설치: sudo bash install-backup.sh
# /var/backups/whv88 에 하루 1개씩 압축 파일을 만들고 14일 지난 파일은 지운다 (개인정보처리방침 2번과 같은 기간).
set -euo pipefail
[ "$(id -u)" -eq 0 ] || { echo "sudo bash install-backup.sh 로 실행하세요."; exit 1; }
mkdir -p /var/backups/whv88 && chmod 700 /var/backups/whv88
cat > /usr/local/sbin/whv88-backup <<'SH'
#!/usr/bin/env bash
set -euo pipefail
OUT=/var/backups/whv88/whv_tracker-$(date +%F).sql.gz
mysqldump -uroot --single-transaction --routines whv_tracker | gzip > "$OUT"
chmod 600 "$OUT"
find /var/backups/whv88 -name 'whv_tracker-*.sql.gz' -mtime +14 -delete
SH
chmod 700 /usr/local/sbin/whv88-backup
echo "17 3 * * * root /usr/local/sbin/whv88-backup" > /etc/cron.d/whv88-backup
/usr/local/sbin/whv88-backup
echo "백업 설치 완료. 오늘 백업:"; ls -la /var/backups/whv88
