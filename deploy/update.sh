#!/usr/bin/env bash
# 새 버전 올리기: 새 jar 를 홈에 업로드한 뒤  sudo bash update.sh
# 업로드할 때 이름이 "..._(1).jar" 처럼 바뀌어도, 홈에서 가장 최근에 올린 whv-tracker*.jar 를 설치한다.
set -euo pipefail
DIR="$(dirname "$0")"
JAR_SRC="$(ls -t "$DIR"/whv-tracker*.jar 2>/dev/null | head -1)"
[ -n "$JAR_SRC" ] || { echo "whv-tracker*.jar 파일이 없어요."; exit 1; }
echo "설치할 파일: $JAR_SRC ($(stat -c %s "$JAR_SRC") bytes)"
install -o whv88 -g whv88 -m 640 "$JAR_SRC" /opt/whv88/app.jar
rm -f "$DIR"/whv-tracker*.jar
systemctl restart whv88
echo "다시 켜는 중... 1분쯤 뒤 사이트를 새로고침하세요."
