-- 주 1회 알림 (PRD 4.10e): 사용자가 직접 켠 브라우저의 구독 정보. 탈퇴하면 함께 지운다.
-- endpoint: 푸시 서비스 주소 (구글·애플·모질라·마이크로소프트), p256dh·auth: 알림 내용 암호화용 브라우저 공개값.
CREATE TABLE push_subscription (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    endpoint   VARCHAR(600) NOT NULL,
    p256dh     VARCHAR(120) NOT NULL,
    auth       VARCHAR(40)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    CONSTRAINT uk_push_endpoint UNIQUE (endpoint),
    CONSTRAINT fk_push_user FOREIGN KEY (user_id) REFERENCES users (id)
);
