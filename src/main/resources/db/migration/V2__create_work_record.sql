-- F1 고용 기록 (모드 A 기간 1건 / 모드 B 고용 기간 1건)
CREATE TABLE work_record (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id       BIGINT       NOT NULL,
    record_mode         VARCHAR(20)  NOT NULL,
    visa_year           INT          NOT NULL,
    employer_name       VARCHAR(100) NOT NULL,
    employer_key        VARCHAR(100) NOT NULL,
    postcode            VARCHAR(4)   NOT NULL,
    industry            VARCHAR(40)  NOT NULL,
    start_date          DATE         NOT NULL,
    end_date            DATE         NOT NULL,
    unpaid_weather_days INT          NOT NULL DEFAULT 0,
    standard_days       INT          NULL,
    disaster_work_type  VARCHAR(20)  NULL,
    CONSTRAINT fk_work_record_owner FOREIGN KEY (owner_user_id) REFERENCES users (id)
);
