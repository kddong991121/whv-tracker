#!/usr/bin/env bash
# 워홀 88 서버 설치 스크립트 (Ubuntu, 구글 클라우드 e2-micro 1GB 기준)
# 사용법: 이 파일과 whv-tracker-0.1.0-SNAPSHOT.jar 를 같은 폴더(홈)에 올린 뒤
#         sudo bash setup.sh
# 비밀값(구글 키, DuckDNS token)은 실행 중에 직접 입력한다. 화면에 표시되지 않고 파일에만 저장된다.
set -euo pipefail

DOMAIN="whv88.duckdns.org"
DUCK_SUB="whv88"
JAR_SRC="$(dirname "$0")/whv-tracker-0.1.0-SNAPSHOT.jar"

[ "$(id -u)" -eq 0 ] || { echo "sudo bash setup.sh 로 실행하세요."; exit 1; }
[ -f "$JAR_SRC" ] || { echo "jar 파일이 없어요: $JAR_SRC"; exit 1; }

echo "== 1/7 스왑(디스크를 메모리처럼) 2GB 만들기"
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "== 2/7 Java, MySQL, Caddy(HTTPS) 설치"
apt-get update -q
DEBIAN_FRONTEND=noninteractive apt-get install -y -q openjdk-21-jre-headless mysql-server caddy curl openssl

echo "== 3/7 MySQL 메모리 줄이기 + DB 만들기"
cat > /etc/mysql/mysql.conf.d/zz-whv88.cnf <<'CNF'
[mysqld]
bind-address = 127.0.0.1
performance_schema = OFF
innodb_buffer_pool_size = 64M
max_connections = 20
CNF
systemctl restart mysql
mkdir -p /etc/whv88 && chmod 700 /etc/whv88
if [ ! -f /etc/whv88/db.pw ]; then
  openssl rand -base64 24 | tr -d '/+=\n' > /etc/whv88/db.pw && chmod 600 /etc/whv88/db.pw
fi
DBPW="$(cat /etc/whv88/db.pw)"
mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS whv_tracker CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'whv'@'localhost' IDENTIFIED BY '${DBPW}';
ALTER USER 'whv'@'localhost' IDENTIFIED BY '${DBPW}';
GRANT ALL PRIVILEGES ON whv_tracker.* TO 'whv'@'localhost';
FLUSH PRIVILEGES;
SQL

echo "== 4/7 비밀값 입력 (입력해도 화면에 안 보여요. 붙여넣고 Enter)"
if [ ! -f /etc/whv88/whv88.env ]; then
  read -r -p "구글 클라이언트 ID: " GID
  read -r -s -p "구글 클라이언트 보안 비밀번호: " GSECRET; echo
  cat > /etc/whv88/whv88.env <<ENV
SPRING_PROFILES_ACTIVE=prod
WHV_DB_PASSWORD=${DBPW}
GOOGLE_CLIENT_ID=${GID}
GOOGLE_CLIENT_SECRET=${GSECRET}
ENV
  chmod 600 /etc/whv88/whv88.env
else
  echo "   /etc/whv88/whv88.env 이미 있음 (건너뜀)"
fi
if [ ! -f /etc/cron.d/duckdns ]; then
  read -r -s -p "DuckDNS token: " DTOKEN; echo
  echo "*/5 * * * * root curl -fsS \"https://www.duckdns.org/update?domains=${DUCK_SUB}&token=${DTOKEN}&ip=\" >/dev/null 2>&1" > /etc/cron.d/duckdns
  chmod 600 /etc/cron.d/duckdns
fi

echo "== 5/7 앱 설치 (/opt/whv88)"
id whv88 >/dev/null 2>&1 || useradd --system --home /opt/whv88 --shell /usr/sbin/nologin whv88
mkdir -p /opt/whv88
install -o whv88 -g whv88 -m 640 "$JAR_SRC" /opt/whv88/app.jar
cat > /etc/systemd/system/whv88.service <<'UNIT'
[Unit]
Description=whv88 (워홀 88)
After=network.target mysql.service
Wants=mysql.service

[Service]
User=whv88
EnvironmentFile=/etc/whv88/whv88.env
ExecStart=/usr/bin/java -Xmx256m -Xss512k -XX:+UseSerialGC -Duser.timezone=Australia/Sydney -jar /opt/whv88/app.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable --now whv88

echo "== 6/7 HTTPS (Caddy가 인증서를 자동으로 받아요)"
cat > /etc/caddy/Caddyfile <<CADDY
${DOMAIN} {
    encode gzip
    reverse_proxy 127.0.0.1:8080
}
CADDY
systemctl restart caddy

echo "== 7/7 앱 켜지는 중 (최대 2분)"
for i in $(seq 1 24); do
  if curl -fsS -o /dev/null http://127.0.0.1:8080/; then
    echo "완료! https://${DOMAIN} 에 접속해 보세요."; exit 0
  fi
  sleep 5
done
echo "앱이 아직 안 켜졌어요. 아래 명령 결과를 보여주세요:"
echo "  sudo journalctl -u whv88 -n 50 --no-pager"
