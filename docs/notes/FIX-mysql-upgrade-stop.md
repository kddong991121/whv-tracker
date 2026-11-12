# 장애: MySQL 자동 업데이트 뒤 앱이 꺼진 채로 남음 (2026-11-12)

## 무슨 일이 있었나
- 15:56(KST) 우분투 자동 보안 업데이트(unattended-upgrades)가 MySQL을 업그레이드했다.
- 업그레이드하면서 MySQL을 껐다 켰다.
- 앱 서비스 설정에 `Requires=mysql.service`가 있어서, MySQL이 꺼질 때 앱도 같이 꺼졌다.
- MySQL은 다시 켜졌지만 앱은 다시 켜지지 않았다. 약 7분 동안 502. UptimeRobot 알림으로 발견, 수동 재시작으로 복구.

## 왜 다시 안 켜졌나
```
MySQL 정지 ──Requires──▶ 앱도 정지 (systemd가 시킨 정상 정지)
MySQL 시작 ──────────────▶ 앱은 그대로 꺼짐
```
- `Requires=`는 "상대가 멈추면 나도 멈춘다"는 뜻이다. 상대가 다시 켜질 때 나를 켜 주지는 않는다.
- `Restart=always`는 앱이 스스로 죽었을 때만 다시 켠다. systemd가 시킨 정지에는 동작하지 않는다.

## 고친 것
- `Requires=mysql.service` → `Wants=mysql.service`. 켤 때 순서(After)는 그대로 두고, MySQL이 멈춰도 앱은 계속 돈다.
- MySQL이 잠깐 멈춘 동안 DB 연결은 실패하지만, 커넥션 풀(Hikari)이 MySQL이 돌아오면 다시 연결한다.
- `deploy/setup.sh`도 같이 고쳤다.

## 확인 방법
`sudo systemctl restart mysql` 뒤 몇 초 기다렸다 `curl http://127.0.0.1:8080/`이 200이면 된다.
