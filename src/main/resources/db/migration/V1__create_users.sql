-- 1단계 인증 기반: 회원 계정 (구글 로그인 전용, 비밀번호 컬럼 없음).
-- 근무 기록 관련 테이블(정규 근무, 일한 날, 근무 목록, W 이력 등)은 F1 단계에서 V2 이후로 다시 만든다.
-- 이전 V1·V2(F1 v3 구조)는 배포 전이라 적용된 적이 없으며 삭제했다.
CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    google_subject  VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    display_name   VARCHAR(100) NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uk_users_google_subject UNIQUE (google_subject)
);
