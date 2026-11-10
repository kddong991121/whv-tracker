-- F3: 페이슬립에서 읽은 값만 저장한다. 원본 PDF는 서버로 받지 않는다 (PRD 4.6).
-- checklist: 증빙 체크리스트 7개 항목 비트값 (1번 = 1, 2번 = 2, 3번 = 4 ...). 완성도 점수는 켜진 비트 수.
CREATE TABLE payslip (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id    BIGINT        NOT NULL,
    record_id        BIGINT        NOT NULL,
    employer_name    VARCHAR(100)  NOT NULL,
    abn              VARCHAR(11)   NULL,
    period_start     DATE          NOT NULL,
    period_end       DATE          NOT NULL,
    total_hours      DECIMAL(6,2)  NOT NULL,
    employment_type  VARCHAR(30)   NULL,
    worked_days      INT           NOT NULL,
    checklist        INT           NOT NULL DEFAULT 0,
    cash_paid        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP     NOT NULL,
    CONSTRAINT fk_payslip_owner FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT fk_payslip_record FOREIGN KEY (record_id) REFERENCES work_record (id)
);
