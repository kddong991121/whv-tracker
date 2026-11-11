#!/usr/bin/env bash
# 새 버전 올리기: 새 jar 를 홈에 올린 뒤  sudo bash update.sh
set -euo pipefail
JAR_SRC="$(dirname "$0")/whv-tracker-0.1.0-SNAPSHOT.jar"
install -o whv88 -g whv88 -m 640 "$JAR_SRC" /opt/whv88/app.jar
systemctl restart whv88
echo "다시 켜는 중... 1분쯤 뒤 사이트를 새로고침하세요."
