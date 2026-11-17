-- 앱 안 의견 받기 (PRD 4.12): 한 줄 의견과 👍(1)/👎(-1). 탈퇴하면 함께 지운다.
CREATE TABLE feedback (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT        NOT NULL,
    rating     INT           NULL,
    message    VARCHAR(500)  NULL,
    created_at TIMESTAMP     NOT NULL,
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_feedback_user_created ON feedback (user_id, created_at);
