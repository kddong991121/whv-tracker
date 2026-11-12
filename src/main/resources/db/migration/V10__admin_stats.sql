-- 운영 통계 (/admin): 마지막 방문 시각과 탈퇴 수.
-- last_seen_at: 로그인한 사용자가 하루에 한 번만 갱신 (다시 온 사람 수 계산용).
ALTER TABLE users ADD COLUMN last_seen_at TIMESTAMP NULL;
-- 탈퇴 기록: 언제 탈퇴했는지만 남긴다 (누구인지는 남기지 않는다).
CREATE TABLE deleted_account_log (
    id         BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    deleted_at TIMESTAMP NOT NULL
);
