-- F1 모드 B 근무일 행 (날짜당 1행, 행별 W 스냅샷, 선택 근무시간)
CREATE TABLE work_day (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id                BIGINT        NOT NULL,
    work_date                DATE          NOT NULL,
    work_hours               DECIMAL(4,2)  NULL,
    standard_days_snapshot   INT           NOT NULL,
    CONSTRAINT fk_work_day_record FOREIGN KEY (record_id) REFERENCES work_record (id),
    CONSTRAINT uk_work_day_record_date UNIQUE (record_id, work_date)
);

-- 기준 주당 근무일 W 변경 이력 (사유 필수, 기본값 5에서 바뀔 때만 생성)
CREATE TABLE work_standard_change (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id      BIGINT        NOT NULL,
    changed_at     TIMESTAMP     NOT NULL,
    previous_days  INT           NOT NULL,
    new_days       INT           NOT NULL,
    reason         VARCHAR(255)  NOT NULL,
    source_note    VARCHAR(255)  NULL,
    CONSTRAINT fk_standard_change_record FOREIGN KEY (record_id) REFERENCES work_record (id)
);
